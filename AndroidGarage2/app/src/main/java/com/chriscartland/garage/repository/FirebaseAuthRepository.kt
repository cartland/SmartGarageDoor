package com.chriscartland.garage.repository

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
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class FirebaseAuthRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val _user: MutableStateFlow<User?> = MutableStateFlow(null)
    val user: StateFlow<User?> = _user

    // Google API for identity.
    private val signInClient = Identity.getSignInClient(context)

    init {
        // One Tap Sign-In configuration.
        checkSignInConfiguration()
    }

    /**
     * Check to make sure we've updated the client ID.
     */
    private val INCORRECT_WEB_CLIENT_ID =
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

    /**
     * Use One Tap with Google to sign in.
     *
     * This will try to seamlessly sign in without additional user input.
     * A previously authorized user might see a dialog for a few seconds.
     * If the account is incorrect, the user can cancel the sign in before it is complete.
     */
    fun signInSeamlessly(activity: ComponentActivity) {
        checkSignInConfiguration()
        Log.d(TAG, "beginSignIn")
        // One Tap Sign-In configuration.
        val oneTapSignInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(true)
                    .build())
            .setAutoSelectEnabled(true)
            .build()
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

    /**
     * Sign in flow that always shows a consent dialog.
     */
    fun signInWithDialog(activity: ComponentActivity) {
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

    /**
     * Sign out.
     */
    fun signOut() {
        Log.d(TAG, "signOut")
        _user.value = null
        signInClient.signOut()
        Firebase.auth.signOut()
    }

    /**
     * Handle the result of a Google One Tap Sign-In request.
     *
     * Extract the Google ID Token from the Intent.
     * Use the Google ID Token to sign in with Firebase.
     * Get the Firebase ID Token.
     * Return the User with updated Firebase ID Token and email.
     */
    suspend fun handleSignInWithIntent(data: Intent?) {
        val googleIdToken = googleIdTokenFromIntent(data) ?: return
        firebaseSignInWithGoogleIdToken(googleIdToken)
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            _user.value = null
            return
        }
        val firebaseIdToken = getIdTokenFromFirebaseUser(currentUser)
        val email = currentUser.email
        _user.value = User(
            idToken = IdToken(firebaseIdToken ?: ""),
            email = Email(email ?: ""),
        )
    }

    /**
     * Extract the Google ID Token from the Intent.
     */
    private fun googleIdTokenFromIntent(data: Intent?): String? {
        Log.d(TAG, "googleIdTokenFromIntent")
        try {
            val credential = signInClient.getSignInCredentialFromIntent(data)
            return credential.googleIdToken
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

    /**
     * Use the Google ID Token to sign in with Firebase.
     *
     * Submit the Google credential to Firebase authentication.
     * The suspend function will not return until the credential has been used.
     */
    private suspend fun firebaseSignInWithGoogleIdToken(googleIdToken: String) {
        suspendCoroutine { continuation ->
            Log.d(TAG, "firebaseAuthWithGoogle")
            val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
            Firebase.auth.signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Sign in success, update UI with the signed-in user's information
                        Log.d(TAG, "signInWithCredential:success")
                    } else {
                        // If sign in fails, display a message to the user.
                        Log.w(TAG, "signInWithCredential:failure", task.exception)
                    }
                    continuation.resume(Unit)
                }
        }
    }

    /**
     * Get the Firebase ID Token.
     *
     * The Firebase API might take time to retrieve the ID Token.
     */
    private suspend fun getIdTokenFromFirebaseUser(currentUser: FirebaseUser): String? {
        return suspendCancellableCoroutine { continuation ->
            currentUser.getIdToken(true).addOnSuccessListener { result ->
                val firebaseIdToken = result.token
                Log.d(TAG, "Firebase ID Token: $firebaseIdToken")
                continuation.resume(firebaseIdToken)
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
