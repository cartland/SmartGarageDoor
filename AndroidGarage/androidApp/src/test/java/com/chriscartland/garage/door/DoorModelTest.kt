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

package com.chriscartland.garage.door

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoorModelTest {
    @Test
    fun primaryKeyIncludesTimestampAndPosition() {
        val event = DoorEvent(doorPosition = DoorPosition.CLOSED, lastChangeTimeSeconds = 1000L)
        assertEquals("1000:CLOSED", event.id)
    }

    @Test
    fun primaryKeyWithNullTimestamp() {
        val event = DoorEvent(doorPosition = DoorPosition.OPEN)
        assertEquals("null:OPEN", event.id)
    }

    @Test
    fun primaryKeyWithNullPosition() {
        val event = DoorEvent(lastChangeTimeSeconds = 1000L)
        assertEquals("1000:UNKNOWN", event.id)
    }

    @Test
    fun primaryKeyWithAllNulls() {
        val event = DoorEvent()
        assertEquals("null:UNKNOWN", event.id)
    }

    @Test
    fun differentPositionsDifferentKeys() {
        val closed = DoorEvent(doorPosition = DoorPosition.CLOSED, lastChangeTimeSeconds = 1000L)
        val open = DoorEvent(doorPosition = DoorPosition.OPEN, lastChangeTimeSeconds = 1000L)
        assertNotEquals(closed.id, open.id)
    }

    @Test
    fun defaultFieldsAreNull() {
        val event = DoorEvent()
        assertNull(event.doorPosition)
        assertNull(event.message)
        assertNull(event.lastCheckInTimeSeconds)
        assertNull(event.lastChangeTimeSeconds)
    }
}
