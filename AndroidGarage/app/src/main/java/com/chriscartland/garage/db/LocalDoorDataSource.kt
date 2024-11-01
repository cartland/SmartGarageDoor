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

package com.chriscartland.garage.db

import android.util.Log
import com.chriscartland.garage.door.DoorEvent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface LocalDoorDataSource {
    val currentDoorEvent: Flow<DoorEvent>
    val recentDoorEvents: Flow<List<DoorEvent>>
    fun insertDoorEvent(doorEvent: DoorEvent)
    fun replaceDoorEvents(doorEvents: List<DoorEvent>)
}

class DatabaseLocalDoorDataSource @Inject constructor(
    private val appDatabase: AppDatabase,
) : LocalDoorDataSource {
    override val currentDoorEvent = appDatabase.doorEventDao().currentDoorEvent()
    override val recentDoorEvents = appDatabase.doorEventDao().recentDoorEvents()

    override fun insertDoorEvent(doorEvent: DoorEvent) {
        Log.d(TAG, "Inserting DoorEvent: $doorEvent")
        appDatabase.doorEventDao().insert(doorEvent)
    }

    override fun replaceDoorEvents(doorEvents: List<DoorEvent>) {
        appDatabase.doorEventDao().replaceAll(doorEvents)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object LocalDataSourceModule {
    @Provides
    @Singleton
    fun provideLocalDataSource(appDatabase: AppDatabase): LocalDoorDataSource {
        return DatabaseLocalDoorDataSource(appDatabase)
    }
}

private const val TAG = "LocalDoorDataSource"
