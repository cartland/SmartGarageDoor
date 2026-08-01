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

/**
 * Maps a [HapticCue] to the platform constant that expresses it. One table for
 * the whole watch app, so the hold gesture and the voice demo cannot drift into
 * saying the same thing two different ways.
 *
 * All of these exist at the app's minSdk (30), so no version guard is needed.
 * `View.performHapticFeedback` needs no VIBRATE permission and respects the
 * watch's own touch-feedback setting — which is why the on-screen ring, not the
 * buzz, stays the authoritative progress channel.
 *
 * The hold cues escalate deliberately, and the last one changes *rhythm* rather
 * than just intensity: wrist actuators are too coarse to judge "how strong was
 * that", but single-versus-double is unmistakable.
 */
internal object WearHaptics {
    fun constantFor(cue: HapticCue): Int =
        when (cue) {
            HapticCue.HoldEngaged -> HapticFeedbackConstants.GESTURE_START
            HapticCue.HoldHalfway -> HapticFeedbackConstants.CLOCK_TICK
            HapticCue.PressCommitted -> HapticFeedbackConstants.CONFIRM
            HapticCue.HoldAborted -> HapticFeedbackConstants.GESTURE_END
            // Lighter than PressCommitted on purpose — this one arrives after
            // the finger has gone, as news rather than gesture feedback.
            HapticCue.PressSucceeded -> HapticFeedbackConstants.CONTEXT_CLICK
            HapticCue.PressFailed -> HapticFeedbackConstants.REJECT
            // Voice demo. Each cue borrows the constant of the hold cue at the
            // same point of the journey, so a ring travelling the bezel feels
            // the same whichever screen drew it: Armed acknowledges like a
            // finger landing, Halfway ticks like the hold's midpoint, Committed
            // borrows the CONFIRM double-beat because it marks the same kind of
            // moment. Refused shares the press's REJECT so "it did not take"
            // feels identical whichever input path produced it.
            HapticCue.VoiceArmed -> HapticFeedbackConstants.GESTURE_START
            HapticCue.VoiceHalfway -> HapticFeedbackConstants.CLOCK_TICK
            HapticCue.VoiceCommitted -> HapticFeedbackConstants.CONFIRM
            // Same constant as HoldAborted: an abandoned countdown is an
            // abandoned countdown, whichever gesture was driving it.
            HapticCue.VoiceAborted -> HapticFeedbackConstants.GESTURE_END
            HapticCue.VoiceRefused -> HapticFeedbackConstants.REJECT
        }
}
