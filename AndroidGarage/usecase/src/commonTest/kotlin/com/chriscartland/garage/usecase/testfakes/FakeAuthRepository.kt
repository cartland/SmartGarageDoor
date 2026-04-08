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

package com.chriscartland.garage.usecase.testfakes

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.chriscartland.garage.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAuthRepository : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = _authState

    var signInCount = 0
        private set
    var signOutCount = 0
        private set
    var refreshCount = 0
        private set

    var signInResult: AuthState? = null
    var refreshResult: AuthState? = null

    fun setAuthState(state: AuthState) {
        _authState.value = state
    }

    override suspend fun signInWithGoogle(idToken: GoogleIdToken): AuthState {
        signInCount++
        val result = signInResult ?: _authState.value
        _authState.value = result
        return result
    }

    override suspend fun refreshFirebaseAuthState(): AuthState {
        refreshCount++
        return refreshResult ?: _authState.value
    }

    override suspend fun signOut() {
        signOutCount++
        _authState.value = AuthState.Unauthenticated
    }
}
