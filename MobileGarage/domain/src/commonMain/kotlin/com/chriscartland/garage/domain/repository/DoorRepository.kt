package com.chriscartland.garage.domain.repository

import com.chriscartland.garage.domain.graph.DataGraph.Cadence
import com.chriscartland.garage.domain.graph.NodeCadence
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.FetchError
import com.chriscartland.garage.domain.model.PaginationState
import kotlinx.coroutines.flow.StateFlow

interface DoorRepository {
    /**
     * Observation: current door event owned as a [StateFlow] (ADR-022 —
     * state-y). Backed by an always-on collector over the local Room flow.
     *
     * There is deliberately NO `currentDoorPosition` sibling: position is a
     * pure projection of this node (`doorPosition ?: UNKNOWN`), and a second
     * repo flow of the same row would be a second root for the graph's G7
     * rule to chase. `ObserveDoorEventsUseCase.position()` owns the map.
     */
    @NodeCadence(Cadence.PUSH)
    val currentDoorEvent: StateFlow<DoorEvent?>

    /**
     * Observation: how many attempts IN A ROW to hear the current door from
     * the server have failed (ADR-022 — state-y, repo-owned). Zero means the
     * last attempt succeeded, or a push landed since; `> 0` is "the last
     * attempt failed". A count rather than a flag because how many failures
     * have piled up is what separates a blip from data that is unhealthy
     * (`DoorDataHealthManager`).
     *
     * ONE memory for the whole process, on purpose. "We could not reach the
     * server" is a fact about the app, not about whichever surface happened
     * to ask — yet until 2026-10-03 it was remembered five times over (the
     * watch screen, the watch's tile and complication, the phone widget per
     * render, the Siri intent per ask), so the watch could grey its dial over
     * a failed poll while its own tile, drawn a second later, presented the
     * same door as confirmed. Whoever asks now writes the answer here, and
     * every surface reads it.
     *
     * Goes up by one each time [fetchCurrentDoorEvent] returns an error, by
     * any caller. Back to zero when one succeeds, and after [insertDoorEvent]:
     * a reading the server pushed to us is as confirmed as a reading gets,
     * whatever the last request did. Seeded zero — a process that has not
     * asked yet is un-asked, not failed, and the check-in's age is what says
     * how much the cached reading is worth.
     *
     * PUSH, because a push can clear it at any time.
     */
    @NodeCadence(Cadence.PUSH)
    val currentDoorFetchFailures: StateFlow<Int>

    /**
     * Observation: recent door events owned as a [StateFlow] (ADR-022 —
     * state-y). Backed by an always-on collector over the local Room flow,
     * same pattern as [currentDoorEvent]. Exposed as [StateFlow] so
     * `DoorHistoryViewModel` can synchronously seed its initial loading-
     * result with the cached events list (avoiding a one-frame
     * `Loading(emptyList())` render on every fresh screen entry).
     */
    @NodeCadence(Cadence.PUSH)
    val recentDoorEvents: StateFlow<List<DoorEvent>>

    /**
     * Observation: pagination cursor for [recentDoorEvents] (ADR-022 — state-y,
     * repo-owned). Drives the history screen's "load more" affordance.
     * USER_ACTION: it changes only when a fetch the app initiated completes
     * (initial page or an explicit "load more"), never behind the app's back.
     */
    @NodeCadence(Cadence.USER_ACTION)
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
