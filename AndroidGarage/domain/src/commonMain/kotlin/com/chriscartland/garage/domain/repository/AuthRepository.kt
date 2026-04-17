package com.chriscartland.garage.domain.repository

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.GoogleIdToken
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** One-shot: current auth state right now. */
    suspend fun getAuthState(): AuthState

    /** Observation: auth state changes over time. */
    fun observeAuthState(): Flow<AuthState>

    suspend fun signInWithGoogle(idToken: GoogleIdToken): AuthState

    /**
     * Force-refresh the ID token via the auth provider and return it.
     *
     * Also updates [observeAuthState] with the new token so the UI stays
     * in sync (avoids split-brain where the UseCase has a fresh token but
     * the ViewModel's StateFlow still shows the old expiry).
     *
     * Returns null if refresh fails (e.g., user signed out, network error).
     */
    suspend fun refreshIdToken(): FirebaseIdToken?

    @Deprecated("Use refreshIdToken() instead", ReplaceWith("refreshIdToken()"))
    suspend fun refreshFirebaseAuthState(): AuthState

    suspend fun signOut()
}
