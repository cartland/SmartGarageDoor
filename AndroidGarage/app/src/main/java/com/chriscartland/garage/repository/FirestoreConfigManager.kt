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
import com.chriscartland.garage.model.Loading
import com.chriscartland.garage.model.LoadingState
import com.chriscartland.garage.model.ServerConfig
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot

class FirestoreConfigManager(
    configReference: DocumentReference
) {

    val loadingConfig: MediatorLiveData<Loading<ServerConfig>> = MediatorLiveData()

    init {
        Log.d(TAG, "init")
        loadingConfig.value = Loading(
            null,
            LoadingState.LOADING_DATA
        )
        loadingConfig.addSource(FirestoreDocumentReferenceLiveData(configReference)) { value ->
            Log.d(Repository.TAG, "Received Firestore update for ServerConfig")
            loadingConfig.value = Loading(
                value?.toServerConfig(),
                LoadingState.LOADED_DATA
            )
        }
    }

    companion object {
        val TAG: String = FirestoreConfigManager::class.java.simpleName
    }
}

fun DocumentSnapshot.toServerConfig(): ServerConfig {
    val data = this.data as? Map<*, *> ?: return ServerConfig()
    val body = data["body"] as? Map<*, *> ?: return ServerConfig()
    val buildTimestamp = body["buildTimestamp"] as? String?
    val remoteButtonPushKey = body["remoteButtonPushKey"] as? String?
    val remoteButtonBuildTimestamp = body["remoteButtonBuildTimestamp"] as? String?
    val host = body["host"] as? String?
    val path = body["path"] as? String?
    val remoteButtonEnabled = body["remoteButtonEnabled"] as? Boolean ?: false
    val remoteButtonAuthorizedEmails = (body["remoteButtonAuthorizedEmails"] as? ArrayList<String>)?.toTypedArray()
    return ServerConfig(
        buildTimestamp = buildTimestamp,
        remoteButtonPushKey = remoteButtonPushKey,
        remoteButtonBuildTimestamp = remoteButtonBuildTimestamp,
        host = host,
        path = path,
        remoteButtonEnabled = remoteButtonEnabled,
        remoteButtonAuthorizedEmails = remoteButtonAuthorizedEmails
    )
}
