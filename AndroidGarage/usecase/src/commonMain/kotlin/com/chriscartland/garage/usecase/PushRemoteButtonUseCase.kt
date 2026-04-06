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

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.domain.repository.PushRepository

/**
 * Pushes the remote garage button.
 *
 * Handles:
 * - Verifying the user is authenticated
 * - Ensuring the auth token is fresh
 * - Delegating to PushRepository
 *
 * @return true if the push was initiated, false if not authenticated
 */
class PushRemoteButtonUseCase(
    private val ensureFreshIdToken: EnsureFreshIdTokenUseCase,
    private val authRepository: AuthRepository,
    private val pushRepository: PushRepository,
) {
    suspend operator fun invoke(buttonAckToken: String): Boolean {
        val authState = authRepository.authState.value
        if (authState !is AuthState.Authenticated) {
            return false
        }
        val idToken = ensureFreshIdToken(authState)
        pushRepository.push(
            idToken = idToken.asString(),
            buttonAckToken = buttonAckToken,
        )
        return true
    }
}
