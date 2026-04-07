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

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import com.chriscartland.garage.BuildConfig
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

/**
 * Compose-layer Google One-Tap sign-in helper.
 *
 * Manages the [SignInClient], [BeginSignInRequest], and [ActivityResultLauncher]
 * so that ViewModels never touch Activity, Intent, or IntentSender.
 *
 * Usage:
 * ```
 * val googleSignIn = rememberGoogleSignIn(
 *     onTokenReceived = { token -> authViewModel.signInWithGoogle(token) },
 * )
 * Button(onClick = { googleSignIn.launchSignIn() }) { Text("Sign in") }
 * ```
 */
@Composable
fun rememberGoogleSignIn(onTokenReceived: (GoogleIdToken) -> Unit): GoogleSignInState {
    val context = LocalContext.current
    val signInClient = remember { Identity.getSignInClient(context) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data == null) {
                Logger.e { "Google sign-in: result data is null" }
                return@rememberLauncherForActivityResult
            }
            extractGoogleIdToken(signInClient, data)?.let(onTokenReceived)
        }
    }

    return remember(signInClient, launcher) {
        GoogleSignInState(signInClient, launcher)
    }
}

/**
 * Holds the sign-in client and launcher. Call [launchSignIn] to start the One-Tap flow.
 */
class GoogleSignInState internal constructor(
    private val signInClient: SignInClient,
    private val launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>,
) {
    fun launchSignIn() {
        checkSignInConfiguration()
        val request = BeginSignInRequest
            .builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions
                    .builder()
                    .setSupported(true)
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(false)
                    .build(),
            ).setAutoSelectEnabled(false)
            .build()

        signInClient
            .beginSignIn(request)
            .addOnSuccessListener { result ->
                val intentSenderRequest = IntentSenderRequest
                    .Builder(
                        result.pendingIntent.intentSender,
                    ).build()
                launcher.launch(intentSenderRequest)
            }.addOnFailureListener { e ->
                Logger.d { "Google sign-in not available: ${e.localizedMessage}" }
            }
    }
}

/**
 * Extract the Google ID Token from the sign-in result Intent.
 */
private fun extractGoogleIdToken(
    client: SignInClient,
    data: android.content.Intent,
): GoogleIdToken? {
    try {
        val credential = client.getSignInCredentialFromIntent(data)
        return credential.googleIdToken?.let { GoogleIdToken(it) }
    } catch (e: ApiException) {
        when (e.statusCode) {
            CommonStatusCodes.CANCELED -> Logger.d { "One-tap dialog was closed." }
            CommonStatusCodes.NETWORK_ERROR -> Logger.d { "One-tap encountered a network error." }
            else -> Logger.d {
                "Couldn't get credential from result. statusCode: ${e.statusCode} (${e.localizedMessage})"
            }
        }
    }
    return null
}

private const val INCORRECT_WEB_CLIENT_ID =
    "123456789012-zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz.apps.googleusercontent.com"

private fun checkSignInConfiguration() {
    if (BuildConfig.GOOGLE_WEB_CLIENT_ID == INCORRECT_WEB_CLIENT_ID) {
        Logger.e {
            "The web client ID matches the INCORRECT_WEB_CLIENT_ID. " +
                "One Tap Sign-In with Google will not work. " +
                "Update the web client ID. " +
                "https://console.cloud.google.com/apis/credentials"
        }
    }
}
