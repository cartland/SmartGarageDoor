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

import com.chriscartland.garage.domain.model.DoorPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Contract tests for FCM data payload parsing.
 *
 * The server sends FCM data messages with specific key/value pairs.
 * If the parsing changes, the app silently stops updating the door status
 * from push notifications. These tests lock down the exact payload contract.
 */
class FcmPayloadParsingTest {
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
    fun validPayloadParsesCorrectly() {
        val payload = makePayload()
        val event = payload.asDoorEvent()

        assertNotNull("Valid payload should parse to DoorEvent", event)
        assertEquals(DoorPosition.CLOSED, event!!.doorPosition)
        assertEquals("The door is closed.", event.message)
        assertEquals(1000L, event.lastChangeTimeSeconds)
        assertEquals(1100L, event.lastCheckInTimeSeconds)
    }

    @Test
    fun allDoorPositionsParse() {
        DoorPosition.entries.forEach { position ->
            val payload = makePayload(type = position.name)
            val event = payload.asDoorEvent()
            assertNotNull("Position ${position.name} should parse", event)
            assertEquals(position, event!!.doorPosition)
        }
    }

    @Test
    fun unknownTypeParsesAsUnknown() {
        val payload = makePayload(type = "NEVER_SEEN_BEFORE")
        val event = payload.asDoorEvent()
        assertNotNull("Unknown type should still parse", event)
        assertEquals(DoorPosition.UNKNOWN, event!!.doorPosition)
    }

    @Test
    fun missingTypeReturnsNull() {
        val payload = makePayload(type = null)
        assertNull("Missing 'type' key should return null", payload.asDoorEvent())
    }

    @Test
    fun missingTimestampReturnsNull() {
        val payload = makePayload(timestampSeconds = null)
        assertNull("Missing 'timestampSeconds' should return null", payload.asDoorEvent())
    }

    @Test
    fun missingCheckInTimestampReturnsNull() {
        val payload = makePayload(checkInTimestampSeconds = null)
        assertNull("Missing 'checkInTimestampSeconds' should return null", payload.asDoorEvent())
    }

    @Test
    fun missingMessageDefaultsToEmpty() {
        val payload = makePayload(message = null)
        val event = payload.asDoorEvent()
        assertNotNull(event)
        assertEquals("", event!!.message)
    }

    @Test
    fun nonNumericTimestampReturnsNull() {
        val payload = makePayload(timestampSeconds = "not-a-number")
        assertNull("Non-numeric timestamp should return null", payload.asDoorEvent())
    }

    @Test
    fun emptyPayloadReturnsNull() {
        val payload = emptyMap<String, String>()
        assertNull("Empty payload should return null", payload.asDoorEvent())
    }

    @Test
    fun realServerPayloadFormat() {
        // This matches the actual format sent by the Firebase server.
        // If this test fails, push notifications are broken.
        val payload = mapOf(
            "type" to "OPEN",
            "message" to "The door is open.",
            "timestampSeconds" to "1710000000",
            "checkInTimestampSeconds" to "1710000060",
        )
        val event = payload.asDoorEvent()
        assertNotNull("Real server payload must parse successfully", event)
        assertEquals(DoorPosition.OPEN, event!!.doorPosition)
        assertEquals("The door is open.", event.message)
        assertEquals(1710000000L, event.lastChangeTimeSeconds)
        assertEquals(1710000060L, event.lastCheckInTimeSeconds)
    }
}
