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

import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The two ways to press the real door must feel like one gesture.
 *
 * Holding the door and speaking to the mic both put a press [WearConfirmTiming]
 * away, both draw the same [ConfirmRing], and both are abandonable right up to
 * the moment it completes. This file pins the three things that make them ONE
 * interaction rather than two that resemble each other:
 *
 *  1. the same clock,
 *  2. the same buzz at each point of the journey,
 *  3. the same meaning for the ring completing.
 *
 * It is a parity test rather than a behaviour test on purpose. Behaviour tests
 * for each surface live beside that surface; nothing there would notice the two
 * drifting APART, which is the failure this is for — and which already happened
 * once (hold 2s, voice 3s, from 0.3.0 until 0.6.1).
 *
 * What is deliberately NOT asserted here is the input: the hold needs sustained
 * contact, voice runs on its own. That difference is the point of having two
 * surfaces, and `docs/WEAR_OS.md` § "One ring, two ways to start it" spells it
 * out.
 */
class WearConfirmParityTest {
    @Test
    fun bothCountdownsTakeTheSameTime() {
        assertEquals(
            "The hold's sweep and the voice cancel window are the same ring making the " +
                "same promise about the same door, so they must take the same time. " +
                "Derive both from WearConfirmTiming rather than writing the number twice.",
            WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS,
            WearVoiceViewModel.ARMED_WINDOW_MILLIS,
        )
    }

    @Test
    fun bothMidpointsFallAtTheHalfway() {
        assertEquals(
            WearConfirmTiming.RING_JOURNEY_MILLIS / 2,
            WearConfirmTiming.HALFWAY_MILLIS,
        )
        assertEquals(
            WearHomeViewModel.HOLD_HALFWAY_MILLIS,
            WearConfirmTiming.HALFWAY_MILLIS,
        )
    }

    /**
     * Each voice cue borrows the constant of the hold cue at the same point of
     * the journey. Asserted pairwise rather than by eyeballing `WearHaptics`,
     * because the mapping is a `when` over two unrelated enum groups and
     * nothing else would catch one of them being changed alone.
     */
    @Test
    fun everyVoiceCueFeelsLikeItsHoldCounterpart() {
        val pairs = listOf(
            "countdown starts" to (HapticCue.HoldEngaged to HapticCue.VoiceArmed),
            "midpoint" to (HapticCue.HoldHalfway to HapticCue.VoiceHalfway),
            "committed" to (HapticCue.PressCommitted to HapticCue.VoiceCommitted),
            "abandoned" to (HapticCue.HoldAborted to HapticCue.VoiceAborted),
            "did not take" to (HapticCue.PressFailed to HapticCue.VoiceRefused),
        )
        pairs.forEach { (moment, cues) ->
            val (hold, voice) = cues
            assertEquals(
                "At \"$moment\" the two surfaces must feel identical, but $hold and " +
                    "$voice map to different haptic constants.",
                WearHaptics.constantFor(hold),
                WearHaptics.constantFor(voice),
            )
        }
    }

    /**
     * A positive control for the test above.
     *
     * Every assertion there is an equality between two constants, so if
     * `constantFor` ever returned one value for everything — a plausible
     * refactoring slip — the whole parity suite would pass vacuously while the
     * watch buzzed identically for "committed" and "refused". These two must
     * differ; they are the pair a user most needs to tell apart without
     * looking.
     */
    @Test
    fun committedAndRefusedDoNotFeelTheSame() {
        assertNotEquals(
            WearHaptics.constantFor(HapticCue.VoiceCommitted),
            WearHaptics.constantFor(HapticCue.VoiceRefused),
        )
        assertNotEquals(
            WearHaptics.constantFor(HapticCue.VoiceAborted),
            WearHaptics.constantFor(HapticCue.VoiceCommitted),
        )
    }

    /**
     * The voice language is COMPLETE: every moment a user can produce has a
     * cue, and no two adjacent meanings share one.
     *
     * This is the property that matters on the go, where the screen is not
     * being looked at. The failure it guards is not "a wrong buzz" but
     * "silence" — a tap that registered nothing feels identical to a tap that
     * registered and did the wrong thing, and the user's only recourse is to
     * tap again, which on a live surface is exactly the wrong instinct.
     *
     * `VoiceListening` is the one with no hold counterpart, and deliberately:
     * the hold has no capture phase to acknowledge, because a finger IS the
     * input.
     */
    @Test
    fun everyVoiceMomentHasItsOwnFeel() {
        val language = mapOf(
            "microphone opened" to HapticCue.VoiceListening,
            "command understood, countdown running" to HapticCue.VoiceArmed,
            "halfway" to HapticCue.VoiceHalfway,
            "sent" to HapticCue.VoiceCommitted,
            "you stopped it" to HapticCue.VoiceAborted,
            "it did not take" to HapticCue.VoiceRefused,
        )
        val collisions = language.entries
            .groupBy { WearHaptics.constantFor(it.value) }
            .filterValues { it.size > 1 }
            .mapValues { (_, entries) -> entries.map { it.key } }
        assertEquals(
            "These voice moments feel identical, so a wrist cannot tell them apart: " +
                "$collisions",
            emptyMap<Int, List<String>>(),
            collisions,
        )
    }

    /** Abandoning is the same news on both surfaces, so it is the same buzz. */
    @Test
    fun abandoningEitherCountdownUsesTheGestureEndConstant() {
        assertEquals(HapticFeedbackConstants.GESTURE_END, WearHaptics.constantFor(HapticCue.HoldAborted))
        assertEquals(HapticFeedbackConstants.GESTURE_END, WearHaptics.constantFor(HapticCue.VoiceAborted))
    }
}
