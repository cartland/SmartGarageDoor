package com.chriscartland.garage.data

import com.chriscartland.garage.domain.model.DoorEvent
import kotlinx.coroutines.flow.Flow

/**
 * Pure data source interface for local door event storage.
 *
 * Implementations handle persistence (Room, in-memory, etc.)
 * without leaking implementation details to the Repository layer.
 */
interface LocalDoorDataSource {
    val currentDoorEvent: Flow<DoorEvent?>
    val recentDoorEvents: Flow<List<DoorEvent>>

    suspend fun insertDoorEvent(doorEvent: DoorEvent)

    /** Replace the whole cache (initial load / pull-to-refresh). */
    suspend fun replaceDoorEvents(doorEvents: List<DoorEvent>)

    /**
     * Append older events to the cache without clearing it (pagination load-more).
     * Page-boundary overlap is de-duplicated by primary key.
     */
    suspend fun appendDoorEvents(doorEvents: List<DoorEvent>)
}
