package com.chriscartland.garage.domain.repository

import com.chriscartland.garage.domain.model.DoorFcmState
import com.chriscartland.garage.domain.model.DoorFcmTopic

interface DoorFcmRepository {
    suspend fun fetchStatus(): DoorFcmState

    suspend fun registerDoor(fcmTopic: DoorFcmTopic): DoorFcmState

    suspend fun deregisterDoor(): DoorFcmState
}
