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

import com.chriscartland.garage.domain.model.DoorColorState
import com.chriscartland.garage.presentation.DataFreshness
import com.chriscartland.garage.presentation.DoorHeadline
import com.chriscartland.garage.presentation.GlanceStatus
import com.chriscartland.garage.presentation.Liveness
import com.chriscartland.garage.presentation.StatusHeadline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Which second line the widget shows, and when it shows none. */
class GarageWidgetSublineTest {
    private fun status(
        freshness: DataFreshness,
        liveness: Liveness,
        since: Long?,
    ) = GlanceStatus(
        headline = StatusHeadline.Door(DoorHeadline.CLOSED),
        freshness = freshness,
        liveness = liveness,
        stateSinceEpochSeconds = since,
        colorState = DoorColorState.CLOSED,
    )

    @Test
    fun aConfirmedDoorShowsTheInstantItChanged() {
        val subline = GarageWidgetSubline.forStatus(
            status(DataFreshness.FRESH, Liveness.LIVE, since = 1_700_000_000L),
        )
        assertEquals(WidgetSubline.Since(1_700_000_000L), subline)
    }

    @Test
    fun aDoorWeCannotVouchForSaysStaleInsteadOfASpan() {
        // GlanceStatus has already withheld the instant; the widget must not
        // fill the gap with something reassuring.
        val subline = GarageWidgetSubline.forStatus(
            status(DataFreshness.STALE, Liveness.STALE, since = null),
        )
        assertEquals(WidgetSubline.Stale, subline)
    }

    @Test
    fun nothingKnownAddsNoSecondLine() {
        // The headline already says "No signal". A second line would be noise
        // on a surface with two lines to spend.
        val subline = GarageWidgetSubline.forStatus(
            status(DataFreshness.FRESH, Liveness.LIVE, since = null),
        )
        assertEquals(WidgetSubline.Silent, subline)
    }

    @Test
    fun theThreeOutcomesAreActuallyDifferent() {
        // Positive control. The three assertions above are equality checks, so a
        // forStatus that returned one constant for everything would fail them
        // individually — but a future refactor that collapsed two cases into one
        // would not be caught by any single assertion. This pins that all three
        // remain distinguishable.
        val live = GarageWidgetSubline.forStatus(status(DataFreshness.FRESH, Liveness.LIVE, 1L))
        val stale = GarageWidgetSubline.forStatus(status(DataFreshness.STALE, Liveness.STALE, null))
        val silent = GarageWidgetSubline.forStatus(status(DataFreshness.FRESH, Liveness.LIVE, null))
        assertNotEquals(live, stale)
        assertNotEquals(stale, silent)
        assertNotEquals(live, silent)
    }

    @Test
    fun anInstantTheMapperAllowedIsShownEvenIfLivenessLooksStale() {
        // Deliberate: the mapper is the single authority on whether the instant
        // may be presented, and re-deciding here would be a second copy of the
        // rule. If this combination ever occurs, following the instant is the
        // behaviour that matches GlanceStatus's contract rather than second-
        // guessing it.
        val subline = GarageWidgetSubline.forStatus(
            status(DataFreshness.STALE, Liveness.STALE, since = 42L),
        )
        assertEquals(WidgetSubline.Since(42L), subline)
    }
}
