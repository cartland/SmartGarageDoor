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

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chriscartland.garage.BuildConfig
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

interface AuthViewModel {
    val authState: StateFlow<AuthState>
    // TODO: Hide AuthRepository by making sure that Hilt instantiates a singleton.
    // I had an issue in release builds (not debug) where Hilt would instantiate multiple
    // AuthRepository instances for each ViewModel, which caused inconsistent auth states.
    // As a workaround, I'm exposing the AuthRepository in the ViewModel so
    // we can access the same auth state in other parts of the app.
    // This breaks separation of concerns, but is necessary until I can ensure that multiple
    // parts of the app are able to access the correct AuthRepository through dependency injection.
    val authRepository: AuthRepository
    fun signInWithGoogle(activity: ComponentActivity)
    fun signOut()
    fun processGoogleSignInResult(data: Intent)
}

@HiltViewModel
class AuthViewModelImpl @Inject constructor(
    private val _authRepository: AuthRepository,
) : ViewModel(), AuthViewModel {

    override val authRepository: AuthRepository = _authRepository

    override val authState: StateFlow<AuthState> = _authRepository.authState

    override fun signInWithGoogle(activity: ComponentActivity) {
        viewModelScope.launch {
            checkSignInConfiguration()
            Log.d(TAG, "beginSignIn")
            // Dialog Sign-In configuration.
            val dialogSignInRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(
                    BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                        .setSupported(true)
                        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                        .setFilterByAuthorizedAccounts(false)
                        .build())
                .setAutoSelectEnabled(false) // Let user choose the account.
                .build()
            createSignInClient(activity).beginSignIn(dialogSignInRequest)
                .addOnSuccessListener(activity) { result ->
                    try {
                        activity.startIntentSenderForResult(
                            result.pendingIntent.intentSender, RC_ONE_TAP_SIGN_IN,
                            null, 0, 0, 0, null)
                    } catch (e: IntentSender.SendIntentException) {
                        Log.e(TAG, "Couldn't start One Tap UI: ${e.localizedMessage}")
                    }
                }
                .addOnFailureListener(activity) { e ->
                    // No saved credentials found. Launch the One Tap sign-up flow, or
                    // do nothing and continue presenting the signed-out UI.
                    Log.d(TAG, e.localizedMessage)
                }
        }
    }

    override fun signOut() {
        viewModelScope.launch {
            _authRepository.signOut()
        }
    }

    override fun processGoogleSignInResult(data: Intent) {
        viewModelScope.launch {
            val googleIdToken = googleIdTokenFromIntent(data)
            if (googleIdToken == null) {
                Log.e(TAG, "Google sign-in failed")
                return@launch
            }
            _authRepository.signInWithGoogle(googleIdToken)
        }
    }

    /**
     * Extract the Google ID Token from the Intent.
     */
    private fun googleIdTokenFromIntent(data: Intent): GoogleIdToken? {
        Log.d(TAG, "googleIdTokenFromIntent")
        val client = signInClient
        if (client == null) {
            Log.e(TAG, "Cannot sign in without a Google client")
            return null
        }
        try {
            val credential = client.getSignInCredentialFromIntent(data)
            return credential.googleIdToken?.let { GoogleIdToken(it) }
        } catch (e: ApiException) {
            Log.e(TAG, "ApiException: handleOneTapSignIn ${e.message}")
            when (e.statusCode) {
                CommonStatusCodes.CANCELED -> {
                    Log.d(TAG, "One-tap dialog was closed.")
                }
                CommonStatusCodes.NETWORK_ERROR -> {
                    Log.d(TAG, "One-tap encountered a network error.")
                }
                else -> {
                    Log.d(
                        TAG, "Couldn't get credential from result." +
                                " ApiException.statusCode: ${e.statusCode}" +
                                " (${e.localizedMessage})")
                }
            }
        }
        return null
    }

    private var _signInClient: SignInClient? = null
    private val signInClient: SignInClient?
        get() = _signInClient

    // Google API for identity.
    private fun createSignInClient(context: Context) =
        Identity.getSignInClient(context).also { _signInClient = it }

    /**
     * Check to make sure we've updated the client ID.
     */
    private val INCORRECT_WEB_CLIENT_ID =
        "123456789012-zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz.apps.googleusercontent.com"

    /**
     * Log a warning if the sign-in configuration is not correct.
     */
    private fun checkSignInConfiguration() {
        val googleClientIdForWeb = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (googleClientIdForWeb == INCORRECT_WEB_CLIENT_ID) {
            Log.e(
                "checkSignInConfiguration",
                "The web client ID matches the INCORRECT_WEB_CLIENT_ID. " +
                        "One Tap Sign-In with Google will not work. " +
                        "Update the web client ID to be used with setServerClientId(). " +
                        "https://developers.google.com/identity/one-tap/android/get-saved-credentials " +
                        "Create a web client ID in this Google Cloud Console " +
                        "https://console.cloud.google.com/apis/credentials"
            )
        }
    }
}

/**
 *  TODO: Figure out why I cannot instantiate AuthViewModel interface with Hilt.
 *  This works in Compose:
 *      authViewModel: AuthViewModelImpl = hiltViewModel(),
 *  This fails in Compose:
 *      authViewModel: AuthViewModel = hiltViewModel(),
 *  The failure is an ANR.
 *      ANR in com.chriscartland.garage
 *      PID: 8316
 *      Reason: Process ProcessRecord{420e8be 8316:com.example.package/u0a228} failed to complete startup
 *  I tried:
 *      @InstallIn(ViewModelComponent::class)
 *      @InstallIn(SingletonComponent::class)
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class AuthViewModelModule {
    @Binds
    abstract fun bindAuthViewModel(authViewModel: AuthViewModelImpl): AuthViewModel
}

private const val TAG = "AuthViewModel"