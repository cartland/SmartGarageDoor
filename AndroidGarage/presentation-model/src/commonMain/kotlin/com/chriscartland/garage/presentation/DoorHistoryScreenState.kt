package com.chriscartland.garage.presentation

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.LoadingResult

/**
 * Complete UI state for the Door History screen.
 */
data class DoorHistoryScreenState(
    val recentDoorEvents: LoadingResult<List<DoorEvent>> = LoadingResult.Loading(listOf()),
)
