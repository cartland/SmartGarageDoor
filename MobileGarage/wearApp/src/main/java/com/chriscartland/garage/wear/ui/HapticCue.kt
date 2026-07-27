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
 * A one-shot haptic moment in the hold-to-press gesture.
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
}
