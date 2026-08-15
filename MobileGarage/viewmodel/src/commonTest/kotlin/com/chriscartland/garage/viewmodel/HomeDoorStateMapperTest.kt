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
 */

package com.chriscartland.garage.viewmodel

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.presentation.DoorWarning
import com.chriscartland.garage.usecase.VoiceDoorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The composition is thin by design (each field delegates to its own
 * tested mapper); what THIS suite pins is the G7 property the collapse
 * exists for — the card and the voice gate are views of the same
 * computed snapshot, so states that disagree-by-design are impossible.
 */
class HomeDoorStateMapperTest {
    @Test
    fun staleCheckInShowsThePillAndRefusesVoiceInTheSameValue() {
        // Pre-G7 these were separate nodes: the pill could show stale
        // while the gate still held a fresh projection (or vice versa)
        // for a frame. Now they are fields of one value.
        val state = HomeDoorStateMapper.compute(
            event = DoorEvent(doorPosition = DoorPosition.CLOSED, lastChangeTimeSeconds = 900L),
            isCheckInStale = true,
            nowEpochSeconds = 1000L,
        )
        assertEquals(true, state.isCheckInStale)
        // Stale cache must never pass the direction gate (the
        // wrong-direction hazard in VoiceDoorStateMapper's KDoc).
        assertEquals(VoiceDoorState.UNKNOWN, state.voice)
    }

    @Test
    fun aCleanClosedDoorIsQuietEverywhereInTheSameValue() {
        val state = HomeDoorStateMapper.compute(
            event = DoorEvent(doorPosition = DoorPosition.CLOSED, lastChangeTimeSeconds = 900L),
            isCheckInStale = false,
            nowEpochSeconds = 1000L,
        )
        assertNull(state.warning)
        assertEquals(false, state.isCheckInStale)
        assertEquals(VoiceDoorState.CLOSED, state.voice)
        assertEquals(900L, state.sinceStatus?.sinceEpochSeconds)
    }

    @Test
    fun anAnomalyWarnsTheCardAndTheGateFromOneSnapshot() {
        // The state that motivated the warning chip is also a state the
        // gate treats specially — computed here from ONE (event, stale,
        // now) triple, so the two surfaces cannot disagree about which
        // door they are describing.
        val state = HomeDoorStateMapper.compute(
            event = DoorEvent(
                doorPosition = DoorPosition.OPENING_TOO_LONG,
                lastChangeTimeSeconds = 900L,
            ),
            isCheckInStale = false,
            nowEpochSeconds = 1000L,
        )
        assertEquals(DoorWarning.OpeningTooLong, state.warning)
        assertEquals(VoiceDoorState.STUCK, state.voice)
    }

    @Test
    fun nullEventProducesTheHonestEmptySurface() {
        val state = HomeDoorStateMapper.compute(
            event = null,
            isCheckInStale = false,
            nowEpochSeconds = 1000L,
        )
        assertNull(state.warning)
        assertNull(state.sinceStatus)
        assertEquals(VoiceDoorState.UNKNOWN, state.voice)
    }
}
