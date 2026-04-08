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
 * All responses are configurable. Tracks call counts for verification.
 */
class FakeAuthBridge : AuthBridge {
    var signInResult: Boolean = true
    var userInfo: AuthUserInfo? = null
    var refreshTokenResult: FirebaseIdToken? = null

    var signInCount = 0
        private set
    var refreshCount = 0
        private set
    var signOutCount = 0
        private set

    override suspend fun signInWithGoogleToken(idToken: GoogleIdToken): Boolean {
        signInCount++
        return signInResult
    }

    override fun getCurrentUser(): AuthUserInfo? = userInfo

    override suspend fun refreshIdToken(): FirebaseIdToken? {
        refreshCount++
        return refreshTokenResult
    }

    override suspend fun signOut() {
        signOutCount++
        userInfo = null
    }
}
