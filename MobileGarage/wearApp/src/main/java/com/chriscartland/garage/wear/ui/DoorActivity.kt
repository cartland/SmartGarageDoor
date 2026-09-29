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

import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.RemoteButtonState

/** Which way a moving door is going: the only two positions that are motion. */
enum class DoorTravel { OPENING, CLOSING }

/**
 * What is happening to the door RIGHT NOW, if anything — the moments a wrist
 * that has dropped should still be told about (strategy 3.3).
 *
 * One decision, two consumers. [WearHomeViewModel] derives BOTH the screen
 * wake ([WearHomeViewModel.keepScreenOn]) and the watch-face chip
 * ([WearHomeViewModel.doorActivity]) from this, on the same bounded window,
 * so the chip can never outlive the app's reason to keep watching. Null is
 * "nothing is happening", and it is deliberately the same null for both: a
 * chip still up after the app has stopped checking would be a claim it no
 * longer has grounds for.
 */
sealed interface DoorActivity {
    /** A press is on its way to the server. */
    data object PressSending : DoorActivity

    /** The server took the press; the door has not answered yet. */
    data object PressAwaitingDoor : DoorActivity

    /** The door is physically in motion, whoever moved it. */
    data class DoorMoving(
        val travel: DoorTravel,
    ) : DoorActivity

    companion object {
        /**
         * The trigger rule, exactly as the screen wake had it: a held press in
         * flight, then a door in motion. An in-progress hold is NOT an
         * activity (the finger is on the screen), a stuck door is NOT motion
         * (it has stopped, and the hero says so), and a SPOKEN press waiting
         * on the door counts the same as a held one — a press is a press.
         */
        fun current(
            buttonState: RemoteButtonState,
            doorPosition: DoorPosition?,
            voicePressAwaitingDoor: Boolean,
        ): DoorActivity? =
            when {
                buttonState is RemoteButtonState.SendingToServer -> PressSending
                buttonState is RemoteButtonState.SendingToDoor -> PressAwaitingDoor
                doorPosition == DoorPosition.OPENING -> DoorMoving(DoorTravel.OPENING)
                doorPosition == DoorPosition.CLOSING -> DoorMoving(DoorTravel.CLOSING)
                voicePressAwaitingDoor -> PressAwaitingDoor
                else -> null
            }
    }
}
