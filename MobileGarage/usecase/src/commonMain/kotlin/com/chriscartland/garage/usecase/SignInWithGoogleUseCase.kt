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
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.chriscartland.garage.domain.repository.AuthRepository

/**
 * Signs the user in with a Google ID token. Returns the post-sign-in
 * [AuthState] so callers can react to success or failure without observing
 * a separate flow:
 *
 * - [AuthState.Authenticated] — sign-in succeeded.
 * - [AuthState.Unauthenticated] — sign-in failed; the user remains signed
 *   out.
 * - [AuthState.Unknown] — an in-flight refresh is still pending; the
 *   eventual outcome resolves via [AuthRepository.authState].
 *
 * `AuthState` is a sealed type, so callers `when (state)` exhaustively —
 * the same typed-result discipline as `AppResult<T, E>`.
 */
class SignInWithGoogleUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(idToken: GoogleIdToken): AuthState = authRepository.signInWithGoogle(idToken)
}
