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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.DoorPosition
import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceDoorStateMapperTest {
    // The projection is the live-wiring safety mapping: the two clean
    // terminal positions are actionable; clean transits are MOVING; a
    // transit past its deadline is STUCK (close-only); the remaining
    // anomalies and a stale check-in force UNKNOWN (gate refuses).
    @Test
    fun projectionMapsCleanStatesAndDeniesAnomalies() {
        assertEquals(
            VoiceDoorState.CLOSED,
            VoiceDoorStateMapper.project(DoorPosition.CLOSED, isCheckInStale = false),
        )
        assertEquals(
            VoiceDoorState.OPEN,
            VoiceDoorStateMapper.project(DoorPosition.OPEN, isCheckInStale = false),
        )
        assertEquals(
            VoiceDoorState.MOVING,
            VoiceDoorStateMapper.project(DoorPosition.OPENING, isCheckInStale = false),
        )
        assertEquals(
            VoiceDoorState.MOVING,
            VoiceDoorStateMapper.project(DoorPosition.CLOSING, isCheckInStale = false),
        )
        // A transit that ran past its deadline is stopped partway, which
        // is definitively neither closed nor open — so it keeps a usable
        // direction rather than collapsing into UNKNOWN.
        assertEquals(
            VoiceDoorState.STUCK,
            VoiceDoorStateMapper.project(DoorPosition.OPENING_TOO_LONG, isCheckInStale = false),
        )
        assertEquals(
            VoiceDoorState.STUCK,
            VoiceDoorStateMapper.project(DoorPosition.CLOSING_TOO_LONG, isCheckInStale = false),
        )
        val anomalies = listOf(
            DoorPosition.ERROR_SENSOR_CONFLICT,
            DoorPosition.UNKNOWN,
            null,
        )
        anomalies.forEach { position ->
            assertEquals(
                VoiceDoorState.UNKNOWN,
                VoiceDoorStateMapper.project(position, isCheckInStale = false),
                "Anomalous position $position must project to UNKNOWN",
            )
        }
    }

    /**
     * A misaligned door is an OPEN door with a flaky open sensor, and it is
     * the one case where closing by voice matters most.
     *
     * The server only emits it when the closed sensor reads NOT-closed, so
     * this is not a guess and the wrong-direction hazard cannot arise: CLOSE
     * goes the right way, OPEN is refused as already-open. Every other surface
     * already treats it as Open (label, hold hint, door art); voice refusing it
     * was the outlier.
     */
    @Test
    fun misalignedProjectsToOpenSoItCanStillBeClosed() {
        assertEquals(
            VoiceDoorState.OPEN,
            VoiceDoorStateMapper.project(DoorPosition.OPEN_MISALIGNED, isCheckInStale = false),
        )
    }

    /**
     * A door stopped partway is the case where sending a press matters most:
     * it may be obstructed and need another go, and it is sitting open to the
     * street while it waits.
     *
     * The boundary asserted here is what keeps STUCK from becoming a synonym
     * for "anomalous". A stuck transit has a known position (the server rules
     * out Closed, Open, and ErrorSensorConflict before it ever reports
     * TooLong), so CLOSE is well defined. A sensor conflict does not — its
     * sensors actively disagree — so it must stay UNKNOWN and keep refusing
     * everything. Without the second half of this test, mapping every anomaly
     * to STUCK would pass the first half.
     */
    @Test
    fun stuckTransitsAreActionableButASensorConflictIsNot() {
        listOf(DoorPosition.OPENING_TOO_LONG, DoorPosition.CLOSING_TOO_LONG).forEach { position ->
            assertEquals(
                VoiceDoorState.STUCK,
                VoiceDoorStateMapper.project(position, isCheckInStale = false),
                "$position is stopped partway, so closing it must stay available",
            )
        }
        assertEquals(
            VoiceDoorState.UNKNOWN,
            VoiceDoorStateMapper.project(DoorPosition.ERROR_SENSOR_CONFLICT, isCheckInStale = false),
            "Conflicting sensors give no position to reason from",
        )
    }

    @Test
    fun staleCheckInForcesUnknownEvenWhenPositionIsClean() {
        DoorPosition.entries.forEach { position ->
            assertEquals(
                VoiceDoorState.UNKNOWN,
                VoiceDoorStateMapper.project(position, isCheckInStale = true),
                "Stale check-in must project $position to UNKNOWN",
            )
        }
    }
}
