package com.chriscartland.garage.data

import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.GoogleIdToken
import kotlinx.coroutines.flow.Flow

/**
 * Platform abstraction for authentication operations.
 *
 * Decouples auth business logic from Firebase SDK, enabling:
 * - Unit testing with fakes (no Firebase dependency in tests)
 * - Future iOS implementation with native auth
 *
 * Each method maps to a Firebase Auth SDK call but uses domain types
 * instead of Firebase types, keeping the interface platform-agnostic.
 *
 * The primary state source is [observeAuthUser] — a reactive Flow driven
 * by the platform's auth state listener (e.g., Firebase AuthStateListener).
 * Commands ([signInWithGoogleToken], [signOut]) trigger state changes that
 * the listener picks up automatically.
 */
interface AuthBridge {
    /**
     * Observe the currently authenticated user, or null when signed out.
     *
     * Emits immediately with the current state when collected, then on every
     * auth state change (sign-in, sign-out, token refresh).
     *
     * Android: wraps FirebaseAuth.AuthStateListener via callbackFlow.
     */
    fun observeAuthUser(): Flow<AuthUserInfo?>

    /**
     * Sign in using a Google ID token.
     *
     * Android: GoogleAuthProvider.getCredential() + Firebase.auth.signInWithCredential()
     *
     * @return true if sign-in succeeded
     */
    suspend fun signInWithGoogleToken(idToken: GoogleIdToken): Boolean

    /**
     * Get the current authenticated user's profile, or null if not signed in.
     *
     * Android: Firebase.auth.currentUser
     *
     * Prefer [observeAuthUser] for reactive observation. This method is retained
     * for callers that need a one-shot snapshot during migration.
     */
    fun getCurrentUser(): AuthUserInfo?

    /**
     * Return the current ID token, optionally forcing a network refresh.
     *
     * [forceRefresh] = false: returns the cached token (no network call).
     * Use this in the [observeAuthUser] collector — the token is already fresh
     * after sign-in or automatic Firebase refresh.
     *
     * [forceRefresh] = true: forces a network round-trip to Firebase servers.
     * Use this only when the cached token is known to be expired (e.g.,
     * [EnsureFreshIdTokenUseCase] detects exp < now).
     *
     * Android: Firebase.auth.currentUser.getIdToken(forceRefresh)
     */
    suspend fun getIdToken(forceRefresh: Boolean = false): FirebaseIdToken?

    /**
     * Sign out the current user.
     *
     * Android: Firebase.auth.signOut()
     */
    suspend fun signOut()
}

/**
 * Authenticated user profile from the platform auth system.
 *
 * Contains only the fields needed by the domain layer — no platform types.
 */
data class AuthUserInfo(
    val displayName: String,
    val email: String,
)
