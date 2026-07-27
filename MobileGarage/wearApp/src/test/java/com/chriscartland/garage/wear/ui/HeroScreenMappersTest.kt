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

import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.wear.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins which door positions are allowed to predict what a press will do.
 *
 * The button is a remote: it sends one press and the garage decides whether
 * that opens, closes, or pauses the door. We may only promise an outcome
 * where an affirmative sensor reading backs it up — CLOSED and OPEN (plus
 * OPEN_MISALIGNED, which the door state label already renders as "Open").
 * Every other position is inferred from history with both sensors quiet, so
 * the hint names our own action instead.
 *
 * Getting this wrong is a correctness bug, not a copy nit: telling someone
 * "Hold to open" while the door is already opening would promise the
 * opposite of what a press actually does.
 */
class HeroScreenMappersTest {
    @Test
    fun affirmativeSensorPositionsPredictTheOutcome() {
        assertEquals(
            R.string.button_hint_hold_to_open,
            HeroScreenMappers.holdHint(DoorPosition.CLOSED, hasDoorData = true),
        )
        assertEquals(
            R.string.button_hint_hold_to_close,
            HeroScreenMappers.holdHint(DoorPosition.OPEN, hasDoorData = true),
        )
    }

    @Test
    fun misalignedIsTreatedAsOpen() {
        // A confident Open whose sensor dropped out for under 3 seconds. The
        // door state label already shows "Open", and the hint must agree with
        // the line directly above it.
        assertEquals(
            R.string.button_hint_hold_to_close,
            HeroScreenMappers.holdHint(DoorPosition.OPEN_MISALIGNED, hasDoorData = true),
        )
    }

    @Test
    fun everyInferredPositionRefusesToPredict() {
        val inferred = DoorPosition.entries - AFFIRMATIVE
        // Guard against the enum shrinking to nothing under a refactor and
        // making this test pass vacuously.
        assert(inferred.isNotEmpty())
        inferred.forEach { position ->
            assertEquals(
                "$position has no affirmative sensor reading, so it must not predict an outcome",
                R.string.button_hint_hold_to_press_remote,
                HeroScreenMappers.holdHint(position, hasDoorData = true),
            )
        }
    }

    @Test
    fun coldStartRefusesToPredictEvenForAffirmativePositions() {
        // No door event yet: the label reads "Connecting…" and the position is
        // a placeholder, so it carries no sensor authority at all.
        DoorPosition.entries.forEach { position ->
            assertEquals(
                "$position with no door data must not predict an outcome",
                R.string.button_hint_hold_to_press_remote,
                HeroScreenMappers.holdHint(position, hasDoorData = false),
            )
        }
    }

    private companion object {
        /**
         * The only positions backed by a sensor that affirmatively reports
         * them. Adding a new [DoorPosition] leaves it out of this set, so
         * `everyInferredPositionRefusesToPredict` covers it automatically and
         * the safe default (no prediction) applies until someone decides
         * otherwise on purpose.
         */
        val AFFIRMATIVE = setOf(
            DoorPosition.CLOSED,
            DoorPosition.OPEN,
            DoorPosition.OPEN_MISALIGNED,
        )
    }
}
