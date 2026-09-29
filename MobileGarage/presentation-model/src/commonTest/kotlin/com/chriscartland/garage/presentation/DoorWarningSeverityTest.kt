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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DoorWarningSeverityTest {
    @Test
    fun aDoorStillMovingIsAnAlarm() {
        assertEquals(WarningSeverity.ALARM, DoorWarning.OpeningTooLong.severity)
        assertEquals(WarningSeverity.ALARM, DoorWarning.ClosingTooLong.severity)
        assertEquals(WarningSeverity.ALARM, DoorWarning.SensorConflict.severity)
        assertEquals(WarningSeverity.ALARM, DoorWarning.ServerMessage("check the door").severity)
    }

    @Test
    fun aMisalignedOpenDoorIsAdvisory() {
        // Positive control for the test above: a severity that was ALARM for
        // everything would pass it.
        assertEquals(WarningSeverity.ADVISORY, DoorWarning.OpenMisaligned.severity)
    }

    @Test
    fun bothSeveritiesAreReachable() {
        val reached = listOf(
            DoorWarning.OpeningTooLong,
            DoorWarning.ClosingTooLong,
            DoorWarning.OpenMisaligned,
            DoorWarning.SensorConflict,
            DoorWarning.ServerMessage("x"),
        ).map { it.severity }.toSet()
        assertTrue(reached == WarningSeverity.entries.toSet(), "a severity nothing reaches is dead colour: $reached")
    }
}
