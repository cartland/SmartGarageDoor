package com.chriscartland.garage.repository

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.chriscartland.garage.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class AuthRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    val _idToken: MutableStateFlow<String> = MutableStateFlow("")
    val idToken: StateFlow<String> = _idToken

    private val credentialManager by lazy {
        CredentialManager.create(context)
    }

    /**
     * Request a Google ID token for an existing authorized account.
     */
    suspend fun seamlessSignIn(
        activityContext: Context,
        nonce: String? = null,
    ) {
        // Seamless Google ID Token fetch.
        val seamlessGoogleIdOption: GetGoogleIdOption =
            GetGoogleIdOption.Builder().apply {
                setFilterByAuthorizedAccounts(true)
                setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                setAutoSelectEnabled(true)
                if (nonce != null) {
                    setNonce("<nonce string to use when generating a Google ID token>")
                }
            }.build()

        val seamlessRequest: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(seamlessGoogleIdOption)
            .build()
        try {
            val result = credentialManager.getCredential(
                request = seamlessRequest,
                context = activityContext,
            )
            handleSignIn(result)
        } catch (e: GetCredentialException) {
            Log.w("AuthRepository", "No credential available", e)
        }
    }

    /**
     * Ask a user to sign in.
     */
    suspend fun signInWithGoogle(
        activityContext: Context,
        nonce: String? = null,
    ) {
        // Sign in with Google request.
        val signInWithGoogleOption: GetSignInWithGoogleOption =
            GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).apply {
                if (nonce != null) {
                    setNonce("<nonce string to use when generating a Google ID token>")
                }
            }.build()
        val signInRequest: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()
        try {
            val result = credentialManager.getCredential(
                request = signInRequest,
                context = activityContext,
            )
            handleSignIn(result)
        } catch (e: GetCredentialException) {
            Log.w("AuthRepository", "Failed to sign in", e)
        }
    }

    private fun handleSignIn(result: GetCredentialResponse) {
        // Handle the successfully returned credential.
        when (val credential = result.credential) {
            // GoogleIdToken credential
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        // Use googleIdTokenCredential and extract the ID to validate and
                        // authenticate on your server.
                        val googleIdTokenCredential = GoogleIdTokenCredential
                            .createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken
                        Log.i("AuthRepository", "Received ID token: $idToken")
                        _idToken.value = idToken // Export to StateFlow
                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e("AuthRepository", "Received an invalid Google IdToken response", e)
                    }
                } else {
                    // Catch any unrecognized custom credential type here.
                    Log.e("AuthRepository", "Unexpected type of credential")
                }
            }
            else -> {
                // Catch any unrecognized credential type here.
                Log.e("AuthRepository", "Unexpected type of credential")
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AuthRepositoryEntryPoint {
    fun authRepository(): AuthRepository
}
