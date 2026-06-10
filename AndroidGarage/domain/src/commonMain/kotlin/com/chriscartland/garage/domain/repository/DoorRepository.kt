package com.chriscartland.garage.domain.repository

import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.FetchError
import com.chriscartland.garage.domain.model.PaginationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DoorRepository {
    /** Observation: current door position derived from latest event. */
    val currentDoorPosition: Flow<DoorPosition>

    /**
     * Observation: current door event owned as a [StateFlow] (ADR-022 —
     * state-y). Backed by an always-on collector over the local Room flow.
     */
    val currentDoorEvent: StateFlow<DoorEvent?>

    /**
     * Observation: recent door events owned as a [StateFlow] (ADR-022 —
     * state-y). Backed by an always-on collector over the local Room flow,
     * same pattern as [currentDoorEvent]. Exposed as [StateFlow] so
     * `DoorHistoryViewModel` can synchronously seed its initial loading-
     * result with the cached events list (avoiding a one-frame
     * `Loading(emptyList())` render on every fresh screen entry).
     */
    val recentDoorEvents: StateFlow<List<DoorEvent>>

    /**
     * Observation: pagination cursor for [recentDoorEvents] (ADR-022 — state-y,
     * repo-owned). Drives the history screen's "load more" affordance.
     */
    val paginationState: StateFlow<PaginationState>

    suspend fun fetchBuildTimestampCached(): String?

    suspend fun insertDoorEvent(doorEvent: DoorEvent)

    /** One-time request: fetch current door event from server and cache locally. */
    suspend fun fetchCurrentDoorEvent(): AppResult<DoorEvent, FetchError>

    /**
     * One-time request: fetch the first page of recent door events (windowed,
     * server-capped) and REPLACE the cache. Resets pagination state.
     */
    suspend fun fetchRecentDoorEvents(): AppResult<List<DoorEvent>, FetchError>

    /**
     * One-time request: fetch the next OLDER page using the stored token and
     * APPEND to the cache. No-op (Success with empty list) when there is nothing
     * more to load or a load is already in flight.
     */
    suspend fun fetchOlderDoorEvents(): AppResult<List<DoorEvent>, FetchError>
}
