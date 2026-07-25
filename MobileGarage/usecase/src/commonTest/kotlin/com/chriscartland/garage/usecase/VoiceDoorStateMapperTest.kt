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
    // The projection is the live-wiring safety mapping: only the two
    // clean terminal positions are actionable; transits are MOVING;
    // every anomaly and a stale check-in force UNKNOWN (gate refuses).
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
        val anomalies = listOf(
            DoorPosition.OPENING_TOO_LONG,
            DoorPosition.CLOSING_TOO_LONG,
            DoorPosition.OPEN_MISALIGNED,
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
