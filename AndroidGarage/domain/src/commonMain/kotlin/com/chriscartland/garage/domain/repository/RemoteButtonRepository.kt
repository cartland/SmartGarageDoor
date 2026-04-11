package com.chriscartland.garage.domain.repository

/**
 * Manages the garage door remote button press action.
 */
interface RemoteButtonRepository {
    suspend fun pushButton(
        idToken: String,
        buttonAckToken: String,
    )
}
