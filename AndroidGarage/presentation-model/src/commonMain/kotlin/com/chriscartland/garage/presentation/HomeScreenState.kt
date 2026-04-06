package com.chriscartland.garage.presentation

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.model.RequestStatus

/**
 * Complete UI state for the Home screen.
 *
 * Combines multiple domain flows into a single observable state,
 * making the UI a pure function of this state object.
 * Shareable across platforms (Android Compose, iOS SwiftUI).
 */
data class HomeScreenState(
    val currentDoorEvent: LoadingResult<DoorEvent?> = LoadingResult.Loading(null),
    val remoteRequestStatus: RequestStatus = RequestStatus.NONE,
    val authState: AuthState = AuthState.Unknown,
)
