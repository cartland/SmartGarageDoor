package com.chriscartland.garage.data.repository

import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.DoorFcmState
import com.chriscartland.garage.domain.model.DoorFcmTopic
import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeAppSettingsRepository
import com.chriscartland.garage.testcommon.FakeMessagingBridge
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FirebaseDoorFcmRepositoryTest {
    private val bridge = FakeMessagingBridge()
    private val settings = FakeAppSettingsRepository()
    private val logger = FakeAppLoggerRepository()
    private val repo = FirebaseDoorFcmRepository(bridge, settings, logger)

    @Test
    fun fetchStatusReturnsNotRegisteredWhenNoTopicSaved() =
        runTest {
            // Default setting is "" (never registered) → getFcmTopic() returns
            // null → fetchStatus() reports NotRegistered. (M1: previously the
            // empty string was wrapped in DoorFcmTopic("") and wrongly reported
            // Registered — the test name now matches the behavior.)
            val state = repo.fetchStatus()
            assertIs<DoorFcmState.NotRegistered>(state)
        }

    @Test
    fun fetchStatusReturnsRegisteredWhenTopicSaved() =
        runTest {
            settings.fcmDoorTopic.set("door-topic-123")
            val state = repo.fetchStatus()
            assertIs<DoorFcmState.Registered>(state)
            assertEquals(DoorFcmTopic("door-topic-123"), state.topic)
        }

    @Test
    fun registerDoorSubscribesToTopicAndReturnsRegistered() =
        runTest {
            val topic = DoorFcmTopic("new-topic")
            val state = repo.registerDoor(topic)

            assertIs<DoorFcmState.Registered>(state)
            assertEquals(topic, state.topic)
            assertTrue(bridge.subscribedTopics.contains("new-topic"))
            assertEquals("new-topic", settings.fcmDoorTopic.flow.first())
        }

    @Test
    fun registerDoorLogsSubscription() =
        runTest {
            repo.registerDoor(DoorFcmTopic("topic"))
            assertTrue(logger.loggedKeys.contains(AppLoggerKeys.FCM_SUBSCRIBE_TOPIC))
        }

    @Test
    fun registerDoorUnsubscribesFromOldTopicWhenDifferent() =
        runTest {
            settings.fcmDoorTopic.set("old-topic")
            repo.registerDoor(DoorFcmTopic("new-topic"))

            assertTrue(bridge.unsubscribedTopics.contains("old-topic"))
            assertTrue(bridge.subscribedTopics.contains("new-topic"))
        }

    @Test
    fun registerDoorDoesNotUnsubscribeWhenSameTopic() =
        runTest {
            settings.fcmDoorTopic.set("same-topic")
            repo.registerDoor(DoorFcmTopic("same-topic"))

            assertTrue(bridge.unsubscribedTopics.isEmpty())
        }

    @Test
    fun registerDoorReturnsNotRegisteredOnSubscribeFailure() =
        runTest {
            bridge.setSubscribeResult(false)
            val state = repo.registerDoor(DoorFcmTopic("topic"))

            assertIs<DoorFcmState.NotRegistered>(state)
        }

    @Test
    fun registerDoorReturnsNotRegisteredWhenNoToken() =
        runTest {
            bridge.setToken(null)
            val state = repo.registerDoor(DoorFcmTopic("topic"))

            assertIs<DoorFcmState.NotRegistered>(state)
        }

    @Test
    fun deregisterDoorUnsubscribesAndReturnsNotRegistered() =
        runTest {
            settings.fcmDoorTopic.set("existing-topic")
            val state = repo.deregisterDoor()

            assertIs<DoorFcmState.NotRegistered>(state)
            assertTrue(bridge.unsubscribedTopics.contains("existing-topic"))
        }

    @Test
    fun deregisterDoorRestoresDefaultSetting() =
        runTest {
            settings.fcmDoorTopic.set("existing-topic")
            repo.deregisterDoor()

            // After restoreDefault, setting returns "" (the default)
            assertEquals("", settings.fcmDoorTopic.flow.first())
        }

    @Test
    fun deregisterDoorWhenNoTopicReturnsNotRegisteredWithoutUnsubscribe() =
        runTest {
            // Default is "" (never registered) → getFcmTopic() returns null →
            // deregisterDoor() early-returns NotRegistered without attempting an
            // unsubscribe. (M1: previously it tried to unsubscribe from the
            // empty-string topic.)
            val state = repo.deregisterDoor()
            assertIs<DoorFcmState.NotRegistered>(state)
            assertTrue(bridge.unsubscribedTopics.isEmpty())
        }
}
