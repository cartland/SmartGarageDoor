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
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.ButtonHealth
import com.chriscartland.garage.domain.model.ButtonHealthError
import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.domain.repository.ButtonHealthRepository

/**
 * Cold-start fetch for the remote-button device's online/offline state.
 *
 * Self-wraps auth (mirrors `PushRemoteButtonUseCase` and
 * `SnoozeNotificationsUseCase`): reads the current [AuthState], gates on
 * [AuthState.Authenticated], and refreshes the id token via
 * [EnsureFreshIdTokenUseCase] before calling the repository. ViewModels
 * call `invoke()` with no token argument and handle the typed result.
 *
 * Updates [ButtonHealthRepository.buttonHealth] on success. Returns
 * the typed result so the caller (the Manager) can react to Forbidden
 * by unsubscribing from the FCM topic.
 */
class FetchButtonHealthUseCase(
    private val ensureFreshIdToken: EnsureFreshIdTokenUseCase,
    private val authRepository: AuthRepository,
    private val repository: ButtonHealthRepository,
) {
    suspend operator fun invoke(): AppResult<ButtonHealth, ButtonHealthError> {
        val authState = authRepository.authState.value
        if (authState !is AuthState.Authenticated) {
            return AppResult.Error(ButtonHealthError.NotAuthenticated())
        }
        val idToken = ensureFreshIdToken(authState)
        return repository.fetchButtonHealth(idToken.asString())
    }
}
