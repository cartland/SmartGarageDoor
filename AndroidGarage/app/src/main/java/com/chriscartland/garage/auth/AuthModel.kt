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

@JvmInline
value class Email(
    private val s: String,
) {
    fun asString(): String = s
}

@JvmInline
value class DisplayName(
    private val s: String,
) {
    fun asString(): String = s
}

/**
 * Firebase ID Token.
 *
 * This token is used with the server to authenticate the user.
 * We use Firebase APIs, so the server expects an ID Token
 * specific for Firebase, not a different type of token.
 * For example, "Sign in with Google" will create an Google ID Token
 * with the audience "aud" set to the project client ID.
 * The Firebase APIs on the server will reject the Google token.
 *
 * We can ask the Firebase APIs to mint a token based on the Google ID Token.
 *
 * val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
 * Firebase.auth.signInWithCredential(credential)
 * ...
 * Firebase.auth.currentUser.getIdToken(...) {
 *     val firebaseIdToken = result.token
 * }
 */
data class FirebaseIdToken(
    val idToken: String,
    val exp: Long, // Expiry time, Unix Epoch Seconds
) {
    fun asString(): String = idToken
}

/**
 * Google ID Token.
 *
 * This token comes from the "Sign in with Google" APIs.
 * This token will NOT work with the server directly.
 * The server requires an ID Token minted for Firebase.
 *
 * We can ask the Firebase APIs to mint a token based on the Google ID Token.
 *
 * val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
 * Firebase.auth.signInWithCredential(credential)
 * ...
 * Firebase.auth.currentUser.getIdToken(...) {
 *     val firebaseIdToken = result.token
 * }
 */
@JvmInline
value class GoogleIdToken(
    private val s: String,
) {
    fun asString(): String = s
}

data class User(
    val name: DisplayName,
    val email: Email,
    val idToken: FirebaseIdToken,
)

sealed class AuthState {
    data object Unknown : AuthState()

    data object Unauthenticated : AuthState()

    data class Authenticated(
        val user: User,
    ) : AuthState()
}
