package com.chriscartland.garage.domain.repository

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.GoogleIdToken
import kotlinx.coroutines.flow.StateFlow

/**
 * Authentication state owner.
 *
 * Per ADR-022, the repository owns the authoritative `StateFlow<AuthState>`;
 * a platform listener (ADR-018) drives writes on an app-lifetime scope.
 * ViewModels and UseCases expose this same [StateFlow] by reference — no
 * mirrors. Callers can read [authState].value for synchronous one-shot
 * access; commands ([signInWithGoogle], [signOut]) are fire-and-forget and
 * the listener reflects the state change automatically.
 */
interface AuthRepository {
    /** Observation: the authoritative auth state as an owned [StateFlow]. */
    val authState: StateFlow<AuthState>

    suspend fun signInWithGoogle(idToken: GoogleIdToken): AuthState

    /**
     * Force-refresh the ID token via the auth provider and return it.
     *
     * Also updates [authState] with the new token so the UI stays in sync
     * (avoids split-brain where the UseCase has a fresh token but the
     * ViewModel's StateFlow still shows the old expiry).
     *
     * Returns null if refresh fails (e.g., user signed out, network error).
     */
    suspend fun refreshIdToken(): FirebaseIdToken?

    @Deprecated("Use refreshIdToken() instead", ReplaceWith("refreshIdToken()"))
    suspend fun refreshFirebaseAuthState(): AuthState

    suspend fun signOut()
}
