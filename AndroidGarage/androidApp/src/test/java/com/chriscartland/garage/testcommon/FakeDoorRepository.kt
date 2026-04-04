/*
 * Copyright 2024 Chris Cartland. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.chriscartland.garage.testcommon

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.door.DoorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeDoorRepository : DoorRepository {
    private val _currentDoorEvent = MutableStateFlow(DoorEvent())
    private val _recentDoorEvents = MutableStateFlow<List<DoorEvent>>(emptyList())
    private val _currentDoorPosition = MutableStateFlow(DoorPosition.UNKNOWN)

    override val currentDoorPosition: Flow<DoorPosition> = _currentDoorPosition
    override val currentDoorEvent: Flow<DoorEvent> = _currentDoorEvent
    override val recentDoorEvents: Flow<List<DoorEvent>> = _recentDoorEvents

    var fetchCurrentDoorEventCount = 0
        private set
    var fetchRecentDoorEventsCount = 0
        private set
    var buildTimestamp: String? = "2024-01-15T00:00:00Z"

    fun setCurrentDoorEvent(event: DoorEvent) {
        _currentDoorEvent.value = event
        _currentDoorPosition.value = event.doorPosition ?: DoorPosition.UNKNOWN
    }

    fun setRecentDoorEvents(events: List<DoorEvent>) {
        _recentDoorEvents.value = events
    }

    override suspend fun fetchBuildTimestampCached(): String? = buildTimestamp

    override fun insertDoorEvent(doorEvent: DoorEvent) {
        _currentDoorEvent.value = doorEvent
        _currentDoorPosition.value = doorEvent.doorPosition ?: DoorPosition.UNKNOWN
    }

    override suspend fun fetchCurrentDoorEvent() {
        fetchCurrentDoorEventCount++
    }

    override suspend fun fetchRecentDoorEvents() {
        fetchRecentDoorEventsCount++
    }
}
