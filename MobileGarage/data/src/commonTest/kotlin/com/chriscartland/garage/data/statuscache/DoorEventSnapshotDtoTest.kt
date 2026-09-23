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

package com.chriscartland.garage.data.statuscache

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoorEventSnapshotDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun everyDoorPositionSurvivesTheRoundTrip() {
        // The persisted door state is the whole point of the snapshot; a
        // position that cannot make the trip would render as UNKNOWN on a
        // tile with no other symptom. Sweeping the enum means a position
        // added later is covered without anyone remembering to add a case.
        DoorPosition.entries.forEach { position ->
            val original = DoorEvent(
                doorPosition = position,
                lastCheckInTimeSeconds = 1_700_000_000L,
                lastChangeTimeSeconds = 1_699_999_000L,
            )
            val encoded = json.encodeToString(DoorEventSnapshotDto.fromDomain(original))
            val decoded = json.decodeFromString<DoorEventSnapshotDto>(encoded).toDomain()
            assertEquals(position, decoded.doorPosition, "round trip lost $position")
            assertEquals(1_700_000_000L, decoded.lastCheckInTimeSeconds)
            assertEquals(1_699_999_000L, decoded.lastChangeTimeSeconds)
        }
    }

    @Test
    fun theRoundTripCanActuallyFail() {
        // Positive control for the sweep above: if `fromDomain` dropped the
        // position, or `toDomain` returned a constant, every assertion there
        // would still pass for whichever position happened to be the
        // constant. Proving two distinct positions map to two distinct
        // encodings is what makes the sweep mean something.
        val closed = json.encodeToString(DoorEventSnapshotDto.fromDomain(DoorEvent(doorPosition = DoorPosition.CLOSED)))
        val open = json.encodeToString(DoorEventSnapshotDto.fromDomain(DoorEvent(doorPosition = DoorPosition.OPEN)))
        assertTrue(closed != open, "CLOSED and OPEN encoded identically: $closed")
    }

    @Test
    fun anUnknownStoredPositionDecodesToUnknownRatherThanDiscardingTheSnapshot() {
        // Forward compatibility: a server that adds a position, written by a
        // newer build and read by an older one. Losing the whole snapshot
        // would also lose the timestamps, and the age of the reading is the
        // part a stale-looking tile most needs.
        val decoded = json
            .decodeFromString<DoorEventSnapshotDto>(
                """{"doorPosition":"DOOR_ATE_THE_CAT","lastCheckInTimeSeconds":42,"lastChangeTimeSeconds":41}""",
            ).toDomain()
        assertEquals(DoorPosition.UNKNOWN, decoded.doorPosition)
        assertEquals(42L, decoded.lastCheckInTimeSeconds)
        assertEquals(41L, decoded.lastChangeTimeSeconds)
    }

    @Test
    fun anAbsentPositionStaysNullAndDoesNotBecomeUnknown() {
        // These two are NOT the same door and must not collapse into one.
        // `null` means we have never heard anything, which every freshness
        // rule reads as "no data"; UNKNOWN is the garage affirmatively
        // reporting that its own sensors disagree with each other. A snapshot
        // that turned the first into the second would make an empty cache
        // indistinguishable from a broken sensor.
        val decoded = json.decodeFromString<DoorEventSnapshotDto>("""{}""").toDomain()
        assertNull(decoded.doorPosition)
    }

    @Test
    fun theServerMessageIsNotPersisted() {
        // Deliberate, per the DTO's KDoc: no watch surface renders it, and it
        // is the one unbounded free-text field on a door event.
        val encoded = json.encodeToString(
            DoorEventSnapshotDto.fromDomain(
                DoorEvent(doorPosition = DoorPosition.OPEN, message = "human readable note"),
            ),
        )
        assertTrue(!encoded.contains("human readable note"), "message reached the snapshot: $encoded")
    }

    @Test
    fun aSnapshotWrittenByAnOlderBuildStillDecodes() {
        // The envelope's schemaVersion is the deliberate-invalidation lever;
        // adding a FIELD must not need it. Defaults on every property are
        // what make that true, so pin it.
        val decoded = json
            .decodeFromString<DoorEventSnapshotDto>("""{"doorPosition":"CLOSED"}""")
            .toDomain()
        assertEquals(DoorPosition.CLOSED, decoded.doorPosition)
        assertNull(decoded.lastCheckInTimeSeconds)
    }
}
