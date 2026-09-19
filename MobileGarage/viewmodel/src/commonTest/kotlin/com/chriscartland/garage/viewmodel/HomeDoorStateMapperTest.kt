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
import com.chriscartland.garage.presentation.DataFreshness
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
            isFetchError = false,
            isSettling = false,
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
            isFetchError = false,
            isSettling = false,
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
            isFetchError = false,
            isSettling = false,
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
            isFetchError = false,
            isSettling = false,
        )
        assertNull(state.warning)
        assertNull(state.sinceStatus)
        assertEquals(VoiceDoorState.UNKNOWN, state.voice)
        // An empty cache is not current. Without this, `hasData = event != null`
        // could degenerate to `true` and a cold start would report FRESH — an
        // un-muted "Connecting…" card with every banner suppressed for as long
        // as the cache stayed empty.
        assertEquals(DataFreshness.STALE, state.freshness)
    }

    /**
     * Freshness is a field of the SAME node as the stale pill, so the muted
     * art and the banner that explains it are computed from one snapshot.
     * The three cases below walk the whole escalation from a single input
     * changing.
     */
    @Test
    fun aCurrentDoorIsFresh() {
        val state = HomeDoorStateMapper.compute(
            event = DoorEvent(doorPosition = DoorPosition.CLOSED, lastChangeTimeSeconds = 900L),
            isCheckInStale = false,
            nowEpochSeconds = 1000L,
            isFetchError = false,
            isSettling = false,
        )
        assertEquals(DataFreshness.FRESH, state.freshness)
    }

    @Test
    fun aStaleDoorInsideTheWindowIsMutedButUnspoken() {
        val state = HomeDoorStateMapper.compute(
            event = DoorEvent(doorPosition = DoorPosition.CLOSED, lastChangeTimeSeconds = 900L),
            isCheckInStale = true,
            nowEpochSeconds = 1000L,
            isFetchError = false,
            isSettling = true,
        )
        assertEquals(DataFreshness.SETTLING, state.freshness)
        // Still the stale pill, still a refused voice gate — the settle
        // window changes how LOUD the screen is, never what is true.
        assertEquals(true, state.isCheckInStale)
        assertEquals(VoiceDoorState.UNKNOWN, state.voice)
    }

    @Test
    fun aStaleDoorOutsideTheWindowIsSpoken() {
        val state = HomeDoorStateMapper.compute(
            event = DoorEvent(doorPosition = DoorPosition.CLOSED, lastChangeTimeSeconds = 900L),
            isCheckInStale = true,
            nowEpochSeconds = 1000L,
            isFetchError = false,
            isSettling = false,
        )
        assertEquals(DataFreshness.STALE, state.freshness)
    }

    /** A failed refresh keeps the door it was showing, and says so. */
    @Test
    fun aFetchErrorIsNotCurrentEvenWithAGoodEvent() {
        val state = HomeDoorStateMapper.compute(
            event = DoorEvent(doorPosition = DoorPosition.OPEN, lastChangeTimeSeconds = 900L),
            isCheckInStale = false,
            nowEpochSeconds = 1000L,
            isFetchError = true,
            isSettling = false,
        )
        assertEquals(DataFreshness.STALE, state.freshness)
        // The event survives: stale-while-revalidate means the last good
        // reading stays on screen, muted rather than erased.
        assertEquals(900L, state.sinceStatus?.sinceEpochSeconds)
    }
}
