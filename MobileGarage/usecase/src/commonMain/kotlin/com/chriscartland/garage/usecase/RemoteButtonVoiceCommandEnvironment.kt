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

import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.DoorCommandRejection
import com.chriscartland.garage.domain.model.VoiceIntent
import kotlinx.coroutines.flow.StateFlow

/**
 * The server's refusal reasons and the client's are deliberately the same set,
 * so a server verdict is worded with strings both surfaces already ship. The
 * mapping is exhaustive with no `else`: a reason added to the shared vocabulary
 * must be worded here on purpose.
 *
 * [DoorCommandRejection.UNRECOGNIZED] is what a newer server's reason decodes
 * to. It lands on "state unknown", which refuses — the deny-by-default answer
 * for "the server said no and this build cannot say why".
 */
private fun DoorCommandRejection.toIgnoreReason(): VoiceCommandIgnoreReason =
    when (this) {
        DoorCommandRejection.ALREADY_OPEN -> VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN
        DoorCommandRejection.ALREADY_CLOSED -> VoiceCommandIgnoreReason.DOOR_ALREADY_CLOSED
        DoorCommandRejection.DOOR_MOVING -> VoiceCommandIgnoreReason.DOOR_MOVING
        DoorCommandRejection.DOOR_STUCK -> VoiceCommandIgnoreReason.DOOR_STUCK
        DoorCommandRejection.DOOR_STATE_UNKNOWN -> VoiceCommandIgnoreReason.DOOR_STATE_UNKNOWN
        DoorCommandRejection.UNRECOGNIZED -> VoiceCommandIgnoreReason.DOOR_STATE_UNKNOWN
    }

/**
 * Live environment for the Home voice surface: [doorState] projects the
 * REAL observed door state (via [VoiceDoorStateMapper], so refusals
 * match the status card), and [pressButton] pushes the REAL remote
 * garage button through [PushRemoteButtonUseCase] — the same auth-gated
 * path the manual two-tap button uses (ADR-027: the UseCase gates on
 * auth state and the repository fetches its own fresh ID token).
 *
 * Contract compliance ("report failure by returning false, never by
 * throwing"): the UseCase returns a typed [AppResult] — [AppResult.Error]
 * (not authenticated, network failure) maps to `false`, which the
 * controller renders as [VoiceCommandState.Failed].
 *
 * [createButtonAckToken] mints a fresh idempotency token per press;
 * the Home wiring tags it with a `voice` marker so server logs can
 * distinguish voice presses from manual ones (the server compares the
 * token only for ack equality — the format is opaque to it).
 */
class RemoteButtonVoiceCommandEnvironment(
    override val doorState: StateFlow<VoiceDoorState>,
    private val pushRemoteButton: PushRemoteButtonUseCase,
    private val checkDoorCommand: CheckDoorCommandUseCase,
    private val createButtonAckToken: () -> String,
) : VoiceCommandEnvironment {
    /**
     * Asks the server's `doorCommand` endpoint. Any answer other than an
     * explicit accept refuses, including an unreachable server — see
     * [VoiceCommandEnvironment.confirmWithServer] for why that costs little.
     */
    override suspend fun confirmWithServer(intent: VoiceIntent): VoiceCommandIgnoreReason? =
        when (val result = checkDoorCommand(intent)) {
            is AppResult.Success -> {
                val verdict = result.data
                if (verdict.accepted) {
                    null
                } else {
                    // A refusal always carries a reason; a server that refused
                    // without saying why is still a refusal.
                    verdict.rejection?.toIgnoreReason()
                        ?: VoiceCommandIgnoreReason.DOOR_STATE_UNKNOWN
                }
            }
            // Exhaustive, no `else` (ADR-010/011): a new ActionError must be
            // classified here deliberately rather than defaulting into one of
            // these buckets, because every branch decides whether a garage door
            // command proceeds.
            is AppResult.Error -> when (result.error) {
                // Deliberately PROCEEDS rather than refusing here. Being signed
                // out is not a fact about the door, and reporting it as one
                // ("door state is unknown") would send the user looking at the
                // garage instead of at their account. The press path's own auth
                // gate is the authority on this, it refuses without any network
                // call, and it words the outcome as a failed send — which is
                // both accurate and what every surface already renders.
                //
                // Safe because passing here cannot execute anything: the very
                // next step is that gate. Pinned by
                // HomeViewModelTest.voiceCommandCommitWhileSignedOutFailsWithoutPressing.
                ActionError.NotAuthenticated -> null
                ActionError.NetworkFailed,
                ActionError.MissingData,
                ActionError.SnoozeEventChanged,
                -> VoiceCommandIgnoreReason.SERVER_UNREACHABLE
            }
        }

    override suspend fun pressButton(intent: VoiceIntent): Boolean =
        when (pushRemoteButton(buttonAckToken = createButtonAckToken())) {
            is AppResult.Success -> true
            is AppResult.Error -> false
        }
}
