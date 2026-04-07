package com.chriscartland.garage.domain.repository

import com.chriscartland.garage.domain.model.PushStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the garage door remote button press action.
 *
 * This is a one-shot action (send command to server), not persisted state.
 * The [pushButtonStatus] flow tracks the in-flight request status.
 */
interface RemoteButtonRepository {
    val pushButtonStatus: StateFlow<PushStatus>

    suspend fun pushButton(
        idToken: String,
        buttonAckToken: String,
    )
}
