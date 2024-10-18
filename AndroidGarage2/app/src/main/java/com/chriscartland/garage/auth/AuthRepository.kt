package com.chriscartland.garage.auth

import android.util.Log
import com.chriscartland.garage.preferences.PreferencesRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

const val RC_ONE_TAP_SIGN_IN = 1

interface AuthRepository {
    val authState: StateFlow<AuthState>
    suspend fun initialize()
    suspend fun signInWithGoogle(idToken: GoogleIdToken): Result<AuthState>
    suspend fun signOut(): Result<Unit>
}

class AuthRepositoryImpl @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /**
     * Check if we think the user is signed in. If so, refresh the Firebase token.
     */
    override suspend fun initialize() {
        Log.d(TAG, "initialize: preferencesRepository.isUserSignedIn")
        preferencesRepository.isUserSignedIn.collect { isSignedIn ->
            Log.d(TAG, "collect: isSignedIn=$isSignedIn")
            if (isSignedIn && _authState.value !is AuthState.Authenticated) {
                refreshFirebaseAuthState()
            }
        }
    }

    /**
     * Sign in with Google ID Token and update the [authState].
     */
    override suspend fun signInWithGoogle(idToken: GoogleIdToken): Result<AuthState> {
        firebaseSignInWithGoogle(idToken)
        return refreshFirebaseAuthState()
    }

    /**
     * Use the Google ID Token to sign in with Firebase.
     */
    private suspend fun firebaseSignInWithGoogle(idToken: GoogleIdToken) {
        suspendCoroutine { continuation ->
            Log.d(TAG, "firebaseAuthWithGoogle")
            val credential = GoogleAuthProvider.getCredential(idToken.asString(), null)
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
        try {
            val currentUser = Firebase.auth.currentUser
            if (currentUser == null) {
                preferencesRepository.setUserSignedIn(false)
                return Result.failure(Error("Firebase user not found"))
            }
            val idToken: FirebaseIdToken? = suspendCancellableCoroutine { continuation ->
                currentUser.getIdToken(true).addOnSuccessListener { result ->
                    Log.d(TAG, "Firebase ID Token: ${result.token}")
                    continuation.resume(result.token?.let { FirebaseIdToken(it) })
                }
            }
            if (idToken == null) {
                preferencesRepository.setUserSignedIn(false)
                return Result.failure(Error("Firebase ID token not found"))
            }
            _authState.value = AuthState.Authenticated(
                User(
                    name = DisplayName(currentUser.displayName ?: ""),
                    email = Email(currentUser.email ?: ""),
                    idToken = idToken,
                )
            )
            preferencesRepository.setUserSignedIn(true)
            return Result.success(_authState.value)
        } catch (e: Exception) {
            preferencesRepository.setUserSignedIn(false)
            return Result.failure(e)
        }
    }

    override suspend fun signOut(): Result<Unit> {
        preferencesRepository.setUserSignedIn(false)
        return try {
            Firebase.auth.signOut()
            _authState.value = AuthState.Unauthenticated
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class AuthRepositoryModule {
    @Binds
    abstract fun bindAuthRepository(authRepository: AuthRepositoryImpl): AuthRepository
}

private const val TAG = "AuthRepository"
