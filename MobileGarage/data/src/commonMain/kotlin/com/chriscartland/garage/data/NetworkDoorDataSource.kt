package com.chriscartland.garage.data

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorEventPage

/**
 * Data source interface for fetching door events from the network.
 *
 * Returns [NetworkResult] so callers handle success, HTTP errors, and
 * connection failures with exhaustive `when`.
 */
interface NetworkDoorDataSource {
    suspend fun fetchCurrentDoorEvent(buildTimestamp: String): NetworkResult<DoorEvent>

    /**
     * Fetch one page of door history. [pageToken] null = the windowed first page
     * (last 7 days, server-capped at 50); a non-null token pages further into the
     * past. Returns the events plus the opaque next/prev tokens.
     */
    suspend fun fetchDoorEventPage(
        buildTimestamp: String,
        pageSize: Int,
        pageToken: String?,
    ): NetworkResult<DoorEventPage>
}
