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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When the voice surface is doing something, as a pure function.
 *
 * The exhaustiveness of [VoiceScreenWake.phaseOf] is the compiler's job — a
 * state added to `VoiceCommandState` fails the build here until someone says
 * what it means for the screen. What a test can add is the two properties the
 * compiler cannot see: that exactly one state is idle, and that the phase names
 * are actually distinct.
 */
class VoiceScreenWakeTest {
    private val everyState = listOf(
        VoiceCommandState.Ready,
        VoiceCommandState.Listening(attempt = 1),
        VoiceCommandState.Armed(
            intent = VoiceIntent.OPEN,
            transcript = "open the garage door",
            windowMs = WearVoiceViewModel.ARMED_WINDOW_MILLIS,
        ),
        VoiceCommandState.Sending(intent = VoiceIntent.OPEN),
        VoiceCommandState.Sent(intent = VoiceIntent.OPEN),
        VoiceCommandState.Failed(intent = VoiceIntent.OPEN),
        VoiceCommandState.Ignored(
            reason = VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN,
            transcript = "open the garage door",
            classification = null,
            engineName = "test",
        ),
    )

    /**
     * Resting is the only thing that is not happening.
     *
     * Stated as "every other state holds the screen" rather than by listing the
     * busy ones, because that is the direction the rule has to survive being
     * extended in: a state nobody thought about should keep the display alive
     * and cost a little battery, not sleep in the middle of an utterance.
     */
    @Test
    fun everyStateExceptRestingHoldsTheScreen() {
        everyState.forEach { state ->
            val phase = VoiceScreenWake.phaseOf(state, awaitingDoorReaction = false)
            if (state == VoiceCommandState.Ready) {
                assertNull("Resting with nothing outstanding must not hold the screen.", phase)
            } else {
                assertNotNull("$state is something happening, so it must hold the screen.", phase)
            }
        }
    }

    /**
     * A press whose door has not moved yet is still the interaction.
     *
     * The controller is finished the moment it reaches `Ready`, but the garage
     * door takes a second or two to start and the watch learns of it a poll
     * later. Releasing the screen at `Ready` would black out precisely the
     * stretch the user is waiting through.
     */
    @Test
    fun restingStillHoldsTheScreenWhileAPressWaitsOnTheDoor() {
        assertNotNull(
            VoiceScreenWake.phaseOf(VoiceCommandState.Ready, awaitingDoorReaction = true),
        )
    }

    /**
     * Positive control for the cap.
     *
     * The cap on how long the screen may be held is restarted whenever the
     * phase NAME changes, so if `phaseOf` returned one name for everything the
     * whole scheme would collapse to a single cap covering an entire command —
     * and nothing else in this file would notice, since every other assertion
     * here is satisfied by any non-null string.
     */
    @Test
    fun eachPhaseHasItsOwnName() {
        val names = everyState.mapNotNull { VoiceScreenWake.phaseOf(it, awaitingDoorReaction = false) }
        assertEquals(
            "Phases must be distinguishable or the per-phase cap silently becomes " +
                "one cap for the whole command: $names",
            names.size,
            names.toSet().size,
        )
    }

    /**
     * Cancelling and speaking again is a NEW phase, not a continuation of the
     * old one — otherwise a second attempt would inherit whatever was left of
     * the first attempt's cap.
     */
    @Test
    fun eachListeningAttemptIsItsOwnPhase() {
        val first = VoiceScreenWake.phaseOf(VoiceCommandState.Listening(attempt = 1), false)
        val second = VoiceScreenWake.phaseOf(VoiceCommandState.Listening(attempt = 2), false)
        assertNotNull(first)
        assertEquals(false, first == second)
    }

    /**
     * Two different refusals in a row are two phases, so the second gets its own
     * reading time rather than the remains of the first's.
     */
    @Test
    fun differentRefusalsAreDifferentPhases() {
        fun ignored(reason: VoiceCommandIgnoreReason) =
            VoiceScreenWake.phaseOf(
                VoiceCommandState.Ignored(
                    reason = reason,
                    transcript = null,
                    classification = null,
                    engineName = "test",
                ),
                awaitingDoorReaction = false,
            )
        assertEquals(
            false,
            ignored(VoiceCommandIgnoreReason.NO_SPEECH) ==
                ignored(VoiceCommandIgnoreReason.DOOR_MOVING),
        )
    }
}
