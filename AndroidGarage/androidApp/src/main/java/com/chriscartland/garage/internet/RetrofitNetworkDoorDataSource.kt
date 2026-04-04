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

package com.chriscartland.garage.internet

import android.util.Log
import com.chriscartland.garage.data.NetworkDoorDataSource
import com.chriscartland.garage.domain.model.DoorEvent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

class RetrofitNetworkDoorDataSource
    @Inject
    constructor(
        private val network: GarageNetworkService,
    ) : NetworkDoorDataSource {
        override suspend fun fetchCurrentDoorEvent(buildTimestamp: String): DoorEvent? {
            try {
                val response = network.getCurrentEventData(
                    buildTimestamp = buildTimestamp,
                    session = null,
                )
                if (response.code() != 200) {
                    Log.e(TAG, "Response code is ${response.code()}")
                    return null
                }
                val body = response.body() ?: run {
                    Log.e(TAG, "Response body is null")
                    return null
                }
                return body.currentEventData?.currentEvent?.asDoorEvent().also {
                    if (it == null) Log.e(TAG, "Door event is null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching current door event: $e")
                return null
            }
        }

        override suspend fun fetchRecentDoorEvents(
            buildTimestamp: String,
            count: Int,
        ): List<DoorEvent>? {
            try {
                val response = network.getRecentEventData(
                    buildTimestamp = buildTimestamp,
                    session = null,
                    count = count,
                )
                if (response.code() != 200) {
                    Log.e(TAG, "Response code is ${response.code()}")
                    return null
                }
                val body = response.body() ?: run {
                    Log.e(TAG, "Response body is null")
                    return null
                }
                if (body.eventHistory.isNullOrEmpty()) {
                    Log.i(TAG, "recentEventData is empty")
                    return null
                }
                val doorEvents = body.eventHistory.mapNotNull {
                    it.currentEvent?.asDoorEvent()
                }
                if (doorEvents.size != body.eventHistory.size) {
                    Log.e(
                        TAG,
                        "Door events size ${doorEvents.size} " +
                            "does not match response size ${body.eventHistory.size}",
                    )
                }
                return doorEvents
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching recent door events: $e")
                return null
            }
        }
    }

@Module
@InstallIn(SingletonComponent::class)
object NetworkDoorDataSourceModule {
    @Provides
    @Singleton
    fun provideNetworkDoorDataSource(network: GarageNetworkService): NetworkDoorDataSource = RetrofitNetworkDoorDataSource(network)
}

private const val TAG = "RetrofitNetworkDoor"
