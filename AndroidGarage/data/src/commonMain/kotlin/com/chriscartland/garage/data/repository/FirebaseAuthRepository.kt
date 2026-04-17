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

package com.chriscartland.garage.data.repository

import co.touchlab.kermit.Logger
import com.chriscartland.garage.data.AuthBridge
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.domain.repository.AppLoggerRepository
import com.chriscartland.garage.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Auth repository driven by the platform's auth state listener.
 *
 * State propagation is fully reactive:
 * - [observeAuthUser][AuthBridge.observeAuthUser] emits whenever the platform
 *   auth state changes (sign-in, sign-out, token refresh).
 * - This collector maps each emission to [AuthState] and updates [_authState].
 * - Commands ([signInWithGoogle], [signOut]) are fire-and-forget — the listener
 *   picks up the resulting state change automatically.
 *
 * No Firebase imports — all platform interaction happens through [AuthBridge].
 */
class FirebaseAuthRepository(
    private val authBridge: AuthBridge,
    private val appLogger: AppLoggerRepository,
    externalScope: CoroutineScope,
) : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)

    override suspend fun getAuthState(): AuthState = _authState.value

    override fun observeAuthState(): Flow<AuthState> = _authState.asStateFlow()

    init {
        // Reactive: collect auth user changes from the platform listener.
        // Firebase's AuthStateListener fires immediately when registered,
        // so _authState transitions from Unknown → Authenticated/Unauthenticated
        // within the first emission.
        externalScope.launch {
            authBridge.observeAuthUser().collect { userInfo ->
                val newState = if (userInfo != null) {
                    val token = authBridge.getIdToken(forceRefresh = false)
                    if (token != null) {
                        AuthState.Authenticated(
                            user = User(
                                name = DisplayName(userInfo.displayName),
                                email = Email(userInfo.email),
                                idToken = token,
                            ),
                        )
                    } else {
                        // User exists but token unavailable — treat as unauthenticated.
                        // This is defensive; Firebase should have a cached token after
                        // AuthStateListener fires.
                        Logger.w { "Auth user present but getIdToken returned null" }
                        AuthState.Unauthenticated
                    }
                } else {
                    AuthState.Unauthenticated
                }
                logStateChange(newState)
                _authState.value = newState
            }
        }
    }

    override suspend fun signInWithGoogle(idToken: GoogleIdToken): AuthState {
        authBridge.signInWithGoogleToken(idToken)
        // State update happens reactively via the AuthStateListener collector.
        // Return the current state (may still be pre-sign-in if the listener
        // hasn't fired yet — callers should observe the Flow, not this return).
        return _authState.value
    }

    override suspend fun refreshIdToken(): FirebaseIdToken? {
        val token = authBridge.getIdToken(forceRefresh = true) ?: return null
        // Update _authState so the UI stays in sync with the fresh token.
        val current = _authState.value
        if (current is AuthState.Authenticated) {
            _authState.value = current.copy(
                user = current.user.copy(idToken = token),
            )
        }
        return token
    }

    @Deprecated("Use refreshIdToken() instead — this method is kept only for migration.")
    override suspend fun refreshFirebaseAuthState(): AuthState {
        // Delegate to the listener-based approach: just return current state.
        // The reactive collector handles state updates.
        return _authState.value
    }

    override suspend fun signOut() {
        // Eagerly update UI before the bridge call — no visible delay.
        _authState.value = AuthState.Unauthenticated
        logStateChange(AuthState.Unauthenticated)
        authBridge.signOut()
        // The AuthStateListener will also fire and confirm Unauthenticated.
    }

    private suspend fun logStateChange(state: AuthState) {
        Logger.d { "AuthState: $state" }
        when (state) {
            is AuthState.Authenticated ->
                appLogger.log(AppLoggerKeys.USER_AUTHENTICATED)
            AuthState.Unauthenticated ->
                appLogger.log(AppLoggerKeys.USER_UNAUTHENTICATED)
            AuthState.Unknown ->
                appLogger.log(AppLoggerKeys.USER_AUTH_UNKNOWN)
        }
    }
}
