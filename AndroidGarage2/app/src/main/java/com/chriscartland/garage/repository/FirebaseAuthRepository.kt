package com.chriscartland.garage.repository

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import androidx.activity.ComponentActivity
import com.chriscartland.garage.BuildConfig
import com.chriscartland.garage.model.Email
import com.chriscartland.garage.model.IdToken
import com.chriscartland.garage.model.User
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class FirebaseAuthRepository @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val _user: MutableStateFlow<User?> = MutableStateFlow(null)
    val user: StateFlow<User?> = _user

    private val signInClient = Identity.getSignInClient(context)
    private val oneTapSignInRequest = BeginSignInRequest.builder()
        .setGoogleIdTokenRequestOptions(
            BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                .setSupported(true)
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setFilterByAuthorizedAccounts(false)
                .build())
        .setAutoSelectEnabled(true)
        .build()
    private val dialogSignInRequest = BeginSignInRequest.builder()
        .setGoogleIdTokenRequestOptions(
            BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                .setSupported(true)
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setFilterByAuthorizedAccounts(false)
                .build())
        .setAutoSelectEnabled(false) // Let user choose the account.
        .build()

    init {
        Firebase.auth.addAuthStateListener { auth ->
            val email = auth.currentUser?.email
            auth.currentUser?.getIdToken(true)?.addOnCompleteListener { completed ->
                _user.value = User(
                    idToken = IdToken(completed.result.token ?: ""),
                    email = Email(email ?: ""),
                )
            }
        }
        // One Tap Sign-In configuration.
        checkSignInConfiguration()
    }


    /**
     * Check to make sure we've updated the client ID.
     */
    val INCORRECT_WEB_CLIENT_ID =
        "123456789012-zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz.apps.googleusercontent.com"

    /**
     * Log a warning if the sign-in configuration is not correct.
     */
    fun checkSignInConfiguration() {
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

    fun signInSeamlessly(activity: ComponentActivity) {
        checkSignInConfiguration()
        Log.d(TAG, "beginSignIn")
        signInClient.beginSignIn(oneTapSignInRequest)
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

    fun signInWithDialog(activity: ComponentActivity) {
        checkSignInConfiguration()
        Log.d(TAG, "beginSignIn")
        signInClient.beginSignIn(dialogSignInRequest)
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

    fun signOut() {
        Log.d(TAG, "signOut")
        _user.value = null
        signInClient.signOut()
        Firebase.auth.signOut()
    }

    fun handleSignIn(activity: Activity, data: Intent?) {
        Log.d(TAG, "handleSignIn")
        try {
            val credential = signInClient.getSignInCredentialFromIntent(data)
            Log.d(TAG, "handleSignIn, received credential")
            val idToken = credential.googleIdToken ?: return
            Log.d(TAG, "handleSignIn, idToken: $idToken")
            firebaseAuthWithGoogle(activity, idToken)
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
    }

    private fun firebaseAuthWithGoogle(activity: Activity, idToken: String) {
        Log.d(TAG, "firebaseAuthWithGoogle")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        Firebase.auth.signInWithCredential(credential)
            .addOnCompleteListener(activity) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")
                    Log.d(TAG, "ID Token: $idToken")
                    val firebaseCurrentUser = Firebase.auth.currentUser
                    val email = firebaseCurrentUser?.email
                    firebaseCurrentUser?.getIdToken(true)?.addOnSuccessListener { result ->
                        val firebaseIdToken = result.token
                        Log.d(TAG, "Firebase ID Token: $firebaseIdToken")
                        _user.value = User(
                            idToken = IdToken(firebaseIdToken ?: ""),
                            email = Email(email ?: ""),
                        )
                    }
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                }
            }
    }

    companion object {
        val TAG: String = "FirebaseAuthRepository"
        const val RC_ONE_TAP_SIGN_IN = 1
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
@Suppress("unused")
interface FirebaseAuthRepositoryEntryPoint {
    fun firebaseAuthRepository(): FirebaseAuthRepository
}
