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

class InMemoryLocalDoorDataSource : LocalDoorDataSource {
    private val _currentDoorEvent = MutableStateFlow<DoorEvent?>(null)
    private val _recentDoorEvents = MutableStateFlow<List<DoorEvent>>(emptyList())

    override val currentDoorEvent: Flow<DoorEvent?> = _currentDoorEvent
    override val recentDoorEvents: Flow<List<DoorEvent>> = _recentDoorEvents

    var insertCount = 0
        private set
    var replaceCount = 0
        private set

    override fun insertDoorEvent(doorEvent: DoorEvent) {
        insertCount++
        _currentDoorEvent.value = doorEvent
    }

    override fun replaceDoorEvents(doorEvents: List<DoorEvent>) {
        replaceCount++
        _recentDoorEvents.value = doorEvents
        if (doorEvents.isNotEmpty()) {
            _currentDoorEvent.value = doorEvents.first()
        }
    }
}
