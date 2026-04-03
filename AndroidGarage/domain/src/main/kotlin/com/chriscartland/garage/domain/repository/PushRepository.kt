package com.chriscartland.garage.domain.repository

import com.chriscartland.garage.domain.model.PushStatus
import com.chriscartland.garage.domain.model.SnoozeRequestStatus
import kotlinx.coroutines.flow.StateFlow

interface PushRepository {
    val pushButtonStatus: StateFlow<PushStatus>
    val snoozeRequestStatus: StateFlow<SnoozeRequestStatus>
    val snoozeEndTimeSeconds: StateFlow<Long>

    suspend fun push(
        idToken: String,
        buttonAckToken: String,
    )

    suspend fun fetchSnoozeEndTimeSeconds()

    suspend fun snoozeOpenDoorsNotifications(
        snoozeDurationHours: String,
        idToken: String,
        snoozeEventTimestampSeconds: Long,
    )
}
