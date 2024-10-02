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

import com.chriscartland.garage.model.DoorEvent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface LocalDataSource {
    val currentDoorEvent: Flow<DoorEvent>
    val recentDoorEvents: Flow<List<DoorEvent>>
    fun insertDoorEvent(doorEvent: DoorEvent)
    fun insertDoorEvents(doorEvents: List<DoorEvent>)
}

class DatabaseLocalDataSource @Inject constructor(
    private val appDatabase: AppDatabase,
) : LocalDataSource {
    override val currentDoorEvent = appDatabase.doorEventDao().currentDoorEvent()
    override val recentDoorEvents = appDatabase.doorEventDao().recentDoorEvents()

    override fun insertDoorEvent(doorEvent: DoorEvent) {
        appDatabase.doorEventDao().insert(doorEvent)
    }

    override fun insertDoorEvents(doorEvents: List<DoorEvent>) {
        appDatabase.doorEventDao().insertList(doorEvents)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object LocalDataSourceModule {
    @Provides
    @Singleton
    fun provideLocalDataSource(appDatabase: AppDatabase): LocalDataSource {
        return DatabaseLocalDataSource(appDatabase)
    }
}
