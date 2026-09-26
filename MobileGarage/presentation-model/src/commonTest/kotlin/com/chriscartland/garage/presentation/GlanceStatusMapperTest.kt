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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlanceStatusMapperTest {
    private fun glance(
        doorPosition: DoorPosition? = DoorPosition.OPEN,
        lastCheckInEpochSeconds: Long? = NOW - 30,
        lastChangeEpochSeconds: Long? = NOW - 480,
        nowEpochSeconds: Long = NOW,
        isFetchError: Boolean = false,
    ) = GlanceStatusMapper.forGlance(
        doorPosition = doorPosition,
        lastCheckInEpochSeconds = lastCheckInEpochSeconds,
        lastChangeEpochSeconds = lastChangeEpochSeconds,
        nowEpochSeconds = nowEpochSeconds,
        isFetchError = isFetchError,
    )

    @Test
    fun aRecentlyConfirmedDoorIsPresentedConfidently() {
        val status = glance()
        assertEquals(DataFreshness.FRESH, status.freshness)
        assertEquals(Liveness.LIVE, status.liveness)
        assertEquals(StatusHeadline.Door(DoorHeadline.OPEN), status.headline)
        assertTrue(!status.freshness.isMuted, "a confirmed door must not be muted")
    }

    @Test
    fun theInstantOfferedIsWhenTheDOORChangedNotWhenWeLastChecked() {
        // The whole point of the rework. "Open for 8 minutes" is a fact about
        // the garage; "we checked 30 seconds ago" is a fact about our
        // plumbing, and only one of them is worth a glance's one line.
        val status = glance(lastCheckInEpochSeconds = NOW - 30, lastChangeEpochSeconds = NOW - 480)
        assertEquals(NOW - 480, status.stateSinceEpochSeconds)
    }

    @Test
    fun aGarageThatStoppedReportingIsStaleAndOffersNoDuration() {
        // The case a glance surface exists to get right: nothing has failed,
        // the stored position is simply too old to assert. A duration would
        // claim the door has been that way CONTINUOUSLY, and a door we have
        // lost contact with may have moved twice since — so it is withheld
        // rather than shown alongside a warning.
        val status = glance(
            lastCheckInEpochSeconds = NOW - CheckInStatusMapper.STALE_THRESHOLD_SECONDS - 1,
        )
        assertEquals(Liveness.STALE, status.liveness)
        assertEquals(DataFreshness.STALE, status.freshness)
        assertNull(status.stateSinceEpochSeconds, "a door we cannot vouch for must not be given a duration")
    }

    @Test
    fun aFailedRefreshAlsoWithholdsTheDuration() {
        // The other way to lose confidence: the garage is reporting fine, but
        // we could not reach the server to confirm it is still the newest.
        val status = glance(isFetchError = true)
        assertEquals(Liveness.STALE, status.liveness)
        assertNull(status.stateSinceEpochSeconds)
    }

    @Test
    fun aKnownDoorIsStillNamedWhenTheVerdictIsStale() {
        // Losing the duration must not lose the door. Going quiet does not
        // erase the last thing we actually know.
        val status = glance(
            doorPosition = DoorPosition.OPEN,
            lastCheckInEpochSeconds = NOW - CheckInStatusMapper.STALE_THRESHOLD_SECONDS - 1,
        )
        assertEquals(StatusHeadline.Door(DoorHeadline.OPEN), status.headline)
    }

    @Test
    fun anEmptyCacheEscalatesToNoSignal() {
        val status = glance(doorPosition = null, lastCheckInEpochSeconds = null, lastChangeEpochSeconds = null)
        assertEquals(StatusHeadline.NoSignal, status.headline)
        assertNull(status.stateSinceEpochSeconds)
    }

    @Test
    fun aGlanceNeverSaysConnecting() {
        // The settle-window decision, asserted where it is observable.
        // `Connecting…` is what a SCREEN says while it is arriving and about
        // to hear back; a glance is asked once and whatever it returns is
        // what gets read and swiped away from.
        val everyInput = listOf(
            glance(doorPosition = null, lastCheckInEpochSeconds = null, lastChangeEpochSeconds = null),
            glance(doorPosition = null, lastCheckInEpochSeconds = null, lastChangeEpochSeconds = null, isFetchError = true),
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
    fun livenessIsTwoStatesAndNothingElse() {
        // Deliberately coarse. A glance redraws on the system's schedule, so
        // any precise figure about its own currency has probably drifted by
        // the time it is read; a two-state verdict can only be wrong by the
        // width of one update.
        assertEquals(2, Liveness.entries.size)
    }

    @Test
    fun theMapperCanActuallyReturnMoreThanOneVerdict() {
        // Positive control for the whole file. Every test above asserts an
        // equality, so a `forGlance` that collapsed to one constant would
        // satisfy whichever tests matched it and the suite would go green
        // with the surface permanently stuck.
        val confident = glance(doorPosition = DoorPosition.CLOSED)
        val silent = glance(doorPosition = null, lastCheckInEpochSeconds = null, lastChangeEpochSeconds = null)
        assertTrue(confident.freshness != silent.freshness, "freshness never varies")
        assertTrue(confident.headline != silent.headline, "headline never varies")
        assertTrue(confident.liveness != silent.liveness, "liveness never varies")
    }

    private companion object {
        const val NOW = 1_700_000_000L
    }
}
