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

package com.chriscartland.garage.widget

import androidx.compose.ui.graphics.Color
import com.chriscartland.garage.domain.model.DoorColorState
import com.chriscartland.garage.presentation.DataFreshness
import com.chriscartland.garage.ui.theme.FreshnessTint
import com.chriscartland.garage.ui.theme.doorStatusDarkScheme
import com.chriscartland.garage.ui.theme.doorStatusLightScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget's half of "one grey" (strategy 1.2). Until this change the
 * widget read the palette's partial `_STALE_` entry, so a stale open door
 * stayed brick-red (`#9A655C`) where the tile and the Home card went grey.
 */
class GarageWidgetColorsTest {
    private fun channels(c: Color) = Triple((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

    @Test
    fun aMutedDoorIsFullyGreyAndDimmedOnBothSchemes() {
        listOf(doorStatusLightScheme, doorStatusDarkScheme).forEach { scheme ->
            DoorColorState.entries.forEach { state ->
                val fill = GarageWidgetColors.fill(scheme, state, DataFreshness.STALE)
                val (r, g, b) = channels(fill)
                assertTrue("$state must be grey when muted, got r=$r g=$g b=$b", r == g && g == b)
                assertEquals("and dimmed by the shared alpha", FreshnessTint.alphaFor(DataFreshness.STALE), fill.alpha, 0.001f)
            }
        }
    }

    @Test
    fun aFreshDoorIsNeitherGreyNorDimmed() {
        // Positive control.
        val fill = GarageWidgetColors.fill(doorStatusLightScheme, DoorColorState.OPEN, DataFreshness.FRESH)
        val (r, g, b) = channels(fill)
        assertTrue("a fresh open door must keep its red", r != g || g != b)
        assertEquals(1f, fill.alpha, 0.001f)
    }
}
