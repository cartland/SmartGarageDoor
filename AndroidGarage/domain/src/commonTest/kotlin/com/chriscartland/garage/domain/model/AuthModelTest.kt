package com.chriscartland.garage.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthModelTest {
    @Test
    fun authenticatedStateHoldsUser() {
        // ADR-027: User carries identity only, no token.
        val user = User(
            name = DisplayName("Test User"),
            email = Email("test@example.com"),
        )
        val state = AuthState.Authenticated(user)
        assertEquals("Test User", state.user.name.asString())
        assertEquals("test@example.com", state.user.email.asString())
    }

    @Test
    fun authStateSubtypesAreDistinct() {
        val unknown: AuthState = AuthState.Unknown
        val unauth: AuthState = AuthState.Unauthenticated
        val auth: AuthState = AuthState.Authenticated(
            User(
                name = DisplayName(""),
                email = Email(""),
            ),
        )
        assertTrue(unknown is AuthState.Unknown)
        assertTrue(unauth is AuthState.Unauthenticated)
        assertTrue(auth is AuthState.Authenticated)
    }

    @Test
    fun firebaseIdTokenExpiration() {
        val token = FirebaseIdToken(idToken = "abc", exp = 1000L)
        assertEquals(1000L, token.exp)
        assertEquals("abc", token.asString())
    }
}
