package com.chriscartland.garage.domain.repository

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import kotlinx.coroutines.flow.Flow

interface DoorRepository {
    val currentDoorPosition: Flow<DoorPosition>
    val currentDoorEvent: Flow<DoorEvent>
    val recentDoorEvents: Flow<List<DoorEvent>>

    suspend fun fetchBuildTimestampCached(): String?

    fun insertDoorEvent(doorEvent: DoorEvent)

    suspend fun fetchCurrentDoorEvent()

    suspend fun fetchRecentDoorEvents()
}
