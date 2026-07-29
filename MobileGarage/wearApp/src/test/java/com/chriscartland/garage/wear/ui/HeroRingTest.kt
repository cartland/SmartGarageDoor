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

import com.chriscartland.garage.domain.model.RemoteButtonState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The hold ring's phase rules.
 *
 * These matter more than they look. The ring is the only channel that reports
 * whether a press happened, and its commit animation is emphatic — a
 * full-screen bloom. Showing that for a gesture that sent nothing would be a
 * lie told loudly, so "which phase" is worth pinning as pure logic rather than
 * leaving to a Composable no command-line test can reach.
 */
class HeroRingTest {
    @Test
    fun aFingerOnTheDoorSweeps() {
        assertEquals(
            RingPhase.Sweeping,
            HeroRing.phaseFor(isHolding = true, buttonState = RemoteButtonState.Preparing),
        )
        assertEquals(
            RingPhase.Sweeping,
            HeroRing.phaseFor(
                isHolding = true,
                buttonState = RemoteButtonState.AwaitingConfirmation,
            ),
        )
    }

    /**
     * The bloom is reachable ONLY from the two states a press actually
     * reaches by calling the server. Everything else that a finished gesture
     * can be sitting in must not celebrate.
     */
    @Test
    fun onlyARealSubmissionCommits() {
        assertEquals(
            RingPhase.Committed,
            HeroRing.phaseFor(isHolding = false, buttonState = RemoteButtonState.SendingToServer),
        )
        assertEquals(
            RingPhase.Committed,
            HeroRing.phaseFor(isHolding = false, buttonState = RemoteButtonState.SendingToDoor),
        )
        val neverCommits = listOf(
            RemoteButtonState.Ready,
            RemoteButtonState.Preparing,
            RemoteButtonState.AwaitingConfirmation,
            RemoteButtonState.Cancelled,
            RemoteButtonState.Succeeded,
            RemoteButtonState.ServerFailed,
            RemoteButtonState.DoorFailed,
        )
        neverCommits.forEach { state ->
            val phase = HeroRing.phaseFor(isHolding = false, buttonState = state)
            assertEquals("$state must not bloom", false, phase == RingPhase.Committed)
        }
    }

    /**
     * The ambiguous frame. `onTap` and `reset` both post to a Channel, so
     * immediately after the finger leaves, `AwaitingConfirmation` is exactly
     * as consistent with a completed hold as with an abandoned one. Neither
     * answer may be drawn yet: unwinding would flinch on a press that
     * succeeded, blooming would claim one that never happened.
     */
    @Test
    fun theAmbiguousFrameHoldsStillInsteadOfGuessing() {
        assertEquals(
            RingPhase.Settling,
            HeroRing.phaseFor(
                isHolding = false,
                buttonState = RemoteButtonState.AwaitingConfirmation,
            ),
        )
    }

    /** An abandoned hold lands back on Ready, which is where the unwind lives. */
    @Test
    fun anAbandonedHoldUnwinds() {
        assertEquals(
            RingPhase.Idle,
            HeroRing.phaseFor(isHolding = false, buttonState = RemoteButtonState.Ready),
        )
    }

    /** After the door answers, the ring gives its sweep back rather than vanishing. */
    @Test
    fun aFinishedPressUnwinds() {
        listOf(
            RemoteButtonState.Succeeded,
            RemoteButtonState.ServerFailed,
            RemoteButtonState.DoorFailed,
        ).forEach { state ->
            assertEquals(
                "$state should return the ring to rest",
                RingPhase.Idle,
                HeroRing.phaseFor(isHolding = false, buttonState = state),
            )
        }
    }
}
