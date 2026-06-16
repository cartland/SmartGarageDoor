package com.chriscartland.garage.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Contract tests for the resolved-on-close FCM data payload parser.
 *
 * The same wire-contract fixture is deep-equal'd by the server's
 * ResolvedNotificationFCMFakeTest.ts, so a unilateral key rename on either
 * side breaks at least one test.
 */
class DoorResolvedPayloadTest {
    @Test
    fun validResolvedPayloadParses() {
        val payload = mapOf(
            "kind" to "open_door_resolved",
            "openTimestampSeconds" to "1800000000",
            "closeTimestampSeconds" to "1800000840",
        )
        val content = DoorResolvedPayload.parse(payload)
        assertNotNull(content)
        assertEquals(1800000000L, content.openTimestampSeconds)
        assertEquals(1800000840L, content.closeTimestampSeconds)
    }

    @Test
    fun wrongKindReturnsNull() {
        val payload = mapOf(
            "kind" to "open_door_warning",
            "openTimestampSeconds" to "1800000000",
            "closeTimestampSeconds" to "1800000840",
        )
        assertNull(DoorResolvedPayload.parse(payload), "Only open_door_resolved should parse in Phase 1")
    }

    @Test
    fun missingKindReturnsNull() {
        val payload = mapOf(
            "openTimestampSeconds" to "1800000000",
            "closeTimestampSeconds" to "1800000840",
        )
        assertNull(DoorResolvedPayload.parse(payload))
    }

    @Test
    fun missingTimestampReturnsNull() {
        val payload = mapOf("kind" to "open_door_resolved", "openTimestampSeconds" to "1800000000")
        assertNull(DoorResolvedPayload.parse(payload), "Missing closeTimestampSeconds should return null")
    }

    @Test
    fun nonNumericTimestampReturnsNull() {
        val payload = mapOf(
            "kind" to "open_door_resolved",
            "openTimestampSeconds" to "not-a-number",
            "closeTimestampSeconds" to "1800000840",
        )
        assertNull(DoorResolvedPayload.parse(payload))
    }

    @Test
    fun emptyPayloadReturnsNull() {
        assertNull(DoorResolvedPayload.parse(emptyMap()))
    }

    @Test
    fun fixturePayloadResolvedParses() {
        val payload = loadFixture("payload_resolved.json")
        val content = DoorResolvedPayload.parse(payload)
        assertNotNull(content, "Wire-contract fixture must parse")
        assertEquals(1800000000L, content.openTimestampSeconds)
        assertEquals(1800000840L, content.closeTimestampSeconds)
    }

    private fun loadFixture(name: String): Map<String, String> {
        val fixtureFile = File("../../wire-contracts/openDoorResolved/$name")
        require(fixtureFile.exists()) {
            "Wire-contract fixture missing: ${fixtureFile.absolutePath}. " +
                "Tests run from the :data module dir; fixture lives at " +
                "<repo>/wire-contracts/openDoorResolved/."
        }
        return Json
            .parseToJsonElement(fixtureFile.readText())
            .jsonObject
            .mapValues { (it.value as JsonPrimitive).content }
    }
}
