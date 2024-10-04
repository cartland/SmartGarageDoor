package com.chriscartland.garage

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.chriscartland.garage.fcm.updateOpenDoorFcmSubscription
import com.chriscartland.garage.ui.GarageApp
import com.chriscartland.garage.viewmodel.DoorViewModel
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.android.gms.auth.api.identity.Identity

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GarageApp()
        }
        val doorViewModel: DoorViewModel by viewModels()
        lifecycleScope.launch(Dispatchers.IO) {
            val buildTimestamp = doorViewModel.buildTimestamp()
            if (buildTimestamp == null) {
                Log.e("MainActivity", "Failed to register for FCM updates")
                return@launch
            }
            updateOpenDoorFcmSubscription(this@MainActivity, buildTimestamp)
        }
    }

    fun signInSetup() {
        // One Tap Sign-In configuration.
        checkSignInConfiguration("MainActivity", this)
        val googleClientIdForWeb = BuildConfig.GOOGLE_WEB_CLIENT_ID
        val oneTapSignInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(googleClientIdForWeb)
                    .setFilterByAuthorizedAccounts(false)
                    .build())
            .setAutoSelectEnabled(true)
            .build()
        val client = Identity.getSignInClient(this)
    }

    val INCORRECT_WEB_CLIENT_ID = "123456789012-zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz.apps.googleusercontent.com"

    /**
     * Log a warning if the sign-in configuration is not correct.
     */
    fun checkSignInConfiguration(TAG: String, context: Context) {
        val googleClientIdForWeb = context.getString(R.string.web_client_id)
        if (googleClientIdForWeb == INCORRECT_WEB_CLIENT_ID) {
            Log.w(TAG, "The web client ID matches the INCORRECT_WEB_CLIENT_ID. " +
                    "One Tap Sign-In with Google will not work. " +
                    "Update the web client ID to be used with setServerClientId(). " +
                    "https://developers.google.com/identity/one-tap/android/get-saved-credentials " +
                    "Create a web client ID in this Google Cloud Console " +
                    "https://console.cloud.google.com/apis/credentials")
        }
    }


//    fun handleOneTapSignIn(activity: Activity, data: Intent?) {
//        try {
//            val oneTapClient = oneTapSignInClient ?: return
//            val credential = oneTapClient.getSignInCredentialFromIntent(data)
//            val idToken = credential.googleIdToken ?: return
//            firebaseAuthWithGoogle(activity, idToken)
//        } catch (e: ApiException) {
//            Log.e(TAG, "ApiException: handleOneTapSignIn ${e.message}")
//            when (e.statusCode) {
//                CommonStatusCodes.CANCELED -> {
//                    Log.d(TAG, "One-tap dialog was closed.")
//                    // Don't re-prompt the user.
//                    showOneTapUI.value = false
//                }
//                CommonStatusCodes.NETWORK_ERROR -> {
//                    Log.d(TAG, "One-tap encountered a network error.")
//                    showOneTapUI.value = true
//                }
//                else -> {
//                    Log.d(
//                        TAG, "Couldn't get credential from result." +
//                                " ApiException.statusCode: ${e.statusCode}" +
//                                " (${e.localizedMessage})")
//                }
//            }
//        }
//    }
//
//    private fun firebaseAuthWithGoogle(activity: Activity, idToken: String) {
//        Log.d(TAG, "firebaseAuthWithGoogle")
//        val credential = GoogleAuthProvider.getCredential(idToken, null)
//        Firebase.auth.signInWithCredential(credential)
//            .addOnCompleteListener(activity) { task ->
//                if (task.isSuccessful) {
//                    // Sign in success, update UI with the signed-in user's information
//                    Log.d(TAG, "signInWithCredential:success")
//                    val user = Firebase.auth.currentUser
//                    firebaseUser.value = user
//                } else {
//                    // If sign in fails, display a message to the user.
//                    Log.w(TAG, "signInWithCredential:failure", task.exception)
//                    firebaseUser.value = null
//                }
//            }
//    }
//
//    fun signOut() {
//        Firebase.auth.signOut()
//        oneTapSignInClient?.signOut()
//        firebaseUser.value = null
//    }
}
