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

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.usecase.ReceiveFcmDoorEventUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FcmMessageHandlerTest {
    private val receiveFcmDoorEvent = RecordingReceiveFcmDoorEventUseCase()
    private val handler = FcmMessageHandler(receiveFcmDoorEvent)

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
            assertNull(receiveFcmDoorEvent.lastEvent)
        }

    @Test
    fun handleDoorMessage_invalidPayload_returnsFalse() =
        runTest {
            // Missing required "type" field.
            val result = handler.handleDoorMessage(mapOf("message" to "hello"))

            assertFalse(result)
            assertNull(receiveFcmDoorEvent.lastEvent)
        }

    @Test
    fun handleDoorMessage_validPayload_forwardsParsedEvent() =
        runTest {
            val result = handler.handleDoorMessage(makePayload())

            assertTrue(result)
            val event = receiveFcmDoorEvent.lastEvent
            assertEquals(DoorPosition.CLOSED, event?.doorPosition)
            assertEquals("The door is closed.", event?.message)
            assertEquals(1000L, event?.lastChangeTimeSeconds)
            assertEquals(1100L, event?.lastCheckInTimeSeconds)
        }

    @Test
    fun handleDoorMessage_validPayload_invokesUseCaseExactlyOnce() =
        runTest {
            handler.handleDoorMessage(makePayload())

            assertEquals(1, receiveFcmDoorEvent.invocationCount)
        }
}

private class RecordingReceiveFcmDoorEventUseCase : ReceiveFcmDoorEventUseCase {
    var invocationCount: Int = 0
        private set
    var lastEvent: DoorEvent? = null
        private set

    override fun invoke(event: DoorEvent) {
        invocationCount += 1
        lastEvent = event
    }
}
