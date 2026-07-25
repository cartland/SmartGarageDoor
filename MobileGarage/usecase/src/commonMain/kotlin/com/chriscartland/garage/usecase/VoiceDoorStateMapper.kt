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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.DoorPosition

/**
 * Projects the rich door model into the voice-command gate's view.
 *
 * Deny-by-default (the standing principle: okay to incorrectly ignore,
 * never okay to incorrectly execute): only the two clean terminal
 * positions map to actionable states, clean transits map to MOVING, and
 * every anomaly (stuck too long, misaligned, sensor conflict) maps to
 * UNKNOWN so the gate refuses rather than guesses. A stale device
 * check-in also forces UNKNOWN — cached state that may no longer match
 * reality must never pass the direction gate (the wrong-direction
 * hazard: cache says closed, door is actually open, an "open" command
 * would really close it).
 */
object VoiceDoorStateMapper {
    fun project(
        position: DoorPosition?,
        isCheckInStale: Boolean,
    ): VoiceDoorState {
        if (isCheckInStale) return VoiceDoorState.UNKNOWN
        return when (position) {
            DoorPosition.CLOSED -> VoiceDoorState.CLOSED
            DoorPosition.OPEN -> VoiceDoorState.OPEN
            DoorPosition.OPENING, DoorPosition.CLOSING -> VoiceDoorState.MOVING
            DoorPosition.OPENING_TOO_LONG,
            DoorPosition.CLOSING_TOO_LONG,
            DoorPosition.OPEN_MISALIGNED,
            DoorPosition.ERROR_SENSOR_CONFLICT,
            DoorPosition.UNKNOWN,
            null,
            -> VoiceDoorState.UNKNOWN
        }
    }
}
