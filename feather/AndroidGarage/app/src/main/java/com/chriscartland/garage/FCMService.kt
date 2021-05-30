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
import com.chriscartland.garage.model.DoorData
import com.chriscartland.garage.model.DoorState
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM Instance Token: $token")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        Log.d(TAG, "onMessageReceived, from: ${remoteMessage.from}")

        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            val doorData = remoteMessage.data.toDoorData()
            Log.d(TAG, "DoorData: ${doorData}")

            if (/* Check if data needs to be processed by long running job */ false) {
                // For long-running tasks (10 seconds or more) use WorkManager.
                scheduleJob()
            } else {
                // Handle message within 10 seconds
                handleNow(doorData)
            }
        } else {
            Log.d(TAG, "Message data payload is empty")
        }

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
        }
    }

    private fun scheduleJob() {
        Log.d(TAG, "scheduleJob...")
    }

    private fun handleNow(doorData: DoorData?) {
        Log.d(TAG, "handleNow...")
        if (doorData == null) {
            return
        }
        // TODO: Save door Data to database.
        // Cannot save directly to LiveData on a background thread.
//        val app = application as App
//        app.repository.loadingDoor.value = Loading(
//            data = doorData,
//            loading = LoadingState.LOADED_DATA
//        )
    }

    companion object {
        val TAG: String = FCMService::class.java.simpleName
    }
}

private fun <K, V> Map<K, V>.toDoorData(): DoorData? {
    val currentEvent = this as? Map<*, *> ?: return null
    val type = currentEvent["type"] as? String ?: ""
    val state = try {
        DoorState.valueOf(type)
    } catch (e: IllegalArgumentException) {
        DoorState.UNKNOWN
    }
    val message = currentEvent["message"] as? String ?: ""
    val timestampSeconds = currentEvent?.get("timestampSeconds") as? Long?
    return DoorData(
        state = state,
        message = message,
        lastChangeTimeSeconds = timestampSeconds,
        lastCheckInTimeSeconds = timestampSeconds
    )
}
