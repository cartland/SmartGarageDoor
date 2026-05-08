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

    /** ADR-022: the repository owns the authoritative [StateFlow]. */
    override val authState: StateFlow<AuthState> = _authState

    init {
        // Reactive: collect auth user changes from the platform listener.
        // Firebase's AuthStateListener fires immediately when registered,
        // so _authState transitions from Unknown → Authenticated/Unauthenticated
        // within the first emission.
        //
        // Defensive token fetch (2.13.3 fix for sign-in regression):
        //
        //   The first call uses `forceRefresh = false` — fast path, returns
        //   the cached token when available (the common case: user already
        //   signed in, app launching, listener fires with a populated cache).
        //
        //   The fallback to `forceRefresh = true` covers a real timing
        //   window we observed on 2.13.2: when `Firebase.auth.signInWithCredential`
        //   completes, AuthStateListener can fire BEFORE Firebase's internal
        //   token cache is populated. `getIdToken(forceRefresh = false)` then
        //   returns null, the defensive `else` branch wrote `Unauthenticated`,
        //   and the UI saw "still signed out" despite a successful sign-in.
        //   `forceRefresh = true` forces Firebase to compute / fetch the token
        //   directly, sidestepping the cache-not-yet-populated state.
        //
        //   Assumptions documented for future debugging:
        //   1. The listener firing after a successful sign-in *always* has a
        //      non-null `currentUser`, even if `getIdToken(false)` returns null.
        //      If Firebase's `currentUser` is also null on first sign-in, this
        //      patch does not help — the bug is elsewhere.
        //   2. `forceRefresh = true` blocks until Firebase's internal state is
        //      consistent enough to compute a token; if Firebase has the same
        //      timing issue with forced refresh, this patch does not help.
        //   3. The fallback adds at most one network round-trip per AuthStateListener
        //      emission. Listener fires once per sign-in / sign-out / explicit
        //      refresh. Cost is negligible in human time.
        //   4. Offline launch with previously-signed-in user: the cached token
        //      is returned by the first call (fast path), the forced fallback
        //      never runs. No regression vs. pre-2.13.3 behavior.
        //   5. Offline launch with empty cache (e.g., user just installed but
        //      has saved Firebase Auth identity): the forced fallback fails
        //      (no network), `_authState` falls through to `Unauthenticated`.
        //      Same as pre-2.13.3 behavior — this race wasn't reachable before
        //      either, since the cache also wouldn't have a token.
        //
        //   This is a tactical fix in the existing observe-with-side-effect
        //   pattern. The architectural fix (move token entirely inside the
        //   repository, listener loop becomes pure) is documented in
        //   `AndroidGarage/docs/SPACING_PLAN.md`'s sister doc and tracked
        //   separately. Revisit this comment after the architectural refactor
        //   lands; the defensive fallback should be deletable then.
        externalScope.launch {
            authBridge.observeAuthUser().collect { userInfo ->
                val newState = if (userInfo != null) {
                    val token = authBridge.getIdToken(forceRefresh = false)
                        ?: authBridge.getIdToken(forceRefresh = true)
                    if (token != null) {
                        AuthState.Authenticated(
                            user = User(
                                name = DisplayName(userInfo.displayName),
                                email = Email(userInfo.email),
                                idToken = token,
                            ),
                        )
                    } else {
                        // Both cached and forced fetches returned null. Most
                        // likely cause: offline launch with empty token cache.
                        // Treat as unauthenticated (existing behavior, unchanged).
                        Logger.w { "Auth user present but getIdToken returned null (cached + forced both failed)" }
                        AuthState.Unauthenticated
                    }
                } else {
                    AuthState.Unauthenticated
                }
                logStateChange(newState)
                _authState.value = newState
                Logger.i { "authState <- $newState (source=listener)" }
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
            val newState = current.copy(
                user = current.user.copy(idToken = token),
            )
            _authState.value = newState
            Logger.i { "authState <- $newState (source=refreshIdToken)" }
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
