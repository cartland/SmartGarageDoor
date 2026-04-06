package com.chriscartland.garage.domain.repository

import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.FetchError
import kotlinx.coroutines.flow.Flow

interface DoorRepository {
    /** Observation: current door position derived from latest event. */
    val currentDoorPosition: Flow<DoorPosition>

    /** Observation: current door event from local cache. */
    val currentDoorEvent: Flow<DoorEvent?>

    /** Observation: recent door events from local cache. */
    val recentDoorEvents: Flow<List<DoorEvent>>

    suspend fun fetchBuildTimestampCached(): String?

    fun insertDoorEvent(doorEvent: DoorEvent)

    /** One-time request: fetch current door event from server and cache locally. */
    suspend fun fetchCurrentDoorEvent(): AppResult<DoorEvent, FetchError>

    /** One-time request: fetch recent door events from server and cache locally. */
    suspend fun fetchRecentDoorEvents(): AppResult<List<DoorEvent>, FetchError>
}
