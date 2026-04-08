package com.chriscartland.garage.data

import com.chriscartland.garage.domain.model.DoorPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
        val event = FcmPayloadParser.parseDoorEvent(payload)

        assertNotNull(event, "Valid payload should parse to DoorEvent")
        assertEquals(DoorPosition.CLOSED, event.doorPosition)
        assertEquals("The door is closed.", event.message)
        assertEquals(1000L, event.lastChangeTimeSeconds)
        assertEquals(1100L, event.lastCheckInTimeSeconds)
    }

    @Test
    fun allDoorPositionsParse() {
        DoorPosition.entries.forEach { position ->
            val payload = makePayload(type = position.name)
            val event = FcmPayloadParser.parseDoorEvent(payload)
            assertNotNull(event, "Position ${position.name} should parse")
            assertEquals(position, event.doorPosition)
        }
    }

    @Test
    fun unknownTypeParsesAsUnknown() {
        val payload = makePayload(type = "NEVER_SEEN_BEFORE")
        val event = FcmPayloadParser.parseDoorEvent(payload)
        assertNotNull(event, "Unknown type should still parse")
        assertEquals(DoorPosition.UNKNOWN, event.doorPosition)
    }

    @Test
    fun missingTypeReturnsNull() {
        val payload = makePayload(type = null)
        assertNull(FcmPayloadParser.parseDoorEvent(payload), "Missing 'type' key should return null")
    }

    @Test
    fun missingTimestampReturnsNull() {
        val payload = makePayload(timestampSeconds = null)
        assertNull(FcmPayloadParser.parseDoorEvent(payload), "Missing 'timestampSeconds' should return null")
    }

    @Test
    fun missingCheckInTimestampReturnsNull() {
        val payload = makePayload(checkInTimestampSeconds = null)
        assertNull(FcmPayloadParser.parseDoorEvent(payload), "Missing 'checkInTimestampSeconds' should return null")
    }

    @Test
    fun missingMessageDefaultsToEmpty() {
        val payload = makePayload(message = null)
        val event = FcmPayloadParser.parseDoorEvent(payload)
        assertNotNull(event)
        assertEquals("", event.message)
    }

    @Test
    fun nonNumericTimestampReturnsNull() {
        val payload = makePayload(timestampSeconds = "not-a-number")
        assertNull(FcmPayloadParser.parseDoorEvent(payload), "Non-numeric timestamp should return null")
    }

    @Test
    fun emptyPayloadReturnsNull() {
        val payload = emptyMap<String, String>()
        assertNull(FcmPayloadParser.parseDoorEvent(payload), "Empty payload should return null")
    }

    @Test
    fun realServerPayloadFormat() {
        val payload = mapOf(
            "type" to "OPEN",
            "message" to "The door is open.",
            "timestampSeconds" to "1710000000",
            "checkInTimestampSeconds" to "1710000060",
        )
        val event = FcmPayloadParser.parseDoorEvent(payload)
        assertNotNull(event, "Real server payload must parse successfully")
        assertEquals(DoorPosition.OPEN, event.doorPosition)
        assertEquals("The door is open.", event.message)
        assertEquals(1710000000L, event.lastChangeTimeSeconds)
        assertEquals(1710000060L, event.lastCheckInTimeSeconds)
    }
}
