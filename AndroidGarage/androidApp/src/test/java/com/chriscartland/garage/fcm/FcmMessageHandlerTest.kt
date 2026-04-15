/*
 * Copyright 2024 Chris Cartland. All rights reserved.
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

package com.chriscartland.garage.fcm

import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FcmMessageHandlerTest {
    private val doorRepository = FakeDoorRepository()
    private val appLoggerRepository = FakeAppLoggerRepository()
    private val handler = FcmMessageHandler(doorRepository, appLoggerRepository)

    private fun makePayload(
        type: String? = "CLOSED",
        message: String? = "The door is closed.",
        timestampSeconds: String? = "1000",
        checkInTimestampSeconds: String? = "1100",
    ): Map<String, String> =
        buildMap {
            type?.let { put("type", it) }
            message?.let { put("message", it) }
            timestampSeconds?.let { put("timestampSeconds", it) }
            checkInTimestampSeconds?.let { put("checkInTimestampSeconds", it) }
        }

    @Test
    fun handleDoorMessage_emptyData_returnsFalse() =
        runTest {
            val result = handler.handleDoorMessage(emptyMap())

            assertFalse(result)
            // No event should have been inserted — currentDoorEvent should still be default.
            val event = doorRepository.currentDoorEvent.first()
            assertNull(event?.doorPosition)
        }

    @Test
    fun handleDoorMessage_invalidPayload_returnsFalse() =
        runTest {
            // Missing required "type" field.
            val result = handler.handleDoorMessage(mapOf("message" to "hello"))

            assertFalse(result)
            val event = doorRepository.currentDoorEvent.first()
            assertNull(event?.doorPosition)
        }

    @Test
    fun handleDoorMessage_validPayload_insertsAndReturnsTrue() =
        runTest {
            val result = handler.handleDoorMessage(makePayload())

            assertTrue(result)
            val event = doorRepository.currentDoorEvent.first()
            assertEquals(DoorPosition.CLOSED, event?.doorPosition)
            assertEquals("The door is closed.", event?.message)
            assertEquals(1000L, event?.lastChangeTimeSeconds)
            assertEquals(1100L, event?.lastCheckInTimeSeconds)
        }

    @Test
    fun handleDoorMessage_validPayload_logsEvent() =
        runTest {
            handler.handleDoorMessage(makePayload())

            assertTrue(
                "Expected FCM_DOOR_RECEIVED to be logged",
                appLoggerRepository.loggedKeys.contains(AppLoggerKeys.FCM_DOOR_RECEIVED),
            )
        }
}
