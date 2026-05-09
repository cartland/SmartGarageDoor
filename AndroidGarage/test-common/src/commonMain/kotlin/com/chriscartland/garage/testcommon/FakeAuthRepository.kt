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

package com.chriscartland.garage.testcommon

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.chriscartland.garage.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fake [AuthRepository] for unit testing.
 *
 * Configure responses with `setX()` methods. Tracks sign-in calls via
 * `signInCalls` (ADR-017 Rule 5 — call-list pattern).
 *
 * ADR-027: token state is a private concern. Tests can configure
 * [setIdTokenResult] to control what `getIdToken(forceRefresh)` returns.
 * `getIdTokenCount` and `getIdTokenForceRefreshCount` track call shape.
 */
class FakeAuthRepository : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = _authState

    private val _signInCalls = mutableListOf<GoogleIdToken>()
    val signInCalls: List<GoogleIdToken> get() = _signInCalls
    val signInCount: Int get() = _signInCalls.size

    private var _signOutCount: Int = 0
    val signOutCount: Int get() = _signOutCount

    private var _getIdTokenCount: Int = 0
    val getIdTokenCount: Int get() = _getIdTokenCount

    private var _getIdTokenForceRefreshCount: Int = 0

    /** Number of `getIdToken(forceRefresh = true)` calls observed. */
    val getIdTokenForceRefreshCount: Int get() = _getIdTokenForceRefreshCount

    private var signInResult: AuthState? = null
    private var idTokenResult: FirebaseIdToken? = null

    fun setAuthState(state: AuthState) {
        _authState.value = state
    }

    fun setSignInResult(value: AuthState?) {
        signInResult = value
    }

    fun setIdTokenResult(value: FirebaseIdToken?) {
        idTokenResult = value
    }

    override suspend fun getIdToken(forceRefresh: Boolean): FirebaseIdToken? {
        _getIdTokenCount++
        if (forceRefresh) _getIdTokenForceRefreshCount++
        return idTokenResult
    }

    override suspend fun signInWithGoogle(idToken: GoogleIdToken): AuthState {
        _signInCalls.add(idToken)
        val result = signInResult ?: _authState.value
        _authState.value = result
        return result
    }

    override suspend fun signOut() {
        _signOutCount++
        _authState.value = AuthState.Unauthenticated
    }
}
