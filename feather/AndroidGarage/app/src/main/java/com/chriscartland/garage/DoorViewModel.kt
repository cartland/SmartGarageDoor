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

package com.chriscartland.garage

import android.util.Log
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.DocumentReference


class DoorViewModel : ViewModel() {

    val doorLoadingState = MutableLiveData<DoorLoadingState>()

    val doorData: MediatorLiveData<DoorData?> = MediatorLiveData()

    fun setDoorStatusDocumentReference(documentReference: DocumentReference) {
        Log.d(TAG, "setDoorStatusDocumentReference")
        doorStatusFirestore.documentReference = documentReference
        doorLoadingState.value = DoorLoadingState.LOADING_DATA
    }

    private val doorStatusFirestore: FirestoreDocumentReferenceLiveData =
        FirestoreDocumentReferenceLiveData(null)

    init {
        Log.d(TAG, "init")
        doorLoadingState.value = DoorLoadingState.DEFAULT
        doorData.addSource(doorStatusFirestore) { value ->
            Log.d(TAG, "Received Firestore update for door")
            doorData.value = value?.toDoorData()
            doorLoadingState.value = DoorLoadingState.LOADED_DATA
        }
    }

    companion object {
        val TAG: String = DoorViewModel::class.java.simpleName
    }
}
