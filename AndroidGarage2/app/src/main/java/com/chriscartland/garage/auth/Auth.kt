package com.chriscartland.garage.auth

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chriscartland.garage.BuildConfig
import com.chriscartland.garage.model.DisplayName
import com.chriscartland.garage.model.Email
import com.chriscartland.garage.model.FirebaseIdToken
import com.chriscartland.garage.model.User
import com.chriscartland.garage.repository.FirebaseAuthRepository.Companion.RC_ONE_TAP_SIGN_IN
import com.chriscartland.garage.repository.FirebaseAuthRepository.Companion.TAG
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

interface AuthViewModel {
    val authState: StateFlow<AuthState>
    fun signInWithGoogle(activity: ComponentActivity)
    fun signOut()
    fun processGoogleSignInResult(data: Intent)
}

interface AuthRepository {
    val authState: StateFlow<AuthState>
    suspend fun signInWithGoogle(idToken: String): Result<AuthState>
    suspend fun signOut(): Result<Unit>
}

@HiltViewModel
class AuthViewModelImpl @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel(), AuthViewModel {

    override val authState: StateFlow<AuthState> = authRepository.authState

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
                }        }
    }

    override fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    override fun processGoogleSignInResult(data: Intent) {
        viewModelScope.launch {
            val googleIdToken = googleIdTokenFromIntent(data)
            if (googleIdToken == null) {
                Log.e(TAG, "Google sign-in failed")
                return@launch
            }
            authRepository.signInWithGoogle(googleIdToken)
        }
    }

    /**
     * Extract the Google ID Token from the Intent.
     */
    private fun googleIdTokenFromIntent(data: Intent): String? {
        Log.d(TAG, "googleIdTokenFromIntent")
        val client = signInClient
        if (client == null) {
            Log.e(TAG, "Cannot sign in without a Google client")
            return null
        }
        try {
            val credential = client.getSignInCredentialFromIntent(data)
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

class AuthRepositoryImpl @Inject constructor() : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /**
     * Sign in with Google ID Token and update the [authState].
     */
    override suspend fun signInWithGoogle(googleIdToken: String): Result<AuthState> {
        firebaseSignInWithGoogle(googleIdToken)
        return refreshFirebaseAuthState()
    }

    /**
     * Use the Google ID Token to sign in with Firebase.
     */
    private suspend fun firebaseSignInWithGoogle(googleIdToken: String) {
        suspendCoroutine { continuation ->
            Log.d(TAG, "firebaseAuthWithGoogle")
            val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
            Firebase.auth.signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Firebase signInWithGoogle: success")
                    } else {
                        Log.w(TAG, "Firebase signInWithGoogle: failure", task.exception)
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
    private suspend fun refreshFirebaseAuthState(): Result<AuthState> {
        return try {
            val user = Firebase.auth.currentUser
            if (user == null) {
                Result.failure(Error("Firebase user not found"))
            } else {
                val firebaseIdToken = getIdTokenFromFirebaseUser(user)
                if (firebaseIdToken == null) {
                    Result.failure(Error("Firebase ID token not found"))
                } else {
                    _authState.value = AuthState.Authenticated(
                        User(
                            name = DisplayName(user.displayName ?: ""),
                            email = Email(user.email ?: ""),
                            idToken = FirebaseIdToken(firebaseIdToken),
                        )
                    )
                    Result.success(_authState.value)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the Firebase ID Token.
     *
     * The Firebase API might take time to retrieve the ID Token.
     */
    private suspend fun getIdTokenFromFirebaseUser(currentUser: FirebaseUser): String? {
        return suspendCoroutine  { continuation ->
            currentUser.getIdToken(true).addOnSuccessListener { result ->
                val firebaseIdToken = result.token
                Log.d(TAG, "Firebase ID Token: $firebaseIdToken")
                continuation.resume(firebaseIdToken)
            }
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            Firebase.auth.signOut()
            _authState.value = AuthState.Unauthenticated
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

sealed class AuthState {
    data object Unauthenticated : AuthState()
    data class Authenticated(val user: User) : AuthState()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthRepositoryModule {
    @Binds
    abstract fun bindAuthRepository(authRepository: AuthRepositoryImpl): AuthRepository
}

// TODO: Figure out why I cannot instantiate AuthViewModel interface with Hilt.
// This works in Compose:
//     authViewModel: AuthViewModelImpl = hiltViewModel(),
// This fails in Compose:
//     authViewModel: AuthViewModel = hiltViewModel(),
// The failure is an ANR.
//     ANR in com.chriscartland.garage
//     PID: 8316
//     Reason: Process ProcessRecord{420e8be 8316:com.example.package/u0a228} failed to complete startup
// I tried:
//     @InstallIn(ViewModelComponent::class)
//     @InstallIn(SingletonComponent::class)
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthViewModelModule {
    @Binds
    abstract fun bindAuthViewModel(authViewModel: AuthViewModelImpl): AuthViewModel
}
