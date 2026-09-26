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
import com.chriscartland.garage.presentation.CheckInAge
import com.chriscartland.garage.presentation.CheckInStatus
import com.chriscartland.garage.presentation.CheckInStatusMapper
import com.chriscartland.garage.presentation.DoorHeadlineMapper
import com.chriscartland.garage.presentation.GlanceStatusMapper
import com.chriscartland.garage.presentation.StatusHeadline
import com.chriscartland.garage.wear.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The complication's words. Resource ids, so none of this needs a `Context`.
 *
 * The seven-character budget is checked separately in
 * [GarageComplicationLengthTest], which reads the real strings.
 */
class GarageComplicationWordsTest {
    private fun glance(
        doorPosition: DoorPosition? = DoorPosition.OPEN,
        lastCheckInEpochSeconds: Long? = NOW - 120,
        isFetchError: Boolean = false,
    ) = GlanceStatusMapper.forGlance(
        doorPosition = doorPosition,
        lastCheckInEpochSeconds = lastCheckInEpochSeconds,
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
        // The ordinary case: the door is what you want to read, and the age
        // is the small print confirming it.
        assertTrue(!GarageComplicationWords.ageLeads(glance()))
    }

    @Test
    fun aReadingWeCannotVouchForLeadsWithTheAge() {
        // THE decision of this file. A complication cannot be greyed — the
        // watch face owns the colours — so the only place doubt can live is
        // the words. Many faces render the main text alone, and on those
        // "Open" would be an unqualified claim about a six-hour-old reading.
        val stale = glance(lastCheckInEpochSeconds = NOW - CheckInStatusMapper.STALE_THRESHOLD_SECONDS - 1)
        assertTrue(GarageComplicationWords.ageLeads(stale))
    }

    @Test
    fun aFailedRefreshAlsoDemotesTheDoor() {
        // The other way to lose confidence: the garage is reporting fine, but
        // we could not reach the server to confirm it is still the newest.
        assertTrue(GarageComplicationWords.ageLeads(glance(isFetchError = true)))
    }

    @Test
    fun theEmphasisRuleCanActuallyGoBothWays() {
        // Positive control: an `ageLeads` stuck on one answer would satisfy
        // every test above or every test below, and the complication would be
        // permanently wrong in one direction.
        assertNotEquals(
            GarageComplicationWords.ageLeads(glance()),
            GarageComplicationWords.ageLeads(glance(isFetchError = true)),
        )
    }

    @Test
    fun anUnstatableAgeFallsBackRatherThanGuessing() {
        // No timestamp at all, and a reading so old the number would overflow
        // seven characters. Both say "Stale", which is true and always fits.
        assertEquals(R.string.complication_stale, GarageComplicationWords.leadingAge(CheckInStatus.NoData).resId)
        assertEquals(
            R.string.complication_stale,
            GarageComplicationWords
                .leadingAge(
                    CheckInStatus.Reported(CheckInAge.Days(1_000), isStale = true),
                ).resId,
        )
    }

    @Test
    fun aRecentButUnconfirmedReadingSaysStaleRatherThanNow() {
        // Reachable when a fetch fails seconds after a good reading. Leading
        // with "now" would be the most misleading thing on offer: the reading
        // is recent, but we could not confirm it is still true.
        assertEquals(
            R.string.complication_stale,
            GarageComplicationWords.leadingAge(CheckInStatus.Reported(CheckInAge.JustNow, isStale = false)).resId,
        )
    }

    @Test
    fun ninetyNineDaysStillFitsButAHundredDoesNot() {
        // The boundary the fallback is drawn at.
        assertEquals(
            R.string.complication_age_days_ago,
            GarageComplicationWords.leadingAge(CheckInStatus.Reported(CheckInAge.Days(99), isStale = true)).resId,
        )
        assertEquals(
            R.string.complication_stale,
            GarageComplicationWords.leadingAge(CheckInStatus.Reported(CheckInAge.Days(100), isStale = true)).resId,
        )
    }

    @Test
    fun anAbsentAgeMeansNoTitleRatherThanAGuess() {
        // The title is simply omitted. Inventing "now" would be guessing
        // about exactly the thing this surface exists to be honest about.
        assertNull(GarageComplicationWords.shortAge(CheckInStatus.NoData))
        assertNull(GarageComplicationWords.longAge(CheckInStatus.NoData))
    }

    @Test
    fun theLongFormReusesTheTileWording() {
        // Both glance surfaces describe the same age the same way. Asserting
        // the resource identity keeps that true through a rename.
        assertEquals(
            R.string.glance_age_hours,
            GarageComplicationWords.longAge(CheckInStatus.Reported(CheckInAge.Hours(6, 0), isStale = true))?.resId,
        )
    }

    @Test
    fun eachShortAgeBucketGetsItsOwnWording() {
        val ids = listOf(
            CheckInAge.Minutes(5, 0),
            CheckInAge.Hours(6, 0),
            CheckInAge.Days(3),
        ).mapNotNull { GarageComplicationWords.shortAge(CheckInStatus.Reported(it, isStale = false))?.resId }
        assertEquals("minutes, hours and days must not share wording", 3, ids.toSet().size)
    }

    private companion object {
        const val NOW = 1_700_000_000L
    }
}
