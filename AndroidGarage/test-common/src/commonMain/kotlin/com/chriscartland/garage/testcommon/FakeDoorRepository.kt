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

import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.FetchError
import com.chriscartland.garage.domain.model.PaginationState
import com.chriscartland.garage.domain.repository.DoorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeDoorRepository : DoorRepository {
    private val _currentDoorEvent = MutableStateFlow<DoorEvent?>(DoorEvent())
    private val _recentDoorEvents = MutableStateFlow<List<DoorEvent>>(emptyList())
    private val _currentDoorPosition = MutableStateFlow(DoorPosition.UNKNOWN)
    private val _paginationState = MutableStateFlow(PaginationState.Initial)

    override val currentDoorPosition: Flow<DoorPosition> = _currentDoorPosition
    override val currentDoorEvent: StateFlow<DoorEvent?> = _currentDoorEvent
    override val recentDoorEvents: StateFlow<List<DoorEvent>> = _recentDoorEvents
    override val paginationState: StateFlow<PaginationState> = _paginationState

    var fetchCurrentDoorEventCount = 0
        private set
    var fetchRecentDoorEventsCount = 0
        private set
    var fetchOlderDoorEventsCount = 0
        private set
    private var buildTimestamp: String? = "2024-01-15T00:00:00Z"

    // Configurable older page returned by fetchOlderDoorEvents().
    private var olderPage: List<DoorEvent> = emptyList()
    private var olderPageState: PaginationState = PaginationState.Initial

    fun setBuildTimestamp(value: String?) {
        buildTimestamp = value
    }

    fun setCurrentDoorEvent(event: DoorEvent) {
        _currentDoorEvent.value = event
        _currentDoorPosition.value = event.doorPosition ?: DoorPosition.UNKNOWN
    }

    fun setRecentDoorEvents(events: List<DoorEvent>) {
        _recentDoorEvents.value = events
    }

    fun setPaginationState(state: PaginationState) {
        _paginationState.value = state
    }

    /** Configure the events appended and the resulting pagination state on the next load-more. */
    fun setOlderPage(
        events: List<DoorEvent>,
        resultingState: PaginationState,
    ) {
        olderPage = events
        olderPageState = resultingState
    }

    override suspend fun fetchBuildTimestampCached(): String? = buildTimestamp

    override suspend fun insertDoorEvent(doorEvent: DoorEvent) {
        _currentDoorEvent.value = doorEvent
        _currentDoorPosition.value = doorEvent.doorPosition ?: DoorPosition.UNKNOWN
    }

    override suspend fun fetchCurrentDoorEvent(): AppResult<DoorEvent, FetchError> {
        fetchCurrentDoorEventCount++
        return _currentDoorEvent.value?.let { AppResult.Success(it) }
            ?: AppResult.Error(FetchError.NotReady)
    }

    override suspend fun fetchRecentDoorEvents(): AppResult<List<DoorEvent>, FetchError> {
        fetchRecentDoorEventsCount++
        return AppResult.Success(_recentDoorEvents.value)
    }

    override suspend fun fetchOlderDoorEvents(): AppResult<List<DoorEvent>, FetchError> {
        fetchOlderDoorEventsCount++
        if (_paginationState.value.nextPageToken == null) {
            return AppResult.Success(emptyList())
        }
        _recentDoorEvents.value = _recentDoorEvents.value + olderPage
        _paginationState.value = olderPageState
        return AppResult.Success(olderPage)
    }
}
