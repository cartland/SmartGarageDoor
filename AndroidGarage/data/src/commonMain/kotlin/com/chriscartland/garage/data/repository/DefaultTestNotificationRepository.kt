/*
 * Copyright 2026 Chris Cartland. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.chriscartland.garage.data.repository

import co.touchlab.kermit.Logger
import com.chriscartland.garage.data.MessagingBridge
import com.chriscartland.garage.domain.model.TestNotificationSandboxState
import com.chriscartland.garage.domain.model.TestNotificationTopic
import com.chriscartland.garage.domain.repository.AppSettingsRepository
import com.chriscartland.garage.domain.repository.TestNotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Enforces the max-one-subscription invariant for the test-notification
 * sandbox (see [TestNotificationRepository]).
 *
 * Persists three values in [AppSettingsRepository] and reconciles to them:
 *  - `testNotificationCurrentTopic` — the topic the user has/copies.
 *  - `testNotificationWantSubscribed` — user intent.
 *  - `testNotificationSubscribedTopic` — what FCM is actually subscribed to
 *    (`""` = none). The authoritative record, since the FCM SDK can't be queried.
 *
 * Every mutating method ends in [reconcile] under [mutex], so concurrent calls
 * can't interleave into a double-subscribe, and the system converges to
 * `subscribedTopic ∈ { currentTopic, none }` regardless of call order.
 *
 * MUST be a `@Singleton` — one instance owns the mutex + the subscription record.
 *
 * @param newTopicId produces the random id for a new topic. Injectable so tests
 *   can use deterministic ids; production uses a 64-bit random hex.
 */
class DefaultTestNotificationRepository(
    private val messagingBridge: MessagingBridge,
    private val settings: AppSettingsRepository,
    private val newTopicId: () -> String = {
        kotlin.random.Random
            .nextLong()
            .toULong()
            .toString(16)
            .padStart(16, '0')
    },
) : TestNotificationRepository {
    private val mutex = Mutex()

    private val _state = MutableStateFlow(TestNotificationSandboxState())
    override val state: StateFlow<TestNotificationSandboxState> = _state

    override suspend fun getTopic(): TestNotificationTopic =
        mutex.withLock {
            val topic = currentTopicOrGenerate()
            publishState()
            TestNotificationTopic(topic)
        }

    override suspend fun changeTopic(): TestNotificationTopic =
        mutex.withLock {
            val fresh = TEST_TOPIC_PREFIX + newTopicId()
            settings.testNotificationCurrentTopic.set(fresh)
            reconcile() // if subscribed, the subscription follows to `fresh`
            publishState()
            TestNotificationTopic(fresh)
        }

    override suspend fun subscribe() =
        mutex.withLock {
            currentTopicOrGenerate() // a subscribe with no topic yet generates one
            settings.testNotificationWantSubscribed.set(true)
            reconcile()
            publishState()
        }

    override suspend fun unsubscribe() =
        mutex.withLock {
            settings.testNotificationWantSubscribed.set(false)
            reconcile()
            publishState()
        }

    // --- internals: all callers already hold `mutex` ---

    /** Current topic string, generating + persisting one if none exists yet. */
    private suspend fun currentTopicOrGenerate(): String {
        val stored = settings.testNotificationCurrentTopic.flow.first()
        if (stored.isNotEmpty()) return stored
        val fresh = TEST_TOPIC_PREFIX + newTopicId()
        settings.testNotificationCurrentTopic.set(fresh)
        return fresh
    }

    /**
     * The single enforcement point. Makes the real FCM subscription match
     * intent. Guarantees `subscribedTopic ∈ { currentTopic, none }` and never
     * touches a non-test topic.
     */
    private suspend fun reconcile() {
        val want = settings.testNotificationWantSubscribed.flow.first()
        val current = settings.testNotificationCurrentTopic.flow.first()
        val target: String? = if (want && current.isNotEmpty()) current else null
        val subscribed: String? =
            settings.testNotificationSubscribedTopic.flow
                .first()
                .ifEmpty { null }

        if (subscribed == target) return

        // Tear down the existing subscription FIRST → never two live at once.
        if (subscribed != null) {
            requireOwnTopic(subscribed)
            messagingBridge.unsubscribeFromTopic(subscribed)
            settings.testNotificationSubscribedTopic.set("")
        }
        // Establish the new subscription; record it ONLY on confirmed success
        // (mirrors the M1 fix — never claim a subscription we don't have).
        if (target != null) {
            requireOwnTopic(target)
            if (messagingBridge.subscribeToTopic(target)) {
                settings.testNotificationSubscribedTopic.set(target)
            } else {
                Logger.w {
                    "TestNotification: subscribe to $target failed; retry on next reconcile"
                }
            }
        }
    }

    private suspend fun publishState() {
        val current = settings.testNotificationCurrentTopic.flow.first()
        val subscribed = settings.testNotificationSubscribedTopic.flow.first()
        _state.value =
            TestNotificationSandboxState(
                topic = current.ifEmpty { null }?.let { TestNotificationTopic(it) },
                isSubscribed = subscribed.isNotEmpty(),
            )
    }

    /**
     * Defense in depth: refuse to pass any non-`testNotification-` topic to the
     * shared [MessagingBridge]. Even a corrupted persisted value can never
     * unsubscribe a production (`door_open-*` / `buttonHealth-*`) topic — it
     * throws here instead, failing safe.
     */
    private fun requireOwnTopic(topic: String) {
        require(topic.startsWith(TEST_TOPIC_PREFIX)) {
            "TestNotificationRepository refused to touch non-test topic: $topic"
        }
    }

    companion object {
        const val TEST_TOPIC_PREFIX = "testNotification-"
    }
}
