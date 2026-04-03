package com.chriscartland.garage.domain.model

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

data class User(
    val name: DisplayName,
    val email: Email,
    val idToken: FirebaseIdToken,
)

sealed class AuthState {
    data object Unknown : AuthState()

    data object Unauthenticated : AuthState()

    data class Authenticated(
        val user: User,
    ) : AuthState()
}
