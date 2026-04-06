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

package com.chriscartland.garage.auth

import co.touchlab.kermit.Logger
import com.chriscartland.garage.applogger.AppLoggerRepository
import com.chriscartland.garage.config.AppLoggerKeys
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.domain.repository.AuthRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

const val RC_ONE_TAP_SIGN_IN = 1

class FirebaseAuthRepository(
    private val appLoggerRepository: AppLoggerRepository,
) : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            refreshFirebaseAuthState()
        }
    }

    /**
     * Sign in with Google ID Token and update the [authState].
     */
    override suspend fun signInWithGoogle(idToken: GoogleIdToken): AuthState {
        firebaseSignInWithGoogle(idToken)
        return refreshFirebaseAuthState()
    }

    /**
     * Use the Google ID Token to sign in with Firebase.
     */
    private suspend fun firebaseSignInWithGoogle(idToken: GoogleIdToken) {
        suspendCoroutine { continuation ->
            Logger.d { "firebaseAuthWithGoogle" }
            val credential = GoogleAuthProvider.getCredential(idToken.asString(), null)
            Firebase.auth
                .signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Logger.d { "Firebase signInWithGoogle: success" }
                    } else {
                        Logger.w { "Firebase signInWithGoogle: failure: ${task.exception}" }
                    }
                    continuation.resume(Unit)
                }
        }
    }

    /**
     * Refresh the AuthState based on the Firebase auth state.
     *
     * Get the ID token and other user information from Firebase
     * and emit them with [_authState].
     */
    override suspend fun refreshFirebaseAuthState(): AuthState {
        try {
            val currentUser = Firebase.auth.currentUser ?: return AuthState.Unauthenticated.commit()
            val idToken: FirebaseIdToken? =
                suspendCancellableCoroutine { continuation ->
                    currentUser
                        .getIdToken(true)
                        .addOnSuccessListener { result ->
                            Logger.d { "Firebase ID Token: ${result.token}" }
                            continuation.resume(
                                result.token?.let {
                                    FirebaseIdToken(
                                        idToken = it,
                                        exp = result.expirationTimestamp,
                                    )
                                },
                            )
                        }.addOnFailureListener { exception ->
                            Logger.e { "Failed to get Firebase ID Token: $exception" }
                            continuation.resume(null)
                        }
                }
            if (idToken == null) {
                return AuthState.Unauthenticated.commit()
            }
            return AuthState
                .Authenticated(
                    user =
                        User(
                            name = DisplayName(currentUser.displayName ?: ""),
                            email = Email(currentUser.email ?: ""),
                            idToken = idToken,
                        ),
                ).commit()
        } catch (e: Exception) {
            return AuthState.Unauthenticated.commit()
        }
    }

    /**
     * Commit the new AuthState and handle any side effects.
     */
    private suspend fun AuthState.commit(): AuthState {
        Logger.d { "AuthState.commit(): $this" }
        when (this) {
            is AuthState.Authenticated ->
                appLoggerRepository.log(AppLoggerKeys.USER_AUTHENTICATED)

            AuthState.Unauthenticated ->
                appLoggerRepository.log(AppLoggerKeys.USER_UNAUTHENTICATED)

            AuthState.Unknown ->
                appLoggerRepository.log(AppLoggerKeys.USER_AUTH_UNKNOWN)
        }
        return this.also {
            _authState.value = it
        }
    }

    override suspend fun signOut() {
        AuthState.Unauthenticated.commit()
        try {
            Firebase.auth.signOut()
        } catch (e: Exception) {
            Logger.e { e.toString() }
        }
    }
}
