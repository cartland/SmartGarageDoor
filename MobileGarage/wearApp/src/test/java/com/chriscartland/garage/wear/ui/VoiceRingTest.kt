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

import com.chriscartland.garage.domain.model.VoiceIntent
import com.chriscartland.garage.usecase.VoiceCommandIgnoreReason
import com.chriscartland.garage.usecase.VoiceCommandState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The demo's ring says the same things the real button's ring says.
 *
 * `HeroRingTest` pins the same rules for `RemoteButtonState`; this is the voice
 * half. Both matter for the same reason: the bloom is the app saying "that
 * worked", so the interesting assertions are all about which states are NOT
 * allowed to trigger it.
 */
class VoiceRingTest {
    /** The cancel window counts down exactly as a hold does. */
    @Test
    fun theCancelWindowSweeps() {
        assertEquals(
            RingPhase.Sweeping,
            VoiceRing.phaseFor(
                VoiceCommandState.Armed(
                    intent = VoiceIntent.OPEN,
                    transcript = "open the garage door",
                    windowMs = WearVoiceViewModel.ARMED_WINDOW_MILLIS,
                ),
            ),
        )
    }

    /**
     * Both halves of the commit bloom, and this is the one that is easy to get
     * wrong.
     *
     * `Sending` lasts 600ms in the simulated environment while the bloom takes
     * roughly 720ms, so keying the celebration off `Sending` alone would cut it
     * off mid-recede — a commit animation that visibly stutters at the exact
     * instant it is meant to land. They are one moment anyway: "would press the
     * remote" and "nothing was sent" are two beats of a single simulated
     * commit.
     */
    @Test
    fun bothBeatsOfTheCommitAreOneBloom() {
        assertEquals(
            RingPhase.Committed,
            VoiceRing.phaseFor(VoiceCommandState.Sending(intent = VoiceIntent.OPEN)),
        )
        assertEquals(
            RingPhase.Committed,
            VoiceRing.phaseFor(VoiceCommandState.Sent(intent = VoiceIntent.OPEN)),
        )
    }

    /**
     * A failed press must never bloom.
     *
     * The bloom means "that worked". `Failed` sits right beside `Sent` in the
     * sealed hierarchy and is the natural thing to sweep into the same branch,
     * which would have the demo celebrate a press that did not happen — the
     * mirror of the hero screen's rule that only a genuinely submitted press
     * may trigger it.
     */
    @Test
    fun aFailedPressIsNeverCelebrated() {
        assertEquals(
            RingPhase.Idle,
            VoiceRing.phaseFor(VoiceCommandState.Failed(intent = VoiceIntent.OPEN)),
        )
    }

    /** A refusal rewinds, which is what makes "nothing happened" legible. */
    @Test
    fun aRefusalUnwinds() {
        assertEquals(
            RingPhase.Idle,
            VoiceRing.phaseFor(
                VoiceCommandState.Ignored(
                    reason = VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN,
                    transcript = "open the garage door",
                    classification = null,
                    engineName = "Rules v3",
                ),
            ),
        )
    }

    /** Nothing is drawn before a command exists. */
    @Test
    fun restingAndListeningDrawNothing() {
        assertEquals(RingPhase.Idle, VoiceRing.phaseFor(VoiceCommandState.Ready))
        assertEquals(
            RingPhase.Idle,
            VoiceRing.phaseFor(VoiceCommandState.Listening(attempt = 1)),
        )
    }

    /**
     * The demo never claims something is outstanding.
     *
     * `Settling` exists for the hero screen's Channel race — two signals that
     * disagree for a frame — and the voice controller has no such gap: it moves
     * straight from the cancel window to a decided outcome. If this ever starts
     * failing, the controller grew an indeterminate state and the ring needs to
     * be taught what to do with it rather than defaulting to Idle.
     */
    @Test
    fun theDemoNeverSettles() {
        val states = listOf(
            VoiceCommandState.Ready,
            VoiceCommandState.Listening(attempt = 1),
            VoiceCommandState.Armed(
                intent = VoiceIntent.CLOSE,
                transcript = "close the garage door",
                windowMs = WearVoiceViewModel.ARMED_WINDOW_MILLIS,
            ),
            VoiceCommandState.Sending(intent = VoiceIntent.CLOSE),
            VoiceCommandState.Sent(intent = VoiceIntent.CLOSE),
            VoiceCommandState.Failed(intent = VoiceIntent.CLOSE),
        )
        assertTrue(states.none { VoiceRing.phaseFor(it) == RingPhase.Settling })
    }
}
