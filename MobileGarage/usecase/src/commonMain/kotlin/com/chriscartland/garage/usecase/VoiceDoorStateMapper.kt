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
 * never okay to incorrectly execute): clean terminal positions map to
 * actionable states, clean transits map to MOVING, and every genuine
 * anomaly (stuck too long, sensor conflict) maps to UNKNOWN so the gate
 * refuses rather than guesses. A stale device check-in also forces
 * UNKNOWN — cached state that may no longer match reality must never
 * pass the direction gate (the wrong-direction hazard: cache says
 * closed, door is actually open, an "open" command would really close
 * it).
 *
 * ## Why OPEN_MISALIGNED is OPEN, not an anomaly
 *
 * It reads like one, and it was treated as one until it turned out that
 * a misaligned door could not be closed by voice — which is the single
 * case where you most want to close it.
 *
 * It is not a guess. The server emits `OpenMisaligned` from `Open` only
 * when the **closed sensor reads NOT-closed** and the open sensor has
 * dropped out within `TOO_SHORT_DURATION_SECONDS`
 * (`EventInterpreter.ts`) — a door that is definitively not closed with
 * a flaky open sensor, not a door of unknown position. The
 * wrong-direction hazard therefore cannot arise: CLOSE is the correct
 * direction, and OPEN is refused as already-open. Nor can it mask a
 * door that has since closed — the moment the closed sensor trips, the
 * server transitions straight to `Closed`, and the gate re-checks at
 * commit time anyway.
 *
 * This also makes voice agree with the rest of the app, which has
 * always treated it as a confident Open: the status label renders
 * "Open", the hero screen's hint offers "Hold to close", and the door
 * art draws it at `OPEN_POSITION`. Voice refusing what every other
 * surface acts on was the inconsistency, not the fix.
 *
 * The stuck-transit positions stay UNKNOWN deliberately. They are
 * genuinely mid-travel and genuinely wrong, so "state unknown" is more
 * honest than either terminal answer — and every direction is refused
 * under MOVING too, so nothing is gained by reclassifying them.
 */
object VoiceDoorStateMapper {
    fun project(
        position: DoorPosition?,
        isCheckInStale: Boolean,
    ): VoiceDoorState {
        if (isCheckInStale) return VoiceDoorState.UNKNOWN
        return when (position) {
            DoorPosition.CLOSED -> VoiceDoorState.CLOSED
            DoorPosition.OPEN, DoorPosition.OPEN_MISALIGNED -> VoiceDoorState.OPEN
            DoorPosition.OPENING, DoorPosition.CLOSING -> VoiceDoorState.MOVING
            DoorPosition.OPENING_TOO_LONG,
            DoorPosition.CLOSING_TOO_LONG,
            DoorPosition.ERROR_SENSOR_CONFLICT,
            DoorPosition.UNKNOWN,
            null,
            -> VoiceDoorState.UNKNOWN
        }
    }
}
