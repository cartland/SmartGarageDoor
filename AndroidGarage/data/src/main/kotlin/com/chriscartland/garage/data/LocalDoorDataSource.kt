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

    fun insertDoorEvent(doorEvent: DoorEvent)

    fun replaceDoorEvents(doorEvents: List<DoorEvent>)
}
