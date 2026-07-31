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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * The ring/content boundary.
 *
 * Worth pinning because the bug these rules exist to prevent is invisible in
 * the place you would look for it. A few dp of ring-over-text overlap reads as
 * "a bit tight" on one watch size and as a line struck through the label on
 * another, and the geometry that decides it is a chord length nobody computes
 * by eye. Both shipping round sizes are checked in every case below: 192dp
 * (small round, e.g. a 41mm watch) and 227dp (large round, e.g. 45mm).
 */
class HeroLayoutTest {
    /**
     * The rule the whole object exists for: a bottom-anchored block's bottom
     * CORNERS — its worst point — land inside the content circle, so the ring
     * has the outer band entirely to itself.
     */
    @Test
    fun theBottomBlocksCornersLandInsideTheContentCircle() {
        ROUND_SIZES.forEach { diameter ->
            val width = diameter * BOTTOM_TEXT_WIDTH_FRACTION
            val inset = HeroLayout.bottomInsetDp(diameter, width)
            val cornerRadius = cornerDistance(diameter, width, inset)
            assertTrue(
                "$diameter dp: corner at $cornerRadius exceeds content radius " +
                    HeroLayout.contentRadiusDp(diameter),
                cornerRadius <= HeroLayout.contentRadiusDp(diameter) + TOLERANCE,
            )
        }
    }

    /**
     * The regression that motivated all of this. The shipped constant was a
     * flat 18dp with a 0.56 width, which put the label straight through the
     * ring — and, the part that makes a constant untenable, WORSE on the larger
     * watch, because the block's width scales with the screen while the ring's
     * thickness does not.
     */
    @Test
    fun theOldFlatInsetWouldHaveCollidedAndWorseOnTheLargerWatch() {
        val overlaps = ROUND_SIZES.map { diameter ->
            val width = diameter * 0.56f
            cornerDistance(diameter, width, OLD_FLAT_INSET_DP) -
                HeroLayout.contentRadiusDp(diameter)
        }
        overlaps.forEach { assertTrue("expected a collision, got $it", it > 0f) }
        assertTrue(
            "the larger watch should overlap more, got $overlaps",
            overlaps[1] > overlaps[0],
        )
    }

    /** A wider block has to sit higher; that relationship is the whole point. */
    @Test
    fun aWiderBlockIsPushedFurtherFromTheEdge() {
        ROUND_SIZES.forEach { diameter ->
            val narrow = HeroLayout.bottomInsetDp(diameter, diameter * 0.40f)
            val wide = HeroLayout.bottomInsetDp(diameter, diameter * 0.56f)
            assertTrue("$diameter dp: $wide should exceed $narrow", wide > narrow)
        }
    }

    /**
     * The bloom fills its lane and stops. Growing past the band is precisely
     * the old behaviour — it ran until the ring closed into a solid disc over
     * the door and both labels.
     */
    @Test
    fun theBloomFillsTheBandAndNeverLeavesIt() {
        assertEquals(HeroLayout.RING_STROKE_DP, HeroLayout.bloomStrokeDp(0f), TOLERANCE)
        assertEquals(HeroLayout.RING_BAND_DP, HeroLayout.bloomStrokeDp(1f), TOLERANCE)
        listOf(-1f, 0f, 0.5f, 1f, 2f).forEach { bloom ->
            val stroke = HeroLayout.bloomStrokeDp(bloom)
            assertTrue(
                "bloom=$bloom produced $stroke, outside the band",
                stroke >= HeroLayout.RING_STROKE_DP - TOLERANCE &&
                    stroke <= HeroLayout.RING_BAND_DP + TOLERANCE,
            )
        }
    }

    /**
     * The bloom grows INWARD only: its outer edge stays pinned to the ring's
     * outer edge at every step, and its inner edge never crosses the band into
     * content. Re-inset by half the current stroke is what buys this; widening
     * about a fixed centreline would spend half the growth off-screen and put
     * the rest somewhere the content circle never accounted for.
     */
    @Test
    fun theBloomGrowsInwardAndStopsAtTheContentCircle() {
        ROUND_SIZES.forEach { diameter ->
            val boxRadius = diameter / 2f - HeroLayout.RING_EDGE_PADDING_DP
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { bloom ->
                val stroke = HeroLayout.bloomStrokeDp(bloom)
                val centreline = boxRadius - HeroLayout.bloomArcInsetDp(stroke)
                assertEquals(
                    "bloom=$bloom outer edge drifted",
                    boxRadius,
                    centreline + stroke / 2f,
                    TOLERANCE,
                )
                assertTrue(
                    "bloom=$bloom inner edge entered the content circle",
                    centreline - stroke / 2f >= HeroLayout.contentRadiusDp(diameter) - TOLERANCE,
                )
            }
        }
    }

    /** A block too wide to fit anywhere is a caller bug, not a render. */
    @Test
    fun anImpossiblyWideBlockIsReportedRatherThanSquashed() {
        ROUND_SIZES.forEach { diameter ->
            val tooWide = HeroLayout.maxBottomBlockWidthDp(diameter) + 1f
            assertEquals(diameter / 2f, HeroLayout.bottomInsetDp(diameter, tooWide), TOLERANCE)
            assertTrue(
                "$diameter dp: the shipped width must be usable",
                diameter * BOTTOM_TEXT_WIDTH_FRACTION <
                    HeroLayout.maxBottomBlockWidthDp(diameter),
            )
        }
    }

    /** The door is square, so the content circle caps it via its diagonal. */
    @Test
    fun theDoorFitsInsideTheContentCircle() {
        ROUND_SIZES.forEach { diameter ->
            val door = diameter * DOOR_WIDTH_FRACTION
            assertTrue(
                "$diameter dp: door $door exceeds " + HeroLayout.maxSquareSideDp(diameter),
                door <= HeroLayout.maxSquareSideDp(diameter),
            )
        }
    }

    /**
     * Distance from the screen centre to a bottom-anchored block's bottom
     * corner — the point that has to clear the ring.
     */
    private fun cornerDistance(
        diameter: Float,
        width: Float,
        insetDp: Float,
    ): Float {
        val halfWidth = width / 2f
        val verticalFromCentre = diameter / 2f - insetDp
        return sqrt(halfWidth * halfWidth + verticalFromCentre * verticalFromCentre)
    }

    private companion object {
        /** Small round (41mm-class) and large round (45mm-class), in dp. */
        val ROUND_SIZES = listOf(192f, 227f)
        const val OLD_FLAT_INSET_DP = 18f
        const val TOLERANCE = 0.01f

        /**
         * Mirrors the private constants in `HeroScreen.kt`. Duplicated on
         * purpose: the point of the test is that THESE shipped values satisfy
         * the rules, so reading them from production would only prove the
         * arithmetic is self-consistent.
         */
        const val BOTTOM_TEXT_WIDTH_FRACTION = 0.46f
        const val DOOR_WIDTH_FRACTION = 0.46f
    }
}
