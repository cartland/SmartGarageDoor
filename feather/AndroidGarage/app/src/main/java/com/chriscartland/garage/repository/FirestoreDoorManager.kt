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
import androidx.lifecycle.MediatorLiveData
import com.chriscartland.garage.model.DoorData
import com.chriscartland.garage.model.DoorState
import com.chriscartland.garage.model.Loading
import com.chriscartland.garage.model.LoadingState
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.ktx.Firebase
import com.google.firebase.perf.ktx.performance

class FirestoreDoorManager {

    val loadingDoor: MediatorLiveData<Loading<DoorData>> = MediatorLiveData()

    private var doorReference: DocumentReference? = null

    fun refreshData() {
        Log.d(TAG, "refreshData")
        val request = doorReference?.get() ?: return
        val refreshTrace = Firebase.performance.newTrace(TRACE_FIRESTORE_REFRESH_REQUEST)
        refreshTrace.start()
        request.addOnSuccessListener { document ->
            Log.d(TAG, "refreshData: onSuccess")
            refreshTrace.stop()
            if (document != null) {
                Log.d(TAG, "refreshData: DocumentSnapshot data: ${document.data}")
                loadingDoor.value = Loading(
                    document.toDoorData(),
                    LoadingState.LOADED_DATA
                )
            } else {
                Log.w(TAG, "refreshData: No document.")
            }
        }.addOnFailureListener { exception ->
            Log.d(TAG, "refreshData: onFailure", exception)
            refreshTrace.stop()
        }
    }

    fun setDoorStatusDocumentReference(documentReference: DocumentReference?) {
        doorReference = documentReference
    }

    init {
        Log.d(TAG, "init")
        loadingDoor.value = Loading(
            null,
            LoadingState.LOADING_DATA
        )
    }

    companion object {
        val TAG: String = FirestoreDoorManager::class.java.simpleName

        const val TRACE_FIRESTORE_REFRESH_REQUEST: String = "TRACE_FIRESTORE_REFRESH_REQUEST"
    }
}

fun DocumentSnapshot.toDoorData(): DoorData {
    val data = this.data as? Map<*, *> ?: return DoorData()
    val currentEvent = data["currentEvent"] as? Map<*, *>
    val type = currentEvent?.get("type") as? String ?: ""
    val state = try {
        DoorState.valueOf(type)
    } catch (e: IllegalArgumentException) {
        DoorState.UNKNOWN
    }
    val message = currentEvent?.get("message") as? String ?: ""
    val timestampSeconds = currentEvent?.get("timestampSeconds") as? Long?
    val lastCheckInTime = data["FIRESTORE_databaseTimestampSeconds"] as? Long?
    return DoorData(
        state = state,
        message = message,
        lastChangeTimeSeconds = timestampSeconds,
        lastCheckInTimeSeconds = lastCheckInTime
    )
}
