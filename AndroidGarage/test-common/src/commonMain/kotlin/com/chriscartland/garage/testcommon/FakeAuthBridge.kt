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

import com.chriscartland.garage.data.AuthBridge
import com.chriscartland.garage.data.AuthUserInfo
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.GoogleIdToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake [AuthBridge] for unit testing.
 *
 * Configure responses with `setX()` methods. Tracks each call via call lists
 * (ADR-017 Rule 5 — call-list pattern).
 *
 * [observeAuthUser] emits from a controllable [MutableStateFlow]. Set it via
 * [setAuthUser] to simulate Firebase AuthStateListener events.
 * [getIdToken] returns the value set by [setIdTokenResult].
 */
class FakeAuthBridge : AuthBridge {
    private val authUserFlow = MutableStateFlow<AuthUserInfo?>(null)
    private var signInResult: Boolean = true
    private var idTokenResult: FirebaseIdToken? = null

    private val _signInCalls = mutableListOf<GoogleIdToken>()
    val signInCalls: List<GoogleIdToken> get() = _signInCalls
    val signInCount: Int get() = _signInCalls.size

    private var _getIdTokenCount: Int = 0
    val getIdTokenCount: Int get() = _getIdTokenCount

    private var _signOutCount: Int = 0
    val signOutCount: Int get() = _signOutCount

    fun setSignInResult(value: Boolean) {
        signInResult = value
    }

    fun setAuthUser(value: AuthUserInfo?) {
        authUserFlow.value = value
    }

    fun setIdTokenResult(value: FirebaseIdToken?) {
        idTokenResult = value
    }

    override fun observeAuthUser(): Flow<AuthUserInfo?> = authUserFlow

    override suspend fun signInWithGoogleToken(idToken: GoogleIdToken): Boolean {
        _signInCalls.add(idToken)
        return signInResult
    }

    override fun getCurrentUser(): AuthUserInfo? = authUserFlow.value

    override suspend fun getIdToken(forceRefresh: Boolean): FirebaseIdToken? {
        _getIdTokenCount++
        return idTokenResult
    }

    override suspend fun signOut() {
        _signOutCount++
        authUserFlow.value = null
    }
}
