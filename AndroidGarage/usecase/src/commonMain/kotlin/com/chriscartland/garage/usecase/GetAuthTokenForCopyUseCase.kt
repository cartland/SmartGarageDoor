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
import com.chriscartland.garage.domain.model.AuthError
import com.chriscartland.garage.domain.repository.AuthRepository

/**
 * Fetches the current Firebase ID token as a raw string suitable for
 * developer-only "copy to clipboard" actions (Diagnostics screen,
 * Function List screen — see `rememberAuthTokenCopier` for the Android
 * call sites). Returns the token directly rather than self-wrapping a
 * server call because the token IS the result here, not an internal
 * detail of an action.
 *
 * Uses `forceRefresh = false` — a fresh-enough cached token is fine for
 * developer smoke tests; forcing a network round-trip every tap would
 * add latency without value (the developer can always sign-out / sign-in
 * if they need a strictly new token).
 *
 * Reuses [AuthError.NotAuthenticated] rather than introducing a
 * one-off error type — same shape as every other auth-gated UseCase.
 */
class GetAuthTokenForCopyUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): AppResult<String, AuthError> {
        val token = authRepository.getIdToken(forceRefresh = false)
            ?: return AppResult.Error(AuthError.NotAuthenticated())
        return AppResult.Success(token.asString())
    }
}
