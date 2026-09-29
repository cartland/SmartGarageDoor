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

import androidx.annotation.StringRes
import com.chriscartland.garage.wear.R

/**
 * The watch face's one line for a [DoorActivity]: the same words the hero
 * screen uses for the same moment, so the chip and the screen it opens can
 * never disagree about what is happening.
 */
object DoorActivityWords {
    @StringRes
    fun status(activity: DoorActivity): Int =
        when (activity) {
            DoorActivity.PressSending -> R.string.button_hint_sending
            DoorActivity.PressAwaitingDoor -> R.string.button_hint_waiting_for_door
            is DoorActivity.DoorMoving ->
                when (activity.travel) {
                    DoorTravel.OPENING -> R.string.door_state_opening
                    DoorTravel.CLOSING -> R.string.door_state_closing
                }
        }
}
