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
 * Auth repository that delegates all platform auth calls to [AuthBridge].
 *
 * No Firebase imports — all Firebase interaction happens through the bridge,
 * enabling unit testing with a fake bridge.
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
        externalScope.launch {
            refreshFirebaseAuthState()
        }
    }

    override suspend fun signInWithGoogle(idToken: GoogleIdToken): AuthState {
        authBridge.signInWithGoogleToken(idToken)
        return refreshFirebaseAuthState()
    }

    override suspend fun refreshFirebaseAuthState(): AuthState {
        try {
            val userInfo = authBridge.getCurrentUser()
                ?: return AuthState.Unauthenticated.commit()

            val idToken = authBridge.refreshIdToken()
                ?: return AuthState.Unauthenticated.commit()

            return AuthState
                .Authenticated(
                    user = User(
                        name = DisplayName(userInfo.displayName),
                        email = Email(userInfo.email),
                        idToken = idToken,
                    ),
                ).commit()
        } catch (e: Exception) {
            return AuthState.Unauthenticated.commit()
        }
    }

    private suspend fun AuthState.commit(): AuthState {
        Logger.d { "AuthState.commit(): $this" }
        when (this) {
            is AuthState.Authenticated ->
                appLogger.log(AppLoggerKeys.USER_AUTHENTICATED)

            AuthState.Unauthenticated ->
                appLogger.log(AppLoggerKeys.USER_UNAUTHENTICATED)

            AuthState.Unknown ->
                appLogger.log(AppLoggerKeys.USER_AUTH_UNKNOWN)
        }
        return this.also {
            _authState.value = it
        }
    }

    override suspend fun signOut() {
        AuthState.Unauthenticated.commit()
        authBridge.signOut()
    }
}
