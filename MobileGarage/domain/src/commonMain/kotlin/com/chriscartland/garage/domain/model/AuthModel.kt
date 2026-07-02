package com.chriscartland.garage.domain.model

import kotlin.jvm.JvmInline

@JvmInline
value class Email(
    private val s: String,
) {
    fun asString(): String = s
}

@JvmInline
value class DisplayName(
    private val s: String,
) {
    fun asString(): String = s
}

data class FirebaseIdToken(
    val idToken: String,
    val exp: Long,
) {
    fun asString(): String = idToken
}

@JvmInline
value class GoogleIdToken(
    private val s: String,
) {
    fun asString(): String = s
}

/**
 * User identity. Does NOT carry an ID token — the token is a private
 * concern of [com.chriscartland.garage.domain.repository.AuthRepository]
 * (see ADR-027). Components that need a token call
 * `AuthRepository.getIdToken(forceRefresh)` explicitly; UseCases
 * never touch a token.
 */
data class User(
    val name: DisplayName,
    val email: Email,
)

sealed class AuthState {
    data object Unknown : AuthState()

    data object Unauthenticated : AuthState()

    data class Authenticated(
        val user: User,
    ) : AuthState()
}
