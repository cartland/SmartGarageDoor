package com.chriscartland.garage.presentation

import com.chriscartland.garage.domain.model.SnoozeRequestStatus
import com.chriscartland.garage.domain.model.User

/**
 * Complete UI state for the Profile/Settings screen.
 *
 * Combines auth and snooze state into a single observable.
 */
data class ProfileScreenState(
    val user: User? = null,
    val snoozeEndTimeSeconds: Long = 0L,
    val snoozeRequestStatus: SnoozeRequestStatus = SnoozeRequestStatus.IDLE,
)
