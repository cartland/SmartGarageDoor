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
 * A one-shot haptic moment: the hold-to-press gesture on the hero screen, or
 * the simulated voice command loop.
 *
 * These are *decisions*, made by [WearHomeViewModel] and emitted as events;
 * the UI performs the irreducible platform write (ADR-033). Two reasons it
 * works this way rather than the UI inferring buzzes from state changes:
 *
 *  1. [HoldEngaged] and [HoldHalfway] correspond to no state at all — they
 *     are points on a timer — so there is nothing for the UI to observe.
 *  2. It makes the haptics **testable**. A buzz cannot be asserted from the
 *     command line, but the cue *sequence* can, and that is where the logic
 *     lives. `WearHomeViewModelTest` pins it.
 *
 * The three hold cues escalate deliberately, and the last one changes
 * *rhythm* rather than just intensity: wrist actuators are too coarse to
 * judge "how strong was that", but single-versus-double is unmistakable. The
 * silence between them is load-bearing — it is what lets [PressCommitted]
 * land with contrast instead of blending into a stream of ticks.
 */
enum class HapticCue {
    /** Finger landed on the door. "I've got it, the timer is running." */
    HoldEngaged,

    /** Halfway through the hold. "On track, about one more second." */
    HoldHalfway,

    /** The hold completed and the press was sent. "Done, you can let go." */
    PressCommitted,

    /** The hold was abandoned before completing. "Nothing was sent." */
    HoldAborted,

    /**
     * The door actually moved in response to our press. Arrives seconds after
     * the finger has left, so it is a notification rather than gesture
     * feedback, and is deliberately lighter than [PressCommitted].
     *
     * This never fires for a door someone else opened: the state it is
     * derived from is only reachable from states this watch entered by
     * submitting a press.
     */
    PressSucceeded,

    /** The press failed, at the server or at the door. "It did not happen." */
    PressFailed,

    /**
     * Voice demo: a spoken command was understood and passed the gate, so the
     * cancel window is now running. Announces "I have a command" at the moment
     * the countdown starts, which is the moment cancelling is still possible.
     */
    VoiceArmed,

    /**
     * Voice demo: halfway through the cancel window. The counterpart of
     * [HoldHalfway], and it exists for the same reason.
     *
     * Both surfaces put a ring around the bezel and drive it from empty to
     * full, so both should feel the same on the way round. The countdown had
     * only its two endpoints, which made the identical-looking ring feel
     * different depending on which screen drew it — and left the longest of
     * the two journeys (3s, versus the hold's 2s) with the least to go on.
     * Like [HoldHalfway] it is pacing, not a point of no return: cancelling
     * works right up to the end.
     */
    VoiceHalfway,

    /**
     * Voice demo: the cancel window elapsed. Fires at the instant the real
     * feature would press the remote — deliberately the same [PressCommitted]
     * feel, because "this is the point of no return" is the same news, even
     * though here nothing is sent.
     */
    VoiceCommitted,

    /**
     * Voice demo: the utterance was refused, whether by the classifier (not a
     * command, not confident) or by the door-state gate (already open, moving).
     * One cue for every refusal — the screen explains which; the wrist only
     * needs to know it did not take.
     */
    VoiceRefused,
}
