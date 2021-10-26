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

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chriscartland.garage.model.DoorData

@Dao
interface DoorDataDao {
    @Query("SELECT * FROM doordata ORDER BY lastChangeTimeSeconds DESC LIMIT 1")
    fun getDoorData(): LiveData<DoorData>

    @Query("SELECT * FROM doordata ORDER BY lastChangeTimeSeconds DESC")
    fun getDoorHistory(): LiveData<List<DoorData>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(doorData: DoorData)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDoorHistory(doorData: List<DoorData>)

    @Query("DELETE FROM doordata")
    fun deleteAll()
}