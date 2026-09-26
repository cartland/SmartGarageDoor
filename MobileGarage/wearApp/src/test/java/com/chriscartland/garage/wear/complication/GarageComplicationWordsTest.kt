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

package com.chriscartland.garage.wear.complication

import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.presentation.CheckInStatusMapper
import com.chriscartland.garage.presentation.DoorHeadlineMapper
import com.chriscartland.garage.presentation.GlanceStatusMapper
import com.chriscartland.garage.presentation.StatusHeadline
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The complication's words. Resource ids, so none of this needs a `Context`.
 *
 * Note what is NOT tested here any more: there are no age-formatting cases,
 * because the complication no longer formats a number at all. The one figure
 * it shows is a `TimeDifferenceComplicationText` rendered by the watch face.
 */
class GarageComplicationWordsTest {
    private fun glance(
        doorPosition: DoorPosition? = DoorPosition.OPEN,
        lastCheckInEpochSeconds: Long? = NOW - 120,
        lastChangeEpochSeconds: Long? = NOW - 480,
        isFetchError: Boolean = false,
    ) = GlanceStatusMapper.forGlance(
        doorPosition = doorPosition,
        lastCheckInEpochSeconds = lastCheckInEpochSeconds,
        lastChangeEpochSeconds = lastChangeEpochSeconds,
        nowEpochSeconds = NOW,
        isFetchError = isFetchError,
    )

    @Test
    fun everyDoorPositionHasAShortWord() {
        // A position with no word would render nothing at all on the face.
        // Sweeping the enum covers a position added later.
        DoorPosition.entries.forEach { position ->
            val headline = StatusHeadline.Door(DoorHeadlineMapper.forPosition(position))
            assertNotEquals("no complication word for $position", null, GarageComplicationWords.doorWord(headline))
        }
    }

    @Test
    fun thereIsNoDoorWordWhenNothingIsKnown() {
        // "Connecting" and "No signal" are not doors. The service renders
        // "No data" rather than inventing one.
        assertNull(GarageComplicationWords.doorWord(StatusHeadline.NoSignal))
        assertNull(GarageComplicationWords.doorWord(StatusHeadline.Connecting))
    }

    @Test
    fun aConfirmedDoorLeadsWithTheDoor() {
        // The ordinary case: the door is what you want to read, and the
        // running duration beside it is the proof that it is current.
        assertFalse(GarageComplicationWords.staleLeads(glance()))
    }

    @Test
    fun aReadingWeCannotVouchForLeadsWithTheWordStale() {
        // A complication cannot be greyed — the watch face owns the colours —
        // so the only place doubt can live is the words. Many faces render
        // the main text alone, and there "Open" would be an unqualified claim
        // about a door we have lost contact with.
        val stale = glance(lastCheckInEpochSeconds = NOW - CheckInStatusMapper.STALE_THRESHOLD_SECONDS - 1)
        assertTrue(GarageComplicationWords.staleLeads(stale))
    }

    @Test
    fun aFailedRefreshAlsoLeadsWithStale() {
        assertTrue(GarageComplicationWords.staleLeads(glance(isFetchError = true)))
    }

    @Test
    fun theEmphasisRuleCanActuallyGoBothWays() {
        // Positive control: a `staleLeads` stuck on one answer would satisfy
        // every test above or every test below, and the complication would be
        // permanently wrong in one direction.
        assertNotEquals(
            GarageComplicationWords.staleLeads(glance()),
            GarageComplicationWords.staleLeads(glance(isFetchError = true)),
        )
    }

    @Test
    fun aStaleReadingHasNoInstantToCountFrom() {
        // The service asks the shared status for the instant; when it is
        // withheld there is nothing to hand the watch face, which is what
        // stops a running duration appearing beside a door we cannot vouch
        // for. Asserted here because it is the precondition `staleLeads`
        // depends on.
        val stale = glance(lastCheckInEpochSeconds = NOW - CheckInStatusMapper.STALE_THRESHOLD_SECONDS - 1)
        assertNull(stale.stateSinceEpochSeconds)
    }

    @Test
    fun aLiveReadingCountsFromWhenTheDoorChanged() {
        // Not from when we last checked. Those differ here by design: the
        // check-in is two minutes old, the door changed eight minutes ago,
        // and eight is the number worth showing.
        assertNotEquals(null, glance().stateSinceEpochSeconds)
        assertNotEquals(NOW - 120, glance().stateSinceEpochSeconds)
    }

    private companion object {
        const val NOW = 1_700_000_000L
    }
}
