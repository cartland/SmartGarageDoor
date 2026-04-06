package com.chriscartland.garage.data

import com.chriscartland.garage.domain.model.DoorEvent

/**
 * Data source interface for fetching door events from the network.
 *
 * Returns [NetworkResult] so callers handle success, HTTP errors, and
 * connection failures with exhaustive `when`.
 */
interface NetworkDoorDataSource {
    suspend fun fetchCurrentDoorEvent(buildTimestamp: String): NetworkResult<DoorEvent>

    suspend fun fetchRecentDoorEvents(
        buildTimestamp: String,
        count: Int,
    ): NetworkResult<List<DoorEvent>>
}
