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
import com.chriscartland.garage.model.ServerConfig
import com.chriscartland.garage.model.toServerConfig
import com.google.firebase.firestore.DocumentReference

class Repository(
    appVersionManager: AppVersionManager
) {

    val appVersion = appVersionManager.appVersion

    val configDataState: MediatorLiveData<Pair<ServerConfig?, State>> = MediatorLiveData()
    private val configDataFirestore: FirestoreDocumentReferenceLiveData =
        FirestoreDocumentReferenceLiveData(
            null
        )

    fun setConfigDataDocumentReference(documentReference: DocumentReference?) {
        Log.d(TAG, "setServerConfigDocumentReference")
        configDataFirestore.documentReference = documentReference
        configDataState.value = Pair(
            null,
            State.LOADING_DATA
        )
    }

    init {
        configDataState.value = Pair(
            null,
            State.DEFAULT
        )
        configDataState.addSource(configDataFirestore) { value ->
            Log.d(TAG, "Received Firestore update for ServerConfig")
            configDataState.value = Pair(value?.toServerConfig(),
                State.LOADED_DATA
            )
        }
    }

    companion object {
        val TAG: String = Repository::class.java.simpleName
    }

    enum class State {
        DEFAULT,
        LOADING_DATA,
        LOADED_DATA
    }
}