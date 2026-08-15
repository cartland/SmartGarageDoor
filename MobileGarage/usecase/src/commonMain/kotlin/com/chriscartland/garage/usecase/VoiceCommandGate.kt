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

import com.chriscartland.garage.domain.model.VoiceIntent

/**
 * Whether a spoken direction is actionable against a door position — the
 * client half of a rule the server also implements.
 *
 * ## Why this is its own object
 *
 * It was a private method on [VoiceCommandController]. The rule itself is
 * pure — a direction and a position in, a refusal or `null` out — and it is
 * the SAME rule as the server's `controller/DoorCommandGate.ts`, which is
 * likewise kept separate from the handler that calls it. Two implementations
 * of one rule should at least be shaped alike, and a rule buried in a private
 * method of a stateful controller cannot be compared to anything.
 *
 * The comparison is now literal: `VoiceGateVerdictTableTest` drives this
 * object and [VoiceDoorStateMapper] from
 * `wire-contracts/doorCommand/verdict_table.json` — the same file the
 * server's own test loads. Before that, the two sides implemented one table
 * from two copies of the reasoning, and nothing would have reported a
 * disagreement; the drift would have surfaced as a spoken command that one
 * side allowed and the other refused.
 *
 * ## What it is not
 *
 * It is not the whole gate. [VoiceCommandController] additionally requires a
 * confident classification, a cancel window that elapses without being
 * interrupted, a re-check at commit time, and the server's own verdict. This
 * object answers only "does this direction make sense from here", which is
 * the part that has to agree across platforms.
 */
object VoiceCommandGate {
    /**
     * The refusal for [intent] given [door], or `null` when the direction is
     * actionable.
     *
     * Deny-by-default: every arm returns a refusal unless the direction is
     * unambiguously the right one from that position. A `null` here is a
     * decision that the command MAY proceed to the remaining checks, never a
     * fallthrough for a position nobody considered — the `when` is exhaustive
     * over [VoiceDoorState] with no `else`, so a new state cannot silently
     * inherit "allowed".
     */
    fun reasonFor(
        intent: VoiceIntent,
        door: VoiceDoorState,
    ): VoiceCommandIgnoreReason? =
        when (door) {
            VoiceDoorState.MOVING -> VoiceCommandIgnoreReason.DOOR_MOVING
            VoiceDoorState.STUCK ->
                if (intent == VoiceIntent.OPEN) VoiceCommandIgnoreReason.DOOR_STUCK else null
            VoiceDoorState.UNKNOWN -> VoiceCommandIgnoreReason.DOOR_STATE_UNKNOWN
            VoiceDoorState.OPEN ->
                if (intent == VoiceIntent.OPEN) VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN else null
            VoiceDoorState.CLOSED ->
                if (intent == VoiceIntent.CLOSE) VoiceCommandIgnoreReason.DOOR_ALREADY_CLOSED else null
        }
}
