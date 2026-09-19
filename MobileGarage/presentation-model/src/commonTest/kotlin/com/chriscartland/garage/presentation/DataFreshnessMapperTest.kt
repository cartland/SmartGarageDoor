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

/**
 * The three-way verdict every platform renders. Two properties matter more
 * than any single row of the table:
 *
 *  1. The settle window can only ever make the app QUIETER, never wronger —
 *     it changes how loudly a doubt is expressed, never whether the doubt
 *     exists.
 *  2. Indicators ACCUMULATE: everything muted in SETTLING is still muted in
 *     STALE, which is why a screen never gets more colourful as the news gets
 *     worse.
 */
class DataFreshnessMapperTest {
    private fun freshness(
        hasData: Boolean = true,
        stale: Boolean = false,
        error: Boolean = false,
        settling: Boolean = false,
    ): DataFreshness =
        DataFreshnessMapper.freshness(
            hasData = hasData,
            isCheckInStale = stale,
            isFetchError = error,
            isSettling = settling,
        )

    @Test
    fun currentDataIsFresh() {
        assertEquals(DataFreshness.FRESH, freshness())
    }

    /**
     * The settle window does NOT make unknown data look current. A screen
     * with nothing on it is not fresh just because it only just opened —
     * that would be the same lie in the other direction.
     */
    @Test
    fun settlingDoesNotMakeCurrentDataAnyLessFresh() {
        assertEquals(DataFreshness.FRESH, freshness(settling = true))
    }

    @Test
    fun noDataYetIsSettlingInsideTheWindowAndStaleOutside() {
        assertEquals(DataFreshness.SETTLING, freshness(hasData = false, settling = true))
        assertEquals(DataFreshness.STALE, freshness(hasData = false, settling = false))
    }

    @Test
    fun anAgedCheckInIsSettlingInsideTheWindowAndStaleOutside() {
        assertEquals(DataFreshness.SETTLING, freshness(stale = true, settling = true))
        assertEquals(DataFreshness.STALE, freshness(stale = true, settling = false))
    }

    @Test
    fun aFailedFetchIsSettlingInsideTheWindowAndStaleOutside() {
        assertEquals(DataFreshness.SETTLING, freshness(error = true, settling = true))
        assertEquals(DataFreshness.STALE, freshness(error = true, settling = false))
    }

    /**
     * Property 1, stated directly: for every combination of the three inputs,
     * opening the window never turns a non-FRESH verdict into FRESH, and
     * never turns a FRESH one into something else. All it can do is downgrade
     * STALE to SETTLING.
     */
    @Test
    fun theWindowOnlyEverySoftensTheVerdictItNeverChangesTheFacts() {
        val flags = listOf(true, false)
        for (hasData in flags) {
            for (stale in flags) {
                for (error in flags) {
                    val settled = freshness(hasData, stale, error, settling = false)
                    val settling = freshness(hasData, stale, error, settling = true)
                    if (settled == DataFreshness.FRESH) {
                        assertEquals(
                            DataFreshness.FRESH,
                            settling,
                            "hasData=$hasData stale=$stale error=$error",
                        )
                    } else {
                        assertEquals(
                            DataFreshness.SETTLING,
                            settling,
                            "hasData=$hasData stale=$stale error=$error",
                        )
                    }
                }
            }
        }
    }

    /**
     * Property 2: indicators accumulate. STALE keeps everything SETTLING
     * shows and adds words to it.
     *
     * The six assertions below ARE the discriminating ones — each predicate is
     * pinned at all three verdicts, so a `isMuted`/`isSpoken` that degenerated
     * to a constant fails here immediately.
     */
    @Test
    fun indicatorsOnlyAccumulate() {
        assertEquals(false, DataFreshnessMapper.isMuted(DataFreshness.FRESH))
        assertEquals(true, DataFreshnessMapper.isMuted(DataFreshness.SETTLING))
        assertEquals(true, DataFreshnessMapper.isMuted(DataFreshness.STALE))

        assertEquals(false, DataFreshnessMapper.isSpoken(DataFreshness.FRESH))
        assertEquals(false, DataFreshnessMapper.isSpoken(DataFreshness.SETTLING))
        assertEquals(true, DataFreshnessMapper.isSpoken(DataFreshness.STALE))
    }

    /**
     * The file's positive control: [DataFreshnessMapper.freshness] must be
     * able to return more than one thing.
     *
     * Every other assertion in this file states that some input produces some
     * verdict, and a `freshness` that had degenerated to returning a single
     * constant would still satisfy a surprising number of them. This one
     * cannot pass unless the function genuinely discriminates — on the settle
     * flag alone, and on the data alone.
     *
     * It replaces an earlier attempt that compared two enum ENTRIES
     * (`assertTrue(SETTLING != STALE)`), which was a tautology: two distinct
     * enum constants can never be equal, so it could not fail under any
     * mutation of the code under test and never called `freshness` at all.
     * Exactly the vacuous-pass family CLAUDE.md warns about, sitting under a
     * comment claiming to be the guard against it.
     */
    @Test
    fun theMapperCanActuallyReturnMoreThanOneVerdict() {
        assertTrue(
            freshness(stale = true, settling = true) != freshness(stale = true, settling = false),
            "the settle flag alone must change the verdict",
        )
        assertTrue(
            freshness(stale = false) != freshness(stale = true),
            "the data alone must change the verdict",
        )
    }

    /** The enum's sugar must agree with the mapper it delegates to. */
    @Test
    fun theEnumPropertiesAgreeWithTheMapperFunctions() {
        for (value in DataFreshness.entries) {
            assertEquals(DataFreshnessMapper.isMuted(value), value.isMuted, "isMuted for $value")
            assertEquals(DataFreshnessMapper.isSpoken(value), value.isSpoken, "isSpoken for $value")
        }
    }

    /**
     * The dim is shared so the three apps cannot drift on how muted a muted
     * door looks, and it must be a real dim — a 1.0 here would make SETTLING
     * silently invisible as a state.
     */
    @Test
    fun theMutedAlphaIsSharedAndActuallyDims() {
        assertEquals(1f, FreshnessTint.alphaFor(DataFreshness.FRESH))
        assertEquals(FreshnessTint.MUTED_ALPHA, FreshnessTint.alphaFor(DataFreshness.SETTLING))
        assertEquals(FreshnessTint.MUTED_ALPHA, FreshnessTint.alphaFor(DataFreshness.STALE))
        assertTrue(FreshnessTint.MUTED_ALPHA < 1f, "a muted hero must actually be dimmer")
        assertTrue(FreshnessTint.MUTED_ALPHA > 0f, "a muted hero must still be readable")
    }

    /**
     * Grey means grey: equal channels out. Rec. 709 weights also mean the
     * result tracks perceived lightness, so the door's green and red do not
     * collapse to wildly different greys — pinned here by checking that a
     * saturated red and a saturated green, which a channel average would send
     * to the same value, do not.
     */
    @Test
    fun lumaPreservesPerceivedLightnessRatherThanAveragingChannels() {
        val red = FreshnessTint.luma(1f, 0f, 0f)
        val green = FreshnessTint.luma(0f, 1f, 0f)
        assertTrue(green > red, "green reads lighter than red; a flat average would tie them")
        assertEquals(0f, FreshnessTint.luma(0f, 0f, 0f))
        assertEquals(1f, FreshnessTint.luma(1f, 1f, 1f))
    }
}
