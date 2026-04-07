package com.chriscartland.garage.usecase.testfakes

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAuthRepository : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = _authState

    var signInResult: AuthState = AuthState.Authenticated(
        user = User(
            name = DisplayName("Test User"),
            email = Email("test@example.com"),
            idToken = FirebaseIdToken("fake-id-token", exp = 9999999999L),
        ),
    )

    override suspend fun signInWithGoogle(idToken: GoogleIdToken): AuthState {
        _authState.value = signInResult
        return signInResult
    }

    override suspend fun refreshFirebaseAuthState(): AuthState = _authState.value

    override suspend fun signOut() {
        _authState.value = AuthState.Unauthenticated
    }
}
