package com.chriscartland.garage.domain.repository

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.GoogleIdToken
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>

    suspend fun signInWithGoogle(idToken: GoogleIdToken): AuthState

    suspend fun refreshFirebaseAuthState(): AuthState

    suspend fun signOut()
}
