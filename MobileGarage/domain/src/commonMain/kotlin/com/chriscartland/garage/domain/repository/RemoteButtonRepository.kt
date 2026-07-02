package com.chriscartland.garage.domain.repository

/**
 * Manages the garage door remote button press action.
 *
 * This is a one-shot action: call [pushButton], await the result.
 * Returns true if the server acknowledged the request, false on any failure.
 *
 * Per ADR-027 the implementation fetches the current Firebase ID token
 * itself via [com.chriscartland.garage.domain.repository.AuthRepository.getIdToken];
 * callers do not pass a token.
 */
interface RemoteButtonRepository {
    suspend fun pushButton(buttonAckToken: String): Boolean
}
