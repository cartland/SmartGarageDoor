package com.chriscartland.garage.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [DoorPosition] enum values.
 *
 * DoorPosition names MUST match server strings exactly.
 * If a name changes, the network response parsing and Room database will break.
 * This test catches accidental renames.
 */
class DoorPositionTest {

    @Test
    fun serverStringsMatchExpectedValues() {
        val expected = listOf(
            "UNKNOWN",
            "CLOSED",
            "OPENING",
            "OPENING_TOO_LONG",
            "OPEN",
            "OPEN_MISALIGNED",
            "CLOSING",
            "CLOSING_TOO_LONG",
            "ERROR_SENSOR_CONFLICT",
        )
        val actual = DoorPosition.entries.map { it.name }
        assertEquals(expected, actual)
    }

    @Test
    fun enumCountMatchesExpected() {
        assertEquals(
            "DoorPosition count changed — update server, network parsing, and this test",
            9,
            DoorPosition.entries.size,
        )
    }
}
