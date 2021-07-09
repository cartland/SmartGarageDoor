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

package com.chriscartland.garage.repository

import android.content.Context
import android.util.Log
import com.chriscartland.garage.disk.LocalDataSource
import com.chriscartland.garage.internet.RemoteDataSource
import com.chriscartland.garage.model.DoorData

class Repository(
    val localDataSource: LocalDataSource,
    val appVersionManager: AppVersionManager,
    val firestoreConfigManager: FirestoreConfigManager,
    val remoteDataSource: RemoteDataSource
) {

    val appVersion = appVersionManager.appVersion

    val loadingConfig = firestoreConfigManager.loadingConfig

    val doorData = localDataSource.doorData

    fun setDoorData(doorData: DoorData) {
        Log.d(TAG, "setDoorData")
        localDataSource.updateDoorData(doorData)
    }

    fun refreshData(context: Context, buildTimestamp: String) {
        Log.d(TAG, "refreshData")
        remoteDataSource.refreshDoorData(context, buildTimestamp)
    }

    init {
        remoteDataSource.doorData.observeForever { doorData ->
            setDoorData(doorData)
        }
    }

    companion object {
        val TAG: String = Repository::class.java.simpleName

        @Volatile
        private var INSTANCE: Repository? = null

        fun getInstance(
            localDataSource: LocalDataSource,
            appVersionManager: AppVersionManager,
            firestoreConfigManager: FirestoreConfigManager,
            remoteDataSource: RemoteDataSource
        ): Repository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Repository(
                    localDataSource,
                    appVersionManager,
                    firestoreConfigManager,
                    remoteDataSource
                ).also { INSTANCE = it }
            }
    }
}
