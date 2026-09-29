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

package com.chriscartland.garage.ui.theme

import androidx.compose.ui.graphics.Color
import com.chriscartland.garage.domain.model.DoorColorState
import com.chriscartland.garage.presentation.DataFreshness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phone's half of "one grey" (strategy 1.2), shaped like the tile's
 * `aMutedDoorIsFullyGreyAndDimmed` so the two surfaces are held to the same
 * bar.
 */
class DoorMutingTest {
    private fun channels(c: Color) = Triple((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

    @Test
    fun aMutedDoorIsFullyGreyOnBothSchemes() {
        // Grey means the three channels agree. A partial desaturation — which
        // is what the palette's _STALE_ entries are — leaves a tint that reads
        // as a different door state.
        listOf(doorStatusLightScheme, doorStatusDarkScheme).forEach { scheme ->
            DoorColorState.entries.forEach { state ->
                val (r, g, b) = channels(DoorMuting.doorColor(scheme, state, DataFreshness.STALE))
                assertTrue("$state must be grey when muted, got r=$r g=$g b=$b", r == g && g == b)
            }
        }
    }

    @Test
    fun aFreshDoorKeepsItsColour() {
        // Positive control: a rule that greyed everything would pass the test above.
        val (r, g, b) = channels(DoorMuting.doorColor(doorStatusLightScheme, DoorColorState.OPEN, DataFreshness.FRESH))
        assertNotEquals("a fresh open door must not be grey", r, g)
        assertTrue(b != g)
    }

    @Test
    fun theGreyDoesNotDependOnWhyTheDoorIsMuted() {
        // The whole finding: a stale check-in and a failed fetch are one
        // verdict (isMuted) and must be one grey. Both are STALE here; the
        // old code differed by which palette entry it started from, which
        // this rule no longer consults.
        val stale = DoorMuting.doorColor(doorStatusLightScheme, DoorColorState.OPEN, DataFreshness.STALE)
        val settling = DoorMuting.doorColor(doorStatusLightScheme, DoorColorState.OPEN, DataFreshness.SETTLING)
        assertEquals("SETTLING and STALE are both muted and must paint the same grey", stale, settling)
    }
}
