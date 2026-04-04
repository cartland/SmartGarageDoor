package com.chriscartland.garage.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthModelTest {

    @Test
    fun authenticatedStateHoldsUser() {
        val user = User(
            name = DisplayName("Test User"),
            email = Email("test@example.com"),
            idToken = FirebaseIdToken(idToken = "token123", exp = 9999999L),
        )
        val state = AuthState.Authenticated(user)
        assertEquals("Test User", state.user.name.asString())
        assertEquals("test@example.com", state.user.email.asString())
        assertEquals("token123", state.user.idToken.asString())
    }

    @Test
    fun authStateSubtypesAreDistinct() {
        val unknown: AuthState = AuthState.Unknown
        val unauth: AuthState = AuthState.Unauthenticated
        val auth: AuthState = AuthState.Authenticated(
            User(
                name = DisplayName(""),
                email = Email(""),
                idToken = FirebaseIdToken("", 0L),
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
