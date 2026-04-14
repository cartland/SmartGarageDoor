package com.chriscartland.garage.domain.repository

/**
 * Manages the garage door remote button press action.
 *
 * This is a one-shot action: call [pushButton], await the result.
 * Returns true if the server acknowledged the request, false on any failure.
 */
interface RemoteButtonRepository {
    suspend fun pushButton(
        idToken: String,
        buttonAckToken: String,
    ): Boolean
}
