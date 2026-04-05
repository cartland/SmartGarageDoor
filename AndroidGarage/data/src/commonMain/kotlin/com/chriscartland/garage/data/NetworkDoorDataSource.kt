package com.chriscartland.garage.data

import com.chriscartland.garage.domain.model.DoorEvent

/**
 * Pure data source interface for fetching door events from the network.
 *
 * Implementations handle HTTP communication (Retrofit, Ktor, etc.)
 * and map network response DTOs to domain types. The Repository layer
 * never sees HTTP response codes, network DTOs, or serialization details.
 */
interface NetworkDoorDataSource {
    /**
     * Fetch the current door event from the server.
     *
     * @param buildTimestamp Server build timestamp for the API request
     * @return The current door event, or null if the request failed
     */
    suspend fun fetchCurrentDoorEvent(buildTimestamp: String): DoorEvent?

    /**
     * Fetch recent door events from the server.
     *
     * @param buildTimestamp Server build timestamp for the API request
     * @param count Maximum number of events to return
     * @return List of door events, or null if the request failed
     */
    suspend fun fetchRecentDoorEvents(
        buildTimestamp: String,
        count: Int,
    ): List<DoorEvent>?
}
