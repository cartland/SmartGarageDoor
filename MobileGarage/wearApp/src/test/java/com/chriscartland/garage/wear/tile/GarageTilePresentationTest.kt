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

package com.chriscartland.garage.wear.tile

import com.chriscartland.garage.domain.model.DoorColorState
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.GarageDoorPalette
import com.chriscartland.garage.presentation.DataFreshness
import com.chriscartland.garage.presentation.DoorHeadlineMapper
import com.chriscartland.garage.presentation.StatusHeadline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tile's words and colours. Resource IDs and ARGB ints, so none of this
 * needs a `Context` — which is why they were split out of the layout.
 */
class GarageTilePresentationTest {
    // --- words -----------------------------------------------------------

    @Test
    fun everyDoorPositionHasATileWord() {
        // A position with no word would render a crash, not a blank. Sweeping
        // the enum covers a position added later without anyone remembering.
        DoorPosition.entries.forEach { position ->
            val headline = StatusHeadline.Door(DoorHeadlineMapper.forPosition(position))
            val resId = GarageTileWords.headline(headline)
            assertNotEquals("no tile word for $position", 0, resId)
        }
    }

    @Test
    fun theTileUsesTheDoorScreensOwnWords() {
        // Not a new vocabulary: the tile and the screen behind it must not
        // describe the same door differently. Asserting the resource identity
        // is what keeps that true through a rename.
        assertEquals(
            com.chriscartland.garage.wear.R.string.door_state_open,
            GarageTileWords.headline(StatusHeadline.Door(DoorHeadlineMapper.forPosition(DoorPosition.OPEN))),
        )
        assertEquals(
            com.chriscartland.garage.wear.R.string.door_state_no_signal,
            GarageTileWords.headline(StatusHeadline.NoSignal),
        )
    }

    // The age-line tests are gone with the age line. The tile no longer
    // formats a duration at all: the number comes from the renderer's own
    // clock (GarageDoorTileLayout.durationInState), so there is nothing here
    // to assert about its wording. What replaced them is
    // GlanceStatusMapperTest, which pins WHICH instant is offered and when it
    // is withheld.

    // --- colours ---------------------------------------------------------

    @Test
    fun aFreshDoorUsesTheSharedPaletteUntouched() {
        // The tile must not invent its own greens and reds — ADR-032 makes
        // the door a brand-locked surface.
        assertEquals(
            GarageDoorPalette.OPEN_FRESH_DARK.toInt(),
            GarageTileColors.doorFill(DoorColorState.OPEN, DataFreshness.FRESH),
        )
        assertEquals(
            GarageDoorPalette.CLOSED_FRESH_DARK.toInt(),
            GarageTileColors.doorFill(DoorColorState.CLOSED, DataFreshness.FRESH),
        )
    }

    @Test
    fun aMutedDoorIsFullyGreyAndDimmed() {
        // The user's own words for this state were "grayscale and dim". Grey
        // means the three channels agree; a partial desaturation would leave
        // a tint and read as a different door state.
        val muted = GarageTileColors.doorFill(DoorColorState.OPEN, DataFreshness.STALE)
        val red = (muted shr 16) and 0xFF
        val green = (muted shr 8) and 0xFF
        val blue = muted and 0xFF
        assertEquals("muted red and green channels must match", red, green)
        assertEquals("muted green and blue channels must match", green, blue)
        assertTrue("and it must be dimmed", ((muted shr 24) and 0xFF) < 0xFF)
    }

    @Test
    fun mutingDoesNotCollapseTwoDoorStatesIntoTheSameGrey() {
        // Rec. 709 luma rather than a channel average, which is the reason
        // FreshnessTint exists: averaging would take the red far darker than
        // the green, turning "we are not sure" into what reads as a different
        // door. They should be CLOSE but need not be identical — what must
        // not happen is the reverse, a muted red landing on the unknown grey.
        val mutedOpen = GarageTileColors.doorFill(DoorColorState.OPEN, DataFreshness.STALE)
        val mutedUnknown = GarageTileColors.doorFill(DoorColorState.UNKNOWN, DataFreshness.STALE)
        assertNotEquals(
            "a muted door must not be indistinguishable from the unknown door",
            mutedUnknown,
            mutedOpen,
        )
    }

    @Test
    fun theColourMappingCanActuallyFail() {
        // Positive control for the equality assertions above: a doorFill that
        // returned one constant would satisfy several of them. Two states
        // must differ while fresh.
        assertNotEquals(
            GarageTileColors.doorFill(DoorColorState.OPEN, DataFreshness.FRESH),
            GarageTileColors.doorFill(DoorColorState.CLOSED, DataFreshness.FRESH),
        )
    }

    @Test
    fun theTextOnTheDoorDimsWithIt() {
        // A bright word on a drained fill reads as a rendering fault, not as
        // a deliberate state.
        val fresh = (GarageTileColors.onDoorFill(DataFreshness.FRESH) shr 24) and 0xFF
        val muted = (GarageTileColors.onDoorFill(DataFreshness.STALE) shr 24) and 0xFF
        assertEquals(0xFF, fresh)
        assertTrue("muted text must be dimmer than fresh text", muted < fresh)
    }
}
