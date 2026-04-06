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

package com.chriscartland.garage.door

import co.touchlab.kermit.Logger
import com.chriscartland.garage.config.APP_CONFIG
import com.chriscartland.garage.config.ServerConfigRepository
import com.chriscartland.garage.data.LocalDoorDataSource
import com.chriscartland.garage.data.NetworkDoorDataSource
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.repository.DoorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DoorRepositoryImpl(
    private val localDoorDataSource: LocalDoorDataSource,
    private val networkDoorDataSource: NetworkDoorDataSource,
    private val serverConfigRepository: ServerConfigRepository,
) : DoorRepository {
    override val currentDoorPosition: Flow<DoorPosition>
        get() =
            localDoorDataSource.currentDoorEvent
                .map {
                    it?.doorPosition ?: DoorPosition.UNKNOWN
                }.distinctUntilChanged()
    override val currentDoorEvent: Flow<DoorEvent?> = localDoorDataSource.currentDoorEvent
    override val recentDoorEvents: Flow<List<DoorEvent>> = localDoorDataSource.recentDoorEvents

    override suspend fun fetchBuildTimestampCached(): String? = serverConfigRepository.getServerConfigCached()?.buildTimestamp

    override fun insertDoorEvent(doorEvent: DoorEvent) {
        Logger.d { "Inserting DoorEvent: $doorEvent" }
        localDoorDataSource.insertDoorEvent(doorEvent)
    }

    override suspend fun fetchCurrentDoorEvent() {
        val buildTimestamp = fetchBuildTimestampCached()
        if (buildTimestamp == null) {
            Logger.e { "Server config is null" }
            return
        }
        val doorEvent = networkDoorDataSource.fetchCurrentDoorEvent(buildTimestamp)
        if (doorEvent == null) {
            Logger.e { "Failed to fetch current door event" }
            return
        }
        Logger.d { "Success: $doorEvent" }
        localDoorDataSource.insertDoorEvent(doorEvent)
    }

    override suspend fun fetchRecentDoorEvents() {
        val buildTimestamp = fetchBuildTimestampCached()
        if (buildTimestamp == null) {
            Logger.e { "Server config is null" }
            return
        }
        val doorEvents = networkDoorDataSource.fetchRecentDoorEvents(
            buildTimestamp = buildTimestamp,
            count = APP_CONFIG.recentEventCount,
        )
        if (doorEvents == null) {
            Logger.e { "Failed to fetch recent door events" }
            return
        }
        Logger.d { "Success: $doorEvents" }
        localDoorDataSource.replaceDoorEvents(doorEvents)
    }
}
