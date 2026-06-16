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

import com.chriscartland.garage.data.repository.DefaultTestNotificationRepository.Companion.TEST_TOPIC_PREFIX
import com.chriscartland.garage.testcommon.FakeAppSettingsRepository
import com.chriscartland.garage.testcommon.FakeMessagingBridge
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultTestNotificationRepositoryTest {
    private class Fixture {
        val bridge = FakeMessagingBridge()
        val settings = FakeAppSettingsRepository()
        private var counter = 0
        val repo =
            DefaultTestNotificationRepository(
                messagingBridge = bridge,
                settings = settings,
                newTopicId = { "id${counter++}" }, // deterministic topics: id0, id1, …
            )
    }

    /** Net currently-subscribed topics derived from the fake's call records. */
    private fun FakeMessagingBridge.activeTopics(): List<String> {
        val net = mutableMapOf<String, Int>()
        subscribedTopics.forEach { net[it] = (net[it] ?: 0) + 1 }
        unsubscribedTopics.forEach { net[it] = (net[it] ?: 0) - 1 }
        return net.filterValues { it > 0 }.keys.toList()
    }

    private fun FakeMessagingBridge.allTouchedTopics(): List<String> = subscribedTopics + unsubscribedTopics

    // --- getTopic ---

    @Test
    fun getTopicGeneratesTestTopicAndDoesNotSubscribe() =
        runTest {
            val f = Fixture()
            val topic = f.repo.getTopic()

            assertTrue(topic.string.startsWith(TEST_TOPIC_PREFIX))
            assertTrue(f.bridge.subscribedTopics.isEmpty())
            assertEquals(topic, f.repo.state.value.topic)
            assertEquals(false, f.repo.state.value.isSubscribed)
        }

    @Test
    fun getTopicIsStableAcrossCalls() =
        runTest {
            val f = Fixture()
            assertEquals(f.repo.getTopic(), f.repo.getTopic())
        }

    // --- subscribe / unsubscribe ---

    @Test
    fun subscribeYieldsExactlyOneActiveSubscriptionToCurrentTopic() =
        runTest {
            val f = Fixture()
            f.repo.subscribe()

            assertEquals(1, f.bridge.activeTopics().size)
            assertEquals(
                f.repo.state.value.topic
                    ?.string,
                f.bridge.activeTopics().single(),
            )
            assertTrue(f.repo.state.value.isSubscribed)
        }

    @Test
    fun subscribeIsIdempotent() =
        runTest {
            val f = Fixture()
            f.repo.subscribe()
            f.repo.subscribe()
            f.repo.subscribe()

            // Only one actual FCM subscribe call; one active subscription.
            assertEquals(1, f.bridge.subscribedTopics.size)
            assertEquals(1, f.bridge.activeTopics().size)
        }

    @Test
    fun unsubscribeAfterSubscribeLeavesZeroActive() =
        runTest {
            val f = Fixture()
            f.repo.subscribe()
            f.repo.unsubscribe()

            assertTrue(f.bridge.activeTopics().isEmpty())
            assertEquals(false, f.repo.state.value.isSubscribed)
        }

    @Test
    fun unsubscribeWithoutSubscribeIsNoOp() =
        runTest {
            val f = Fixture()
            f.repo.unsubscribe()
            assertTrue(f.bridge.allTouchedTopics().isEmpty())
        }

    // --- changeTopic ---

    @Test
    fun changeTopicWhileSubscribedMovesSubscriptionToNewTopic() =
        runTest {
            val f = Fixture()
            f.repo.subscribe()
            val old = f.repo.state.value.topic
                ?.string
            val new = f.repo.changeTopic().string

            assertTrue(f.bridge.unsubscribedTopics.contains(old))
            assertTrue(f.bridge.subscribedTopics.contains(new))
            assertEquals(listOf(new), f.bridge.activeTopics())
            assertEquals(
                new,
                f.repo.state.value.topic
                    ?.string,
            )
            assertTrue(f.repo.state.value.isSubscribed)
        }

    @Test
    fun changeTopicWhileNotSubscribedDoesNotSubscribe() =
        runTest {
            val f = Fixture()
            f.repo.getTopic()
            val new = f.repo.changeTopic().string

            assertTrue(f.bridge.allTouchedTopics().isEmpty())
            assertEquals(
                new,
                f.repo.state.value.topic
                    ?.string,
            )
            assertEquals(false, f.repo.state.value.isSubscribed)
        }

    // --- invariant: at most one active subscription, any call order ---

    @Test
    fun atMostOneActiveSubscriptionForEveryCallOrder() =
        runTest {
            val sequences =
                listOf(
                    listOf("sub", "sub", "change", "sub", "unsub", "change", "sub"),
                    listOf("change", "sub", "change", "change", "unsub", "sub"),
                    listOf("sub", "unsub", "sub", "unsub", "sub"),
                    listOf("get", "sub", "get", "change", "sub", "unsub"),
                    listOf("unsub", "unsub", "sub", "sub", "change", "unsub", "change"),
                )
            for (seq in sequences) {
                val f = Fixture()
                for (op in seq) {
                    when (op) {
                        "sub" -> f.repo.subscribe()
                        "unsub" -> f.repo.unsubscribe()
                        "change" -> f.repo.changeTopic()
                        "get" -> f.repo.getTopic()
                    }
                    // The invariant holds after EVERY operation, not just at the end.
                    assertTrue(
                        f.bridge.activeTopics().size <= 1,
                        "More than one active subscription after $op in $seq: ${f.bridge.activeTopics()}",
                    )
                }
                // Final state agrees with the fake's record.
                val active = f.bridge.activeTopics()
                assertEquals(active.isNotEmpty(), f.repo.state.value.isSubscribed, "seq=$seq")
                if (active.isNotEmpty()) {
                    assertEquals(
                        f.repo.state.value.topic
                            ?.string,
                        active.single(),
                        "seq=$seq",
                    )
                }
            }
        }

    @Test
    fun concurrentOperationsNeverExceedOneActiveSubscription() =
        runTest {
            val f = Fixture()
            // Fire many operations concurrently; the repo's Mutex serializes them.
            val ops = List(30) { i ->
                launch {
                    when (i % 3) {
                        0 -> f.repo.subscribe()
                        1 -> f.repo.unsubscribe()
                        else -> f.repo.changeTopic()
                    }
                }
            }
            ops.forEach { it.join() }

            assertTrue(
                f.bridge.activeTopics().size <= 1,
                "Concurrent ops left >1 active: ${f.bridge.activeTopics()}",
            )
            assertEquals(f.bridge.activeTopics().isNotEmpty(), f.repo.state.value.isSubscribed)
        }

    // --- safety: only ever touch own (testNotification-*) topics ---

    @Test
    fun onlyTestNotificationTopicsAreEverPassedToTheBridge() =
        runTest {
            val f = Fixture()
            listOf("sub", "change", "sub", "unsub", "change", "sub", "unsub").forEach {
                when (it) {
                    "sub" -> f.repo.subscribe()
                    "unsub" -> f.repo.unsubscribe()
                    "change" -> f.repo.changeTopic()
                }
            }
            assertTrue(f.bridge.allTouchedTopics().isNotEmpty())
            assertTrue(
                f.bridge.allTouchedTopics().all { it.startsWith(TEST_TOPIC_PREFIX) },
                "A non-test topic reached the bridge: ${f.bridge.allTouchedTopics()}",
            )
            // Production namespaces are never touched.
            assertTrue(f.bridge.allTouchedTopics().none { it.startsWith("door_open-") })
            assertTrue(f.bridge.allTouchedTopics().none { it.startsWith("buttonHealth-") })
        }

    @Test
    fun reconcileRefusesACorruptedNonTestSubscribedTopicAndNeverUnsubscribesProduction() =
        runTest {
            val f = Fixture()
            // Simulate a corrupted persisted record pointing at a PRODUCTION topic.
            f.settings.testNotificationSubscribedTopic.set("door_open-PRODUCTION_DEVICE")
            f.settings.testNotificationCurrentTopic.set("${TEST_TOPIC_PREFIX}safe")
            f.settings.testNotificationWantSubscribed.set(true)

            // Any reconcile must refuse — the prefix guard throws BEFORE the bridge call.
            assertFailsWith<IllegalArgumentException> { f.repo.subscribe() }

            // The production topic was never handed to the bridge.
            assertTrue(f.bridge.unsubscribedTopics.none { it == "door_open-PRODUCTION_DEVICE" })
            assertTrue(f.bridge.allTouchedTopics().none { it.startsWith("door_open-") })
        }

    @Test
    fun freshRepoStartsUnsubscribedWithNoTopic() =
        runTest {
            val f = Fixture()
            assertNull(f.repo.state.value.topic)
            assertEquals(false, f.repo.state.value.isSubscribed)
        }
}
