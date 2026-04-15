package com.chriscartland.garage.domain.repository

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.GoogleIdToken
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** One-shot: current auth state right now. */
    suspend fun getAuthState(): AuthState

    /** Observation: auth state changes over time. */
    fun observeAuthState(): Flow<AuthState>

    suspend fun signInWithGoogle(idToken: GoogleIdToken): AuthState

    suspend fun refreshFirebaseAuthState(): AuthState

    suspend fun signOut()
}
