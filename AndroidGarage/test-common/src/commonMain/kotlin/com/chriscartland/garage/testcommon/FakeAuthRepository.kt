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
 */
class FakeAuthRepository : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = _authState

    private val _signInCalls = mutableListOf<GoogleIdToken>()
    val signInCalls: List<GoogleIdToken> get() = _signInCalls
    val signInCount: Int get() = _signInCalls.size

    private var _signOutCount: Int = 0
    val signOutCount: Int get() = _signOutCount

    private var _refreshIdTokenCount: Int = 0
    val refreshIdTokenCount: Int get() = _refreshIdTokenCount

    private var signInResult: AuthState? = null
    private var refreshIdTokenResult: FirebaseIdToken? = null

    // Keep deprecated field for tests that still use refreshFirebaseAuthState
    private var legacyRefreshCount: Int = 0
    val refreshCount: Int get() = legacyRefreshCount
    private var legacyRefreshResult: AuthState? = null

    fun setAuthState(state: AuthState) {
        _authState.value = state
    }

    fun setSignInResult(value: AuthState?) {
        signInResult = value
    }

    fun setRefreshIdTokenResult(value: FirebaseIdToken?) {
        refreshIdTokenResult = value
    }

    @Deprecated("Use setRefreshIdTokenResult instead")
    fun setRefreshResult(value: AuthState?) {
        legacyRefreshResult = value
    }

    override suspend fun signInWithGoogle(idToken: GoogleIdToken): AuthState {
        _signInCalls.add(idToken)
        val result = signInResult ?: _authState.value
        _authState.value = result
        return result
    }

    override suspend fun refreshIdToken(): FirebaseIdToken? {
        _refreshIdTokenCount++
        return refreshIdTokenResult
    }

    @Deprecated("Use refreshIdToken() instead")
    override suspend fun refreshFirebaseAuthState(): AuthState {
        legacyRefreshCount++
        return legacyRefreshResult ?: _authState.value
    }

    override suspend fun signOut() {
        _signOutCount++
        _authState.value = AuthState.Unauthenticated
    }
}
