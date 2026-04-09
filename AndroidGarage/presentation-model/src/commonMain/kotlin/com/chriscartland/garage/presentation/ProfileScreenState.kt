package com.chriscartland.garage.presentation

import com.chriscartland.garage.domain.model.SnoozeAction
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.model.User

/**
 * Complete UI state for the Profile/Settings screen.
 *
 * Combines auth and snooze state into a single observable.
 */
data class ProfileScreenState(
    val user: User? = null,
    val snoozeState: SnoozeState = SnoozeState.Loading,
    val snoozeAction: SnoozeAction = SnoozeAction.Idle,
)
