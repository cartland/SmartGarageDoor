/*
 * Copyright 2021 Chris Cartland. All rights reserved.
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

package com.chriscartland.garage.datalocal

import co.touchlab.kermit.Logger
import com.chriscartland.garage.data.LocalDoorDataSource
import com.chriscartland.garage.domain.model.DoorEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DatabaseLocalDoorDataSource(
    private val appDatabase: AppDatabase,
) : LocalDoorDataSource {
    override val currentDoorEvent: Flow<DoorEvent?> =
        appDatabase.doorEventDao().currentDoorEvent().map { it?.toDomain() }
    override val recentDoorEvents: Flow<List<DoorEvent>> =
        appDatabase.doorEventDao().recentDoorEvents().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun insertDoorEvent(doorEvent: DoorEvent) {
        Logger.d { "Inserting DoorEvent: $doorEvent" }
        appDatabase.doorEventDao().insert(doorEvent.toEntity())
    }

    override suspend fun replaceDoorEvents(doorEvents: List<DoorEvent>) {
        Logger.d { "Replacing DoorEvents: $doorEvents" }
        appDatabase.doorEventDao().replaceAll(doorEvents.map { it.toEntity() })
    }
}
