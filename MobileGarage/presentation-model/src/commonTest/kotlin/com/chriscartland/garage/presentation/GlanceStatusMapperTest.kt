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

import com.chriscartland.garage.domain.model.DoorPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlanceStatusMapperTest {
    private fun glance(
        doorPosition: DoorPosition? = DoorPosition.OPEN,
        lastCheckInEpochSeconds: Long? = NOW - 30,
        nowEpochSeconds: Long = NOW,
        isFetchError: Boolean = false,
    ) = GlanceStatusMapper.forGlance(
        doorPosition = doorPosition,
        lastCheckInEpochSeconds = lastCheckInEpochSeconds,
        nowEpochSeconds = nowEpochSeconds,
        isFetchError = isFetchError,
    )

    @Test
    fun aRecentlyConfirmedDoorIsPresentedConfidently() {
        val status = glance()
        assertEquals(DataFreshness.FRESH, status.freshness)
        assertEquals(StatusHeadline.Door(DoorHeadline.OPEN), status.headline)
        assertTrue(!status.freshness.isMuted, "a confirmed door must not be muted")
    }

    @Test
    fun aGarageThatStoppedReportingIsMutedAndSpoken() {
        // The case a glance surface exists to get right: the app is not
        // running, nothing has failed, and the stored position is simply too
        // old to assert.
        val status = glance(
            lastCheckInEpochSeconds = NOW - CheckInStatusMapper.STALE_THRESHOLD_SECONDS - 1,
        )
        assertEquals(DataFreshness.STALE, status.freshness)
        assertTrue(status.freshness.isMuted)
        assertTrue(status.freshness.isSpoken)
    }

    @Test
    fun aKnownDoorIsStillNamedWhenTheVerdictIsSpoken() {
        // Mirrors the screens' rule: going quiet must not throw away the last
        // thing we actually know, at exactly the moment it matters most.
        // Without this the tile would replace "Open" with "No signal" and the
        // user would lose the one fact worth glancing for.
        val status = glance(
            doorPosition = DoorPosition.OPEN,
            lastCheckInEpochSeconds = NOW - CheckInStatusMapper.STALE_THRESHOLD_SECONDS - 1,
        )
        assertEquals(StatusHeadline.Door(DoorHeadline.OPEN), status.headline)
    }

    @Test
    fun anEmptyCacheEscalatesToNoSignal() {
        // Nothing known AND nothing settling: a glance says so immediately
        // rather than claiming to be connecting.
        val status = glance(doorPosition = null, lastCheckInEpochSeconds = null)
        assertEquals(StatusHeadline.NoSignal, status.headline)
    }

    @Test
    fun aGlanceNeverSaysConnecting() {
        // The settle-window decision, asserted at the only place it is
        // observable. `Connecting…` is what a SCREEN says while it is
        // arriving and about to hear back; a tile is asked once and whatever
        // it returns is what the user reads and swipes away from. If this
        // ever fails, someone has threaded `isSettling` back in and the tile
        // will render a grey door with no explanation.
        val everyInput = listOf(
            glance(doorPosition = null, lastCheckInEpochSeconds = null),
            glance(doorPosition = null, lastCheckInEpochSeconds = null, isFetchError = true),
            glance(doorPosition = DoorPosition.CLOSED),
            glance(doorPosition = DoorPosition.CLOSED, isFetchError = true),
            glance(lastCheckInEpochSeconds = NOW - CheckInStatusMapper.STALE_THRESHOLD_SECONDS - 1),
        )
        everyInput.forEach { status ->
            assertTrue(
                status.headline != StatusHeadline.Connecting,
                "a glance must never render the arriving state: $status",
            )
        }
    }

    @Test
    fun aFailedRefreshMutesEvenWhenTheGarageIsReportingRecently() {
        // The two staleness inputs are independent. Here the stored check-in
        // is recent, but we could not reach the server to confirm it is still
        // the newest — so the value on screen is remembered, not confirmed.
        val status = glance(isFetchError = true)
        assertEquals(DataFreshness.STALE, status.freshness)
    }

    @Test
    fun theAgeIsCarriedSoTheSurfaceCanSayHowOld() {
        // The reliability affordance: a glance that shows a position without
        // its age is asking to be trusted on nothing.
        val status = glance(lastCheckInEpochSeconds = NOW - 125)
        assertEquals(CheckInStatus.Reported(CheckInAge.Minutes(minutes = 2, seconds = 5), isStale = false), status.age)
    }

    @Test
    fun anUnknownCheckInTimeIsReportedAsNoDataRatherThanStale() {
        // Unjudgeable, not old. The surface renders the position without an
        // age line instead of implying the garage has gone quiet.
        val status = glance(lastCheckInEpochSeconds = null)
        assertEquals(CheckInStatus.NoData, status.age)
        assertEquals(DataFreshness.FRESH, status.freshness)
    }

    @Test
    fun theMapperCanActuallyReturnMoreThanOneVerdict() {
        // Positive control for the whole file. Every test above asserts an
        // equality, so a `forGlance` that collapsed to one constant would
        // satisfy whichever tests happened to match it and the suite would go
        // green with the tile permanently stuck. Proving two inputs produce
        // two different verdicts AND two different headlines is what stops
        // that.
        val confident = glance(doorPosition = DoorPosition.CLOSED)
        val silent = glance(doorPosition = null, lastCheckInEpochSeconds = null)
        assertTrue(confident.freshness != silent.freshness, "freshness never varies")
        assertTrue(confident.headline != silent.headline, "headline never varies")
    }

    private companion object {
        const val NOW = 1_700_000_000L
    }
}
