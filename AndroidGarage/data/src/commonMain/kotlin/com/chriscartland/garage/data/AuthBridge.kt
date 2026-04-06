package com.chriscartland.garage.data

import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.GoogleIdToken

/**
 * Platform abstraction for authentication operations.
 *
 * Decouples auth business logic from Firebase SDK, enabling:
 * - Unit testing with fakes (no Firebase dependency in tests)
 * - Future iOS implementation with native auth
 *
 * Each method maps to a Firebase Auth SDK call but uses domain types
 * instead of Firebase types, keeping the interface platform-agnostic.
 */
interface AuthBridge {
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
     */
    fun getCurrentUser(): AuthUserInfo?

    /**
     * Force-refresh the auth token and return it, or null on failure.
     *
     * Android: Firebase.auth.currentUser.getIdToken(true)
     */
    suspend fun refreshIdToken(): FirebaseIdToken?

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
