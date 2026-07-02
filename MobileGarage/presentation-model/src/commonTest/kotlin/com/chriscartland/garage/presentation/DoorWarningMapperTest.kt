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

package com.chriscartland.garage.presentation

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Shared (commonTest) contract for [DoorWarningMapper]. Moved out of
 * `androidApp`'s `HomeMapperTest` in the presentation-model realization
 * (ADR-031) — the warning mapping is now platform-free, so its tests guard
 * the contract on every platform (Android + iOS).
 *
 * Asserts on TYPE, not text — a copy revision in either UI must not break the
 * mapper.
 */
class DoorWarningMapperTest {
    @Test
    fun null_event_returns_null() = assertNull(DoorWarningMapper.forEvent(null))

    @Test
    fun open_no_warning() =
        assertNull(
            DoorWarningMapper.forEvent(DoorEvent(doorPosition = DoorPosition.OPEN, message = "anything")),
        )

    @Test
    fun closed_no_warning() =
        assertNull(
            DoorWarningMapper.forEvent(DoorEvent(doorPosition = DoorPosition.CLOSED, message = "anything")),
        )

    @Test
    fun opening_no_warning() = assertNull(DoorWarningMapper.forEvent(DoorEvent(doorPosition = DoorPosition.OPENING)))

    @Test
    fun closing_no_warning() = assertNull(DoorWarningMapper.forEvent(DoorEvent(doorPosition = DoorPosition.CLOSING)))

    @Test
    fun openingTooLong_uses_server_message_when_present() {
        val w = DoorWarningMapper.forEvent(
            DoorEvent(doorPosition = DoorPosition.OPENING_TOO_LONG, message = "Specific server text"),
        )
        assertEquals(DoorWarning.ServerMessage("Specific server text"), w)
    }

    @Test
    fun openingTooLong_falls_back_to_typed_default_when_message_null() {
        val w = DoorWarningMapper.forEvent(DoorEvent(doorPosition = DoorPosition.OPENING_TOO_LONG))
        assertEquals(DoorWarning.OpeningTooLong, w)
    }

    @Test
    fun openingTooLong_falls_back_to_typed_default_when_message_blank() {
        val w = DoorWarningMapper.forEvent(
            DoorEvent(doorPosition = DoorPosition.OPENING_TOO_LONG, message = "   "),
        )
        assertEquals(DoorWarning.OpeningTooLong, w)
    }

    @Test
    fun closingTooLong_default() {
        val w = DoorWarningMapper.forEvent(DoorEvent(doorPosition = DoorPosition.CLOSING_TOO_LONG))
        assertEquals(DoorWarning.ClosingTooLong, w)
    }

    @Test
    fun openMisaligned_default() {
        val w = DoorWarningMapper.forEvent(DoorEvent(doorPosition = DoorPosition.OPEN_MISALIGNED))
        assertEquals(DoorWarning.OpenMisaligned, w)
    }

    @Test
    fun sensorConflict_default() {
        val w = DoorWarningMapper.forEvent(DoorEvent(doorPosition = DoorPosition.ERROR_SENSOR_CONFLICT))
        assertEquals(DoorWarning.SensorConflict, w)
    }

    @Test
    fun unknown_uses_server_message_only_no_default() {
        // Unknown is too vague for a fixed default, so only the server's own
        // message is surfaced — and only when non-blank.
        assertNull(DoorWarningMapper.forEvent(DoorEvent(doorPosition = DoorPosition.UNKNOWN)))
        assertEquals(
            DoorWarning.ServerMessage("Server says X"),
            DoorWarningMapper.forEvent(DoorEvent(doorPosition = DoorPosition.UNKNOWN, message = "Server says X")),
        )
    }
}
