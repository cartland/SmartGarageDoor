/*
 * Copyright 2024 Chris Cartland. All rights reserved.
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
import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.domain.repository.RemoteButtonRepository

/**
 * Pushes the remote garage button.
 *
 * Per ADR-027 the UseCase doesn't see the ID token; it gates on
 * [AuthState.Authenticated] and delegates to the repository, which
 * fetches a fresh token internally before calling its data source.
 *
 * Returns [AppResult] so callers can handle [ActionError.NotAuthenticated]
 * explicitly with exhaustive `when`.
 */
class PushRemoteButtonUseCase(
    private val authRepository: AuthRepository,
    private val remoteButtonRepository: RemoteButtonRepository,
) {
    suspend operator fun invoke(buttonAckToken: String): AppResult<Unit, ActionError> {
        if (authRepository.authState.value !is AuthState.Authenticated) {
            return AppResult.Error(ActionError.NotAuthenticated)
        }
        val success = remoteButtonRepository.pushButton(buttonAckToken = buttonAckToken)
        return if (success) {
            AppResult.Success(Unit)
        } else {
            AppResult.Error(ActionError.NetworkFailed)
        }
    }
}
