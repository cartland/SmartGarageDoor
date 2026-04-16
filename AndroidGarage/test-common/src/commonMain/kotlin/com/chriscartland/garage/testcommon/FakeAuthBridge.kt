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

/**
 * Fake [AuthBridge] for unit testing.
 *
 * Configure responses with `setX()` methods. Tracks each call via call lists
 * (ADR-017 Rule 5 — call-list pattern), so tests can assert on the exact
 * arguments passed (e.g., the `idToken` from sign-in attempts), not just call
 * counts. The `*Count` accessors are convenience reads backed by the lists.
 */
class FakeAuthBridge : AuthBridge {
    private var signInResult: Boolean = true
    private var userInfo: AuthUserInfo? = null
    private var refreshTokenResult: FirebaseIdToken? = null

    private val _signInCalls = mutableListOf<GoogleIdToken>()
    val signInCalls: List<GoogleIdToken> get() = _signInCalls
    val signInCount: Int get() = _signInCalls.size

    private var _refreshCount: Int = 0
    val refreshCount: Int get() = _refreshCount

    private var _signOutCount: Int = 0
    val signOutCount: Int get() = _signOutCount

    fun setSignInResult(value: Boolean) {
        signInResult = value
    }

    fun setUserInfo(value: AuthUserInfo?) {
        userInfo = value
    }

    fun setRefreshTokenResult(value: FirebaseIdToken?) {
        refreshTokenResult = value
    }

    override suspend fun signInWithGoogleToken(idToken: GoogleIdToken): Boolean {
        _signInCalls.add(idToken)
        return signInResult
    }

    override fun getCurrentUser(): AuthUserInfo? = userInfo

    override suspend fun refreshIdToken(): FirebaseIdToken? {
        _refreshCount++
        return refreshTokenResult
    }

    override suspend fun signOut() {
        _signOutCount++
        userInfo = null
    }
}
