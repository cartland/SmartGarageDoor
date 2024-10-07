package com.chriscartland.garage.model

@JvmInline
value class IdToken(private val s: String) {
    fun asString(): String = s
}

@JvmInline
value class Email(private val s: String) {
    fun asString(): String = s
}

data class User(
    val idToken: IdToken,
    val email: Email,
)
