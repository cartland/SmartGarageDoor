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

package com.chriscartland.garage.wear.ui

import com.chriscartland.garage.wear.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** The chip says what the hero screen says for the same moment. */
class DoorActivityWordsTest {
    @Test
    fun theChipUsesTheHeroScreensOwnWords() {
        assertEquals(R.string.button_hint_sending, DoorActivityWords.status(DoorActivity.PressSending))
        assertEquals(R.string.button_hint_waiting_for_door, DoorActivityWords.status(DoorActivity.PressAwaitingDoor))
        assertEquals(R.string.door_state_opening, DoorActivityWords.status(DoorActivity.DoorMoving(DoorTravel.OPENING)))
        assertEquals(R.string.door_state_closing, DoorActivityWords.status(DoorActivity.DoorMoving(DoorTravel.CLOSING)))
    }

    @Test
    fun everyMomentHasItsOwnLine() {
        // Positive control for the table above: four moments, four strings.
        val lines = listOf(
            DoorActivity.PressSending,
            DoorActivity.PressAwaitingDoor,
            DoorActivity.DoorMoving(DoorTravel.OPENING),
            DoorActivity.DoorMoving(DoorTravel.CLOSING),
        ).map { DoorActivityWords.status(it) }
        assertEquals(lines.size, lines.toSet().size)
    }
}
