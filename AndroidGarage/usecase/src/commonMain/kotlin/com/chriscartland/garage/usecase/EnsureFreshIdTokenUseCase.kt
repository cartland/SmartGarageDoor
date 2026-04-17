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
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.repository.AuthRepository

/**
 * Ensures we have a fresh (non-expired) Firebase ID token for API calls.
 *
 * Logic:
 * - If the cached token is still valid (exp > now), return it
 * - Otherwise, force-refresh the token via the auth provider
 * - If refresh fails, fall back to the cached token (best-effort)
 *
 * The refresh also updates the repository's auth state so the UI stays
 * in sync with the new token expiry (no split-brain).
 */
class EnsureFreshIdTokenUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        currentAuth: AuthState.Authenticated,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): FirebaseIdToken =
        if (currentAuth.user.idToken.exp > currentTimeMillis) {
            currentAuth.user.idToken
        } else {
            authRepository.refreshIdToken() ?: currentAuth.user.idToken
        }
}
