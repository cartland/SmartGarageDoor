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
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.DocumentReference


class DoorViewModel : ViewModel() {

    enum class State {
        DEFAULT,
        LOADING_DATA,
        LOADED_DATA
    }

    val configData: MediatorLiveData<Pair<ServerConfig?, State>> = MediatorLiveData()
    private val configDataFirestore: FirestoreDocumentReferenceLiveData =
        FirestoreDocumentReferenceLiveData(null)
    fun setConfigDataDocumentReference(documentReference: DocumentReference?) {
        Log.d(TAG, "setServerConfigDocumentReference")
        configDataFirestore.documentReference = documentReference
        configData.value = Pair(null, State.LOADING_DATA)
    }

    val doorData: MediatorLiveData<Pair<DoorData?, State>> = MediatorLiveData()
    private val doorStatusFirestore: FirestoreDocumentReferenceLiveData =
        FirestoreDocumentReferenceLiveData(null)
    fun setDoorStatusDocumentReference(documentReference: DocumentReference?) {
        Log.d(TAG, "setDoorStatusDocumentReference")
        doorStatusFirestore.documentReference = documentReference
        doorData.value = Pair(null, State.LOADING_DATA)
    }

    init {
        Log.d(TAG, "init")
        doorData.value = Pair(null, State.DEFAULT)
        doorData.addSource(doorStatusFirestore) { value ->
            Log.d(TAG, "Received Firestore update for DoorData")
            doorData.value = Pair(value?.toDoorData(), State.LOADED_DATA)
        }
        configData.value = Pair(null, State.DEFAULT)
        configData.addSource(configDataFirestore) { value ->
            Log.d(TAG, "Received Firestore update for ServerConfig")
            configData.value = Pair(value?.toServerConfig(), State.LOADED_DATA)
        }
    }

    companion object {
        val TAG: String = DoorViewModel::class.java.simpleName
    }
}
