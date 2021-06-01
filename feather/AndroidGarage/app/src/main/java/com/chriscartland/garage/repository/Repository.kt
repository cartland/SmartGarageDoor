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

import android.util.Log
import com.chriscartland.garage.disk.LocalDataSource
import com.chriscartland.garage.model.DoorData
import com.chriscartland.garage.model.LoadingState
import com.google.firebase.firestore.DocumentReference

class Repository(
    val localDataSource: LocalDataSource,
    val appVersionManager: AppVersionManager,
    val firestoreConfigManager: FirestoreConfigManager,
    val firestoreDoorManager: FirestoreDoorManager
) {

    val appVersion = appVersionManager.appVersion

    val loadingConfig = firestoreConfigManager.loadingConfig

    val doorData = localDataSource.doorData

    fun setDoorData(doorData: DoorData) {
        Log.d(TAG, "setDoorData")
        localDataSource.updateDoorData(doorData)
    }

    fun setDoorStatusDocumentReference(documentReference: DocumentReference?) {
        firestoreDoorManager.setDoorStatusDocumentReference(documentReference)
    }

    fun refreshData() {
        Log.d(TAG, "refreshData")
        firestoreDoorManager.refreshData()
    }

    init {
        firestoreDoorManager.loadingDoor.observeForever { loadingDoor ->
            if (loadingDoor.loading != LoadingState.LOADED_DATA) {
                Log.d(TAG, "Door data is not loaded: ${loadingDoor}")
                return@observeForever
            }
            Log.d(TAG, "Door data is loaded: ${loadingDoor}")
            val doorData = loadingDoor.data
            if (doorData == null) {
                Log.d(TAG, "Door data is null")
                return@observeForever
            }
            Log.d(TAG, "setDoorData: Writing data from Firestore to local database")
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
            firestoreDoorManager: FirestoreDoorManager
        ): Repository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Repository(
                    localDataSource,
                    appVersionManager,
                    firestoreConfigManager,
                    firestoreDoorManager
                ).also { INSTANCE = it }
            }
    }
}
