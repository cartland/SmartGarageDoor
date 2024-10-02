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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.chriscartland.garage.model.DoorEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface DoorEventDao {
    @Query("SELECT * FROM doorEvent ORDER BY lastChangeTimeSeconds DESC LIMIT 1")
    fun currentDoorEvent(): Flow<DoorEvent>

    @Query("SELECT * FROM doorEvent ORDER BY lastChangeTimeSeconds DESC")
    fun recentDoorEvents(): Flow<List<DoorEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(doorEvent: DoorEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertList(doorEvents: List<DoorEvent>)

    @Transaction
    fun replaceAll(doorEvents: List<DoorEvent>) {
        deleteAll()
        insertList(doorEvents)
    }

    @Query("DELETE FROM doorEvent")
    fun deleteAll()
}
