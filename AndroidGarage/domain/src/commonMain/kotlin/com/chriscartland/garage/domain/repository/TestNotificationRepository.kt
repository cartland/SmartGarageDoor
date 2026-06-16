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

package com.chriscartland.garage.domain.repository

import com.chriscartland.garage.domain.model.TestNotificationSandboxState
import com.chriscartland.garage.domain.model.TestNotificationTopic
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the test-notification sandbox subscription with a hard invariant:
 *
 * **At most ONE active FCM subscription at a time, regardless of the order the
 * four UseCases (get / change / subscribe / unsubscribe) are called.**
 *
 * The invariant is enforced HERE, not in the UseCases — they are thin
 * delegators. Internally a single `reconcile()` runs under a `Mutex`, so
 * concurrent calls cannot interleave into a double-subscribe, and every
 * mutating call converges to `subscribedTopic ∈ { currentTopic, none }`.
 *
 * **Safety:** the repository only ever touches its own `testNotification-*`
 * topics. It never reads the production door topic, never enumerates
 * subscriptions, and guards every bridge call with a prefix check — so it can
 * never unsubscribe a production (`door_open-*` / `buttonHealth-*`) topic.
 */
interface TestNotificationRepository {
    /** Observable sandbox state (current topic + whether currently subscribed). */
    val state: StateFlow<TestNotificationSandboxState>

    /** Get-or-generate the current personal topic. Does not change subscription. */
    suspend fun getTopic(): TestNotificationTopic

    /**
     * Regenerate the personal topic. If currently subscribed, the subscription
     * follows to the new topic (the old one is torn down first), preserving the
     * max-one-subscription invariant.
     */
    suspend fun changeTopic(): TestNotificationTopic

    /** Subscribe to the current topic. Idempotent; tears down any prior subscription first. */
    suspend fun subscribe()

    /** Unsubscribe from the current subscription. Idempotent. */
    suspend fun unsubscribe()
}
