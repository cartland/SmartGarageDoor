package com.chriscartland.garage.domain.repository

import com.chriscartland.garage.domain.model.SnoozeRequestStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages snooze-notification state for the garage door.
 *
 * Snoozing suppresses open-door notifications for a specified duration.
 * The snooze end time is persisted server-side and fetched on demand.
 */
interface SnoozeRepository {
    val snoozeRequestStatus: StateFlow<SnoozeRequestStatus>
    val snoozeEndTimeSeconds: StateFlow<Long>

    suspend fun fetchSnoozeEndTimeSeconds()

    suspend fun snoozeNotifications(
        snoozeDurationHours: String,
        idToken: String,
        snoozeEventTimestampSeconds: Long,
    )
}
