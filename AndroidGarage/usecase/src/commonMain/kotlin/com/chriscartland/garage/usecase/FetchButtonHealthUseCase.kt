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
 * Per ADR-027 the UseCase doesn't see the ID token; it gates on
 * [AuthState.Authenticated] and delegates to the repository, which
 * fetches a fresh token internally before calling its data source.
 *
 * Updates [ButtonHealthRepository.buttonHealth] on success. Returns
 * the typed result so the caller (the Manager) can react to Forbidden
 * by unsubscribing from the FCM topic.
 */
class FetchButtonHealthUseCase(
    private val authRepository: AuthRepository,
    private val repository: ButtonHealthRepository,
) {
    suspend operator fun invoke(): AppResult<ButtonHealth, ButtonHealthError> {
        if (authRepository.authState.value !is AuthState.Authenticated) {
            return AppResult.Error(ButtonHealthError.NotAuthenticated())
        }
        return repository.fetchButtonHealth()
    }
}
