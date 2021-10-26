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

package com.chriscartland.garage.disk

import android.util.Log
import com.chriscartland.garage.AppExecutors
import com.chriscartland.garage.db.AppDatabase
import com.chriscartland.garage.model.DoorData
import java.util.concurrent.Executor

class LocalDataSource private constructor(
    private val executor: Executor,
    private val appDatabase: AppDatabase
) {

    val doorData = appDatabase.doorDataDao().getDoorData()

    val eventHistory = appDatabase.doorDataDao().getDoorHistory()

    fun updateDoorData(doorData: DoorData) {
        Log.d(TAG, "updateDoorData")
        executor.execute {
            appDatabase.runInTransaction {
                // Put new subscriptions data into localDataSource.
                appDatabase.doorDataDao().insert(doorData)
            }
        }
    }

    fun updateDoorHistory(eventHistory: List<DoorData>) {
        Log.d(TAG, "updateDoorHistory")
        executor.execute {
            appDatabase.runInTransaction {
                // Delete existing subscriptions.
                appDatabase.doorDataDao().deleteAll()
                // Put new subscriptions data into localDataSource.
                appDatabase.doorDataDao().insertDoorHistory(eventHistory)
            }
        }
    }

    companion object {
        val TAG: String = LocalDataSource::class.java.simpleName

        @Volatile
        private var INSTANCE: LocalDataSource? = null

        fun getInstance(executors: AppExecutors, database: AppDatabase): LocalDataSource =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalDataSource(executors.diskIO, database).also { INSTANCE = it }
            }
    }
}
