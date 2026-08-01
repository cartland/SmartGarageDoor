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

/**
 * The one confirm gesture this app has, in time.
 *
 * Two surfaces can put a press three seconds away: holding the door, and
 * speaking to the mic. They draw the SAME ring ([ConfirmRing]) and buzz the
 * SAME cues ([WearHaptics]), so they must also take the SAME time — a ring
 * that means "when I complete, the door moves" cannot take one duration on one
 * screen and another elsewhere without teaching the user that the ring is
 * decorative.
 *
 * Before 0.6.1 they disagreed: the hold swept in 2s and the voice cancel window
 * ran 3s, inherited from `VoiceCommandController.MAX_ARMED_WINDOW_MS` on the
 * reasoning that a glanced-at watch wants the most forgiving window available.
 * That reasoning was sound while voice was a simulation with nothing at stake.
 * Once voice began pressing the real button (0.6.0) the two became the same
 * promise about the same door, and one number is the only way to keep them one
 * promise.
 *
 * **What is deliberately NOT shared** is how the countdown is driven — see
 * `docs/WEAR_OS.md` § "One ring, two ways to start it". The hold requires
 * sustained contact and abandoning it means lifting a finger; voice runs on its
 * own and abandoning it means tapping. Same ring, same clock, same buzzes,
 * opposite input.
 */
internal object WearConfirmTiming {
    /**
     * How long a confirm ring takes to travel from empty to full — and
     * therefore how long there is to call the press off.
     *
     * Two seconds is the hold's original value, kept because it is the one
     * tuned against a finger: long enough that a brush cannot complete it,
     * short enough not to feel like a punishment for meaning it.
     */
    const val RING_JOURNEY_MILLIS: Long = 2_000L

    /**
     * When the midpoint haptic fires. A pacing cue ("about one more second"),
     * deliberately NOT a point of no return — cancelling works right up to the
     * end on both surfaces, which is a safety property worth more than tidier
     * haptic semantics.
     */
    const val HALFWAY_MILLIS: Long = RING_JOURNEY_MILLIS / 2

    /**
     * Gap between the two beats of the commit buzz. Long enough to be felt as
     * two events rather than one smeared one, short enough to still be the
     * same event — and it lands under the screen's commit bloom.
     *
     * The commit is the one irreversible moment either gesture has, and wrist
     * actuators are too coarse to convey "harder", so emphasis is expressed as
     * *again*.
     */
    const val COMMIT_BEAT_GAP_MILLIS: Long = 110L
}
