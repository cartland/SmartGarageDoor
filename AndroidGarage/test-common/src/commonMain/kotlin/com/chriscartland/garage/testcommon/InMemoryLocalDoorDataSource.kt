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

import com.chriscartland.garage.data.LocalDoorDataSource
import com.chriscartland.garage.domain.model.DoorEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [LocalDoorDataSource] suitable for tests and lightweight production scenarios.
 *
 * Insert and replace calls are recorded via `insertCalls` / `replaceCalls`
 * (ADR-017 Rule 5 — call-list pattern), so tests can assert on the exact
 * `DoorEvent` arguments passed. The `*Count` accessors are convenience reads
 * backed by the lists.
 */
class InMemoryLocalDoorDataSource : LocalDoorDataSource {
    private val _currentDoorEvent = MutableStateFlow<DoorEvent?>(null)
    private val _recentDoorEvents = MutableStateFlow<List<DoorEvent>>(emptyList())

    override val currentDoorEvent: Flow<DoorEvent?> = _currentDoorEvent
    override val recentDoorEvents: Flow<List<DoorEvent>> = _recentDoorEvents

    private val _insertCalls = mutableListOf<DoorEvent>()
    val insertCalls: List<DoorEvent> get() = _insertCalls
    val insertCount: Int get() = _insertCalls.size

    private val _replaceCalls = mutableListOf<List<DoorEvent>>()
    val replaceCalls: List<List<DoorEvent>> get() = _replaceCalls
    val replaceCount: Int get() = _replaceCalls.size

    override suspend fun insertDoorEvent(doorEvent: DoorEvent) {
        _insertCalls.add(doorEvent)
        _currentDoorEvent.value = doorEvent
    }

    override suspend fun replaceDoorEvents(doorEvents: List<DoorEvent>) {
        _replaceCalls.add(doorEvents)
        _recentDoorEvents.value = doorEvents
        if (doorEvents.isNotEmpty()) {
            _currentDoorEvent.value = doorEvents.first()
        }
    }
}
