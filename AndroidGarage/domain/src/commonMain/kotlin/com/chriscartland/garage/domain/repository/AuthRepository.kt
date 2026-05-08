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
 *
 * Per ADR-027, the ID token is a *private* concern of this repository
 * (and the network repositories that legitimately need it). [authState]
 * carries identity only (`User.name`, `User.email`) — never a token.
 * Token refreshes do NOT cause [authState] to re-emit; only sign-in,
 * sign-out, and account changes do.
 *
 * Callers that need a token call [getIdToken] explicitly. UseCases must
 * not — token plumbing is contained at the repository layer.
 */
interface AuthRepository {
    /** Observation: identity-only auth state as an owned [StateFlow]. */
    val authState: StateFlow<AuthState>

    /**
     * Fetch the current ID token. Pass `forceRefresh = true` to bypass
     * the platform's token cache and force a network round-trip; pass
     * `false` to use the cached token if available.
     *
     * Returns null if the user is not signed in or the token request
     * failed (network error, sign-out race).
     *
     * Authoritative for token consumers. Network repositories call this
     * before delegating to a data source; UseCases must not.
     */
    suspend fun getIdToken(forceRefresh: Boolean): FirebaseIdToken?

    suspend fun signInWithGoogle(idToken: GoogleIdToken): AuthState

    suspend fun signOut()
}
