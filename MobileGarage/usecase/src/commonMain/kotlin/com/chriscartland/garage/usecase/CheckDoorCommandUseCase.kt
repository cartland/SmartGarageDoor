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
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DoorCommandVerdict
import com.chriscartland.garage.domain.model.VoiceIntent
import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.domain.repository.DoorCommandRepository

/**
 * Asks the server whether a spoken direction is actionable.
 *
 * Self-wraps auth exactly like [PushRemoteButtonUseCase] (ADR-027): gates on
 * [AuthState.Authenticated] and lets the repository fetch its own fresh token,
 * so no caller ever handles an ID token. The two must agree — a command that
 * passes this gate goes straight on to that press, and it would be strange for
 * one to consider you signed in and the other not.
 */
class CheckDoorCommandUseCase(
    private val authRepository: AuthRepository,
    private val doorCommandRepository: DoorCommandRepository,
) {
    suspend operator fun invoke(intent: VoiceIntent): AppResult<DoorCommandVerdict, ActionError> {
        if (authRepository.authState.value !is AuthState.Authenticated) {
            return AppResult.Error(ActionError.NotAuthenticated)
        }
        return doorCommandRepository.checkCommand(intent)
    }
}
