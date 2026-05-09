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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Auth repository driven by the platform's auth state listener.
 *
 * State propagation is fully reactive (ADR-018, ADR-022, ADR-027):
 * - [observeAuthUser][AuthBridge.observeAuthUser] emits whenever the platform
 *   auth state changes (sign-in, sign-out, account change).
 * - The listener loop is *pure*: it maps `AuthUserInfo?` to [AuthState] without
 *   side-effecting calls. Token state is private to this repository and fetched
 *   on demand via [getIdToken] — the listener never fetches a token.
 * - Commands ([signInWithGoogle], [signOut]) are fire-and-forget; the listener
 *   reflects the resulting state change automatically.
 *
 * Per ADR-027, [authState] carries identity only — it never re-emits on token
 * refresh. Network repositories that need a token call [getIdToken] explicitly
 * before delegating to a data source. UseCases never see a token.
 *
 * No Firebase imports — all platform interaction happens through [AuthBridge].
 */
class FirebaseAuthRepository(
    private val authBridge: AuthBridge,
    private val appLogger: AppLoggerRepository,
    externalScope: CoroutineScope,
) : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)

    /** ADR-022: the repository owns the authoritative [StateFlow]. */
    override val authState: StateFlow<AuthState> = _authState

    init {
        // Pure listener loop (ADR-027): no getIdToken call inside the collect.
        // Identity changes only — token state is fetched on demand by callers
        // of getIdToken(), not embedded in AuthState.
        externalScope.launch {
            authBridge.observeAuthUser().collect { userInfo ->
                val newState = if (userInfo != null) {
                    AuthState.Authenticated(
                        user = User(
                            name = DisplayName(userInfo.displayName),
                            email = Email(userInfo.email),
                        ),
                    )
                } else {
                    AuthState.Unauthenticated
                }
                logStateChange(newState)
                _authState.value = newState
                Logger.i { "authState <- $newState (source=listener)" }
            }
        }
    }

    override suspend fun getIdToken(forceRefresh: Boolean): FirebaseIdToken? = authBridge.getIdToken(forceRefresh)

    override suspend fun signInWithGoogle(idToken: GoogleIdToken): AuthState {
        authBridge.signInWithGoogleToken(idToken)
        // State update happens reactively via the AuthStateListener collector.
        // Return the current state (may still be pre-sign-in if the listener
        // hasn't fired yet — callers should observe the Flow, not this return).
        return _authState.value
    }

    override suspend fun signOut() {
        // Eagerly update UI before the bridge call — no visible delay.
        _authState.value = AuthState.Unauthenticated
        Logger.i { "authState <- Unauthenticated (source=signOut)" }
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
