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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DoorEventDao {
    @Query("SELECT * FROM DoorEvent ORDER BY lastChangeTimeSeconds DESC LIMIT 1")
    fun currentDoorEvent(): Flow<DoorEventEntity?>

    @Query("SELECT * FROM DoorEvent ORDER BY lastChangeTimeSeconds DESC")
    fun recentDoorEvents(): Flow<List<DoorEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doorEvent: DoorEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(doorEvents: List<DoorEventEntity>)

    @Transaction
    suspend fun replaceAll(doorEvents: List<DoorEventEntity>) {
        deleteAll()
        insertList(doorEvents)
    }

    @Query("DELETE FROM DoorEvent")
    suspend fun deleteAll()
}
