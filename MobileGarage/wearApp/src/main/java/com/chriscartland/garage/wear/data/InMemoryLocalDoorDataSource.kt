/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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

package com.chriscartland.garage.wear.data

import com.chriscartland.garage.data.LocalDoorDataSource
import com.chriscartland.garage.domain.model.DoorEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [LocalDoorDataSource] for the Wear app.
 *
 * The watch shows live status only (no history screen, no offline cache),
 * so process-lifetime memory is enough — `:data-local`'s Room database is
 * deliberately not pulled in. Semantics mirror the Room-backed source:
 * newest event wins for `currentDoorEvent`; the recent list is kept
 * newest-first and deduped by (lastChangeTimeSeconds, doorPosition).
 */
class InMemoryLocalDoorDataSource : LocalDoorDataSource {
    private val _currentDoorEvent = MutableStateFlow<DoorEvent?>(null)
    private val _recentDoorEvents = MutableStateFlow<List<DoorEvent>>(emptyList())

    override val currentDoorEvent: Flow<DoorEvent?> = _currentDoorEvent
    override val recentDoorEvents: Flow<List<DoorEvent>> = _recentDoorEvents

    override suspend fun insertDoorEvent(doorEvent: DoorEvent) {
        _currentDoorEvent.value = doorEvent
    }

    override suspend fun replaceDoorEvents(doorEvents: List<DoorEvent>) {
        _recentDoorEvents.value = doorEvents
        if (doorEvents.isNotEmpty()) {
            _currentDoorEvent.value = doorEvents.first()
        }
    }

    override suspend fun appendDoorEvents(doorEvents: List<DoorEvent>) {
        _recentDoorEvents.value = (_recentDoorEvents.value + doorEvents)
            .distinctBy { it.lastChangeTimeSeconds to it.doorPosition }
            .sortedByDescending { it.lastChangeTimeSeconds ?: Long.MIN_VALUE }
    }
}
