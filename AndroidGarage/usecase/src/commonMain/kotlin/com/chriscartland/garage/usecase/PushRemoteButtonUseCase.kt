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
 * Returns [AppResult] so callers can handle [ActionError.NotAuthenticated]
 * explicitly with exhaustive `when`.
 */
class PushRemoteButtonUseCase(
    private val ensureFreshIdToken: EnsureFreshIdTokenUseCase,
    private val authRepository: AuthRepository,
    private val remoteButtonRepository: RemoteButtonRepository,
) {
    suspend operator fun invoke(buttonAckToken: String): AppResult<Unit, ActionError> {
        val authState = authRepository.authState.value
        if (authState !is AuthState.Authenticated) {
            return AppResult.Error(ActionError.NotAuthenticated)
        }
        val idToken = ensureFreshIdToken(authState)
        val success = remoteButtonRepository.pushButton(
            idToken = idToken.asString(),
            buttonAckToken = buttonAckToken,
        )
        return if (success) {
            AppResult.Success(Unit)
        } else {
            AppResult.Error(ActionError.NetworkFailed)
        }
    }
}
