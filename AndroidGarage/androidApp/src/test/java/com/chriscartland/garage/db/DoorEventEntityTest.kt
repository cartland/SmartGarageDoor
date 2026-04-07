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

package com.chriscartland.garage.db

import com.chriscartland.garage.datalocal.DoorEventEntity
import com.chriscartland.garage.datalocal.toDomain
import com.chriscartland.garage.datalocal.toEntity
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoorEventEntityTest {
    @Test
    fun toDomainPreservesAllFields() {
        val entity = DoorEventEntity(
            doorPosition = DoorPosition.CLOSED,
            message = "Door is closed",
            lastCheckInTimeSeconds = 1000L,
            lastChangeTimeSeconds = 900L,
        )
        val domain = entity.toDomain()
        assertEquals(DoorPosition.CLOSED, domain.doorPosition)
        assertEquals("Door is closed", domain.message)
        assertEquals(1000L, domain.lastCheckInTimeSeconds)
        assertEquals(900L, domain.lastChangeTimeSeconds)
    }

    @Test
    fun toEntityPreservesAllFields() {
        val domain = DoorEvent(
            doorPosition = DoorPosition.OPEN,
            message = "Door is open",
            lastCheckInTimeSeconds = 2000L,
            lastChangeTimeSeconds = 1800L,
        )
        val entity = domain.toEntity()
        assertEquals(DoorPosition.OPEN, entity.doorPosition)
        assertEquals("Door is open", entity.message)
        assertEquals(2000L, entity.lastCheckInTimeSeconds)
        assertEquals(1800L, entity.lastChangeTimeSeconds)
    }

    @Test
    fun roundTripPreservesData() {
        val original = DoorEvent(
            doorPosition = DoorPosition.OPENING_TOO_LONG,
            message = "Garage opening too long",
            lastCheckInTimeSeconds = 5000L,
            lastChangeTimeSeconds = 4500L,
        )
        val roundTripped = original.toEntity().toDomain()
        assertEquals(original, roundTripped)
    }

    @Test
    fun roundTripWithNullFields() {
        val original = DoorEvent()
        val roundTripped = original.toEntity().toDomain()
        assertEquals(original, roundTripped)
        assertNull(roundTripped.doorPosition)
        assertNull(roundTripped.message)
        assertNull(roundTripped.lastCheckInTimeSeconds)
        assertNull(roundTripped.lastChangeTimeSeconds)
    }

    @Test
    fun entityIdDerivedFromChangeTimeAndPosition() {
        val entity = DoorEventEntity(
            doorPosition = DoorPosition.CLOSED,
            lastChangeTimeSeconds = 12345L,
        )
        assertEquals("12345:CLOSED", entity.id)
    }

    @Test
    fun entityIdWithNullPosition() {
        val entity = DoorEventEntity(
            doorPosition = null,
            lastChangeTimeSeconds = 12345L,
        )
        assertEquals("12345:UNKNOWN", entity.id)
    }

    @Test
    fun entityIdWithNullTimestamp() {
        val entity = DoorEventEntity(
            doorPosition = DoorPosition.OPEN,
            lastChangeTimeSeconds = null,
        )
        assertEquals("null:OPEN", entity.id)
    }

    @Test
    fun allDoorPositionsRoundTrip() {
        DoorPosition.entries.forEach { position ->
            val original = DoorEvent(doorPosition = position)
            val roundTripped = original.toEntity().toDomain()
            assertEquals(
                "DoorPosition.$position failed round trip",
                original,
                roundTripped,
            )
        }
    }
}
