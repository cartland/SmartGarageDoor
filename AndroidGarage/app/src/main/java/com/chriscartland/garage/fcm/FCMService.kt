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

package com.chriscartland.garage.fcm

import android.util.Log
import com.chriscartland.garage.applogger.AppLoggerRepository
import com.chriscartland.garage.config.AppLoggerKeys
import com.chriscartland.garage.door.DoorEvent
import com.chriscartland.garage.door.DoorPosition
import com.chriscartland.garage.door.DoorRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FCMService : FirebaseMessagingService() {
    @Inject
    lateinit var doorRepository: DoorRepository

    @Inject
    lateinit var appLoggerRepository: AppLoggerRepository

    // Create Job and CoroutineScope to schedule brief, concurrent work.
    private val supervisorJob = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + supervisorJob)

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM Instance Token: $token")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        Log.d(TAG, "onMessageReceived, from: ${remoteMessage.from}")

        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            val doorEvent = remoteMessage.data.asDoorEvent()
            if (doorEvent == null) {
                Log.e(TAG, "Unknown message type: ${remoteMessage.data.entries.joinToString()}")
                return
            }
            Log.d(TAG, "DoorData: $doorEvent")
            coroutineScope.launch(Dispatchers.IO) {
                Log.d(TAG, "Logging FCM_DOOR_RECEIVED: ${AppLoggerKeys.FCM_DOOR_RECEIVED}")
                appLoggerRepository.log(AppLoggerKeys.FCM_DOOR_RECEIVED)
            }
            if (false) {
                /* Check if data needs to be processed by long running job */
                // For long-running tasks (10 seconds or more) use WorkManager.
                scheduleJob()
            } else {
                // Handle message within 10 seconds
                handleNow(doorEvent)
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

    /**
     * Handle the new door info now (complete within 10 seconds).
     */
    private fun handleNow(doorEvent: DoorEvent?) {
        Log.d(TAG, "handleNow...")
        if (doorEvent == null) {
            Log.d(TAG, "DoorEvent is null")
            return
        }
        Log.d(TAG, "Inserting DoorEvent: $doorEvent")
        doorRepository.insertDoorEvent(doorEvent)
    }

    override fun onDestroy() {
        super.onDestroy()
        supervisorJob.cancel()
    }
}

/**
 * Extract DoorEvent from Firebase RemoteMessage data payload.
 */
private fun <K, V> Map<K, V>.asDoorEvent(): DoorEvent? {
    try {
        val currentEvent = this as? Map<*, *> ?: return null // Required
        val type = currentEvent["type"] as? String ?: return null // Required
        val position = try {
            DoorPosition.valueOf(type)
        } catch (e: IllegalArgumentException) {
            DoorPosition.UNKNOWN
        }
        val message = currentEvent["message"] as? String ?: "" // Optional
        val timestampSeconds = (currentEvent["timestampSeconds"] as? String)
            ?.toLong() ?: return null // Required
        val checkInTimestampSeconds = (currentEvent["checkInTimestampSeconds"] as? String)
            ?.toLong() ?: return null // Required
        return DoorEvent(
            doorPosition = position,
            message = message,
            lastChangeTimeSeconds = timestampSeconds,
            lastCheckInTimeSeconds = checkInTimestampSeconds,
        )
    } catch (e: Exception) {
        Log.e("FCMService", "Error converting to DoorEvent: $e")
        return null
    }
}

private const val TAG = "FCMService"
