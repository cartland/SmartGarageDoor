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

package com.chriscartland.garage.datalocal

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLoggerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(appEvent: AppEvent)

    @Query("SELECT * FROM appEvent ORDER BY timestamp ASC")
    fun getAll(): Flow<List<AppEvent>>

    @Query("SELECT count(*) from appEvent WHERE eventKey = :key")
    fun countKey(key: String): Flow<Long>

    @Query("SELECT DISTINCT eventKey FROM appEvent")
    fun distinctKeys(): List<String>

    @Query(
        "DELETE FROM appEvent WHERE eventKey = :key " +
            "AND id NOT IN (SELECT id FROM appEvent WHERE eventKey = :key " +
            "ORDER BY timestamp DESC, id DESC LIMIT :limit)",
    )
    fun pruneKey(
        key: String,
        limit: Int,
    )

    @Transaction
    fun insertAndPruneKey(
        appEvent: AppEvent,
        limit: Int,
    ) {
        insert(appEvent)
        pruneKey(appEvent.eventKey, limit)
    }

    @Transaction
    fun pruneAllKeys(limit: Int) {
        for (key in distinctKeys()) {
            pruneKey(key, limit)
        }
    }

    @Query("DELETE FROM appEvent")
    fun deleteAllAppEvents()
}
