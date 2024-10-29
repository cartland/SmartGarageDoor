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

import android.util.Log
import com.chriscartland.garage.config.APP_CONFIG
import com.chriscartland.garage.config.ServerConfigRepository
import com.chriscartland.garage.db.LocalDoorDataSource
import com.chriscartland.garage.internet.GarageNetworkService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface DoorRepository {
    // Only exposes the door position.
    val currentDoorPosition: Flow<DoorPosition>
    // Exposes the event, including last updated timestamps.
    val currentDoorEvent: Flow<DoorEvent>
    val recentDoorEvents: Flow<List<DoorEvent>>
    suspend fun fetchBuildTimestampCached(): String?
    suspend fun insertDoorEvent(doorEvent: DoorEvent)
    suspend fun fetchCurrentDoorEvent()
    suspend fun fetchRecentDoorEvents()
}

class DoorRepositoryImpl @Inject constructor(
    private val localDoorDataSource: LocalDoorDataSource,
    private val network: GarageNetworkService,
    private val serverConfigRepository: ServerConfigRepository,
) : DoorRepository {
    override val currentDoorPosition: Flow<DoorPosition>
        get() = localDoorDataSource.currentDoorEvent
            .map {
                // [it] can be null -- this might be a Kotlin bug
                it?.doorPosition ?: DoorPosition.UNKNOWN
            }
            .distinctUntilChanged()
    override val currentDoorEvent: Flow<DoorEvent> = localDoorDataSource.currentDoorEvent
    override val recentDoorEvents: Flow<List<DoorEvent>> = localDoorDataSource.recentDoorEvents

    override suspend fun fetchBuildTimestampCached(): String? =
        serverConfigRepository.getServerConfigCached()?.buildTimestamp

    override suspend fun insertDoorEvent(doorEvent: DoorEvent) {
        Log.d("insertDoorEvent", "Inserting door event: $doorEvent")
        localDoorDataSource.insertDoorEvent(doorEvent)
    }

    /**
     * Fetch current door event.
     */
    override suspend fun fetchCurrentDoorEvent() {
        val buildTimestamp = fetchBuildTimestampCached()
        if (buildTimestamp == null) {
            Log.e(TAG, "Server config is null")
            return
        }
        try {
            Log.d(TAG, "Fetching current door event")
            val response = network.getCurrentEventData(
                buildTimestamp = buildTimestamp,
                session = null,
            )
            if (response.code() != 200) {
                Log.e(TAG, "Response code is ${response.code()}")
                return
            }
            val body = response.body()
            if (body == null) {
                Log.e(TAG, "Response body is null")
                return
            }
            Log.d(TAG, "Response: $response")
            if (body.currentEventData == null) {
                Log.e(TAG, "currentEventData is null")
            } else if (body.currentEventData.currentEvent == null) {
                Log.e(TAG, "currentEvent is null")
            }
            val doorEvent = body.currentEventData?.currentEvent?.asDoorEvent()
            if (doorEvent == null) {
                Log.e(TAG, "Door event is null")
                return
            }
            Log.d(TAG, "Success: $doorEvent")
            localDoorDataSource.insertDoorEvent(doorEvent)
        } catch (e: Exception) {
            Log.e(TAG, "Error: $e")
        }
    }

    /**
     * Fetch recent door events.
     */
    override suspend fun fetchRecentDoorEvents() {
        val buildTimestamp = fetchBuildTimestampCached()
        if (buildTimestamp == null) {
            Log.e(TAG, "Server config is null")
            return
        }
        try {
            Log.d(TAG, "Fetching recent door events")
            val response = network.getRecentEventData(
                buildTimestamp = buildTimestamp,
                session = null,
                count = APP_CONFIG.recentEventCount,
            )
            if (response.code() != 200) {
                Log.e(TAG, "Response code is ${response.code()}")
                return
            }
            val body = response.body()
            if (body == null) {
                Log.e(TAG, "Response body is null")
                return
            }
            Log.d(TAG, "Response: $response")
            if (body.eventHistory.isNullOrEmpty()) {
                Log.i(TAG, "recentEventData is empty")
                return
            }
            val doorEvents = body.eventHistory.map {
                it.currentEvent?.asDoorEvent()
            }.filterNotNull()
            if (doorEvents.size != body.eventHistory.size) {
                Log.e(
                    TAG,
                    "Door events size ${doorEvents.size} " +
                            "does not match response size ${body.eventHistory.size}"
                )
            }
            Log.d(TAG, "Success: $doorEvents")
            localDoorDataSource.replaceDoorEvents(doorEvents)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "IllegalArgumentException: $e")
        } catch (e: Exception) {
            Log.e(TAG, "Exception: $e")
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class GarageRepositoryModule {
    @Binds
    abstract fun bindGarageRepository(doorRepositoryImpl: DoorRepositoryImpl): DoorRepository
}

private const val TAG = "DoorRepository"
