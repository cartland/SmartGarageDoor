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

import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.VoiceIntent
import kotlinx.coroutines.flow.StateFlow

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
    private val createButtonAckToken: () -> String,
) : VoiceCommandEnvironment {
    override suspend fun pressButton(intent: VoiceIntent): Boolean =
        when (pushRemoteButton(buttonAckToken = createButtonAckToken())) {
            is AppResult.Success -> true
            is AppResult.Error -> false
        }
}
