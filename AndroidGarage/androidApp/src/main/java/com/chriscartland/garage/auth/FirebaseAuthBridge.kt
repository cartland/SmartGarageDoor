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
import com.chriscartland.garage.data.AuthBridge
import com.chriscartland.garage.data.AuthUserInfo
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Firebase implementation of [AuthBridge].
 *
 * Wraps all Firebase Auth SDK calls so that [FirebaseAuthRepository] and tests
 * never directly import Firebase types.
 */
class FirebaseAuthBridge : AuthBridge {
    override suspend fun signInWithGoogleToken(idToken: GoogleIdToken): Boolean =
        suspendCoroutine { continuation ->
            val credential = GoogleAuthProvider.getCredential(idToken.asString(), null)
            Firebase.auth
                .signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Logger.d { "Firebase signInWithGoogle: success" }
                    } else {
                        Logger.w { "Firebase signInWithGoogle: failure: ${task.exception}" }
                    }
                    continuation.resume(task.isSuccessful)
                }
        }

    override fun getCurrentUser(): AuthUserInfo? {
        val user = Firebase.auth.currentUser ?: return null
        return AuthUserInfo(
            displayName = user.displayName ?: "",
            email = user.email ?: "",
        )
    }

    override suspend fun refreshIdToken(): FirebaseIdToken? {
        val currentUser = Firebase.auth.currentUser ?: return null
        return suspendCancellableCoroutine { continuation ->
            currentUser
                .getIdToken(true)
                .addOnSuccessListener { result ->
                    Logger.d { "Firebase ID Token refreshed" }
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
    }

    override suspend fun signOut() {
        try {
            Firebase.auth.signOut()
        } catch (e: Exception) {
            Logger.e { "Sign out error: $e" }
        }
    }
}
