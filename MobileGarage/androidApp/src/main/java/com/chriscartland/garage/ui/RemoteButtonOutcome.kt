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

package com.chriscartland.garage.ui

import com.chriscartland.garage.domain.model.RemoteButtonState

/** How the remote button's resting outcome should be coloured. */
enum class OutcomeTone {
    /** In flight, cancelled, or idle: the neutral disabled tone. */
    NEUTRAL,

    /** The door did what was asked: the closed-door green. */
    SUCCESS,

    /** The server or the door did not respond: the error tone. */
    FAILURE,
}

/**
 * The outcome-colour rule for the remote button, the same on both phones.
 *
 * iOS coloured "Done" green and the two failures red; Android greyed every
 * state after the confirm, so "Done" was a green button on one phone and a
 * grey one on the other (audit finding 3.1, strategy 1.6). Decided here as
 * a plain object so the rule is pinned on the JVM; the Composable only maps
 * a tone to Material colours.
 */
object RemoteButtonOutcome {
    fun toneFor(state: RemoteButtonState): OutcomeTone =
        when (state) {
            RemoteButtonState.Succeeded -> OutcomeTone.SUCCESS
            RemoteButtonState.ServerFailed,
            RemoteButtonState.Forbidden,
            RemoteButtonState.DoorFailed,
            -> OutcomeTone.FAILURE
            RemoteButtonState.Ready,
            RemoteButtonState.Preparing,
            RemoteButtonState.AwaitingConfirmation,
            RemoteButtonState.Cancelled,
            RemoteButtonState.SendingToServer,
            RemoteButtonState.SendingToDoor,
            -> OutcomeTone.NEUTRAL
        }
}
