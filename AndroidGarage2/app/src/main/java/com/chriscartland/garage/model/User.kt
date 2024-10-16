package com.chriscartland.garage.model

@JvmInline
value class Email(private val s: String) {
    fun asString(): String = s
}

@JvmInline
value class DisplayName(private val s: String) {
    fun asString(): String = s
}

@JvmInline
value class FirebaseIdToken(private val s: String) {
    fun asString(): String = s
}

data class User(
    val name: DisplayName,
    val email: Email,
    val idToken: FirebaseIdToken,
)