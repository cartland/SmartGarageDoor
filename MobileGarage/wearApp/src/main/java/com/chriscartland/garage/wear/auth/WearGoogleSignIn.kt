/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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

package com.chriscartland.garage.wear.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Sign in with Google on Wear OS via Credential Manager.
 *
 * Credential Manager is Google's recommended auth surface on Wear OS 5.1+.
 * On older watches (or when no Google account is available on the watch)
 * `getCredential` throws and this returns null — the UI stays on the
 * sign-in chip. The returned [GoogleIdToken] feeds the shared
 * `SignInWithGoogleUseCase` → `FirebaseAuthRepository` chain, exactly as
 * the phone's One-Tap flow does.
 */
object WearGoogleSignIn {
    suspend fun requestGoogleIdToken(
        context: Context,
        serverClientId: String,
    ): GoogleIdToken? {
        if (serverClientId.isBlank()) {
            Logger.w { "GOOGLE_WEB_CLIENT_ID is not configured; cannot sign in" }
            return null
        }
        val request = GetCredentialRequest
            .Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()
        return try {
            val credential = CredentialManager.create(context).getCredential(context, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleIdToken(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                Logger.w { "Unexpected credential type: ${credential.type}" }
                null
            }
        } catch (e: GetCredentialException) {
            Logger.w { "Sign in with Google failed: $e" }
            null
        }
    }
}
