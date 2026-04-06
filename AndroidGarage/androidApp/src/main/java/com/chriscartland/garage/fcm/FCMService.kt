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

import co.touchlab.kermit.Logger
import com.chriscartland.garage.GarageApplication
import com.chriscartland.garage.applogger.AppLoggerRepository
import com.chriscartland.garage.config.AppLoggerKeys
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.repository.DoorRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FCMService : FirebaseMessagingService() {
    private val doorRepository: DoorRepository by lazy {
        (application as GarageApplication).component.provideDoorRepository()
    }

    private val appLoggerRepository: AppLoggerRepository by lazy {
        (application as GarageApplication).component.provideAppLoggerRepository()
    }

    // Create Job and CoroutineScope to schedule brief, concurrent work.
    private val supervisorJob = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + supervisorJob)

    override fun onNewToken(token: String) {
        Logger.d { "FCM Instance Token: $token" }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        Logger.d { "onMessageReceived, from: ${remoteMessage.from}" }

        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            Logger.d { "Message data payload: ${remoteMessage.data}" }

            val doorEvent = FcmPayloadParser.parseDoorEvent(remoteMessage.data)
            if (doorEvent == null) {
                Logger.e { "Unknown message type: ${remoteMessage.data.entries.joinToString()}" }
                return
            }
            Logger.d { "DoorData: $doorEvent" }
            coroutineScope.launch(Dispatchers.IO) {
                Logger.d { "Logging FCM_DOOR_RECEIVED: ${AppLoggerKeys.FCM_DOOR_RECEIVED}" }
                appLoggerRepository.log(AppLoggerKeys.FCM_DOOR_RECEIVED)
            }
            if (false) {
                // Check if data needs to be processed by long running job

                // For long-running tasks (10 seconds or more) use WorkManager.
                scheduleJob()
            } else {
                // Handle message within 10 seconds
                handleNow(doorEvent)
            }
        } else {
            Logger.d { "Message data payload is empty" }
        }

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            Logger.d { "Message Notification Body: ${it.body}" }
        }
    }

    private fun scheduleJob() {
        Logger.d { "scheduleJob..." }
    }

    /**
     * Handle the new door info now (complete within 10 seconds).
     */
    private fun handleNow(doorEvent: DoorEvent?) {
        Logger.d { "handleNow..." }
        if (doorEvent == null) {
            Logger.d { "DoorEvent is null" }
            return
        }
        Logger.d { "Inserting DoorEvent: $doorEvent" }
        doorRepository.insertDoorEvent(doorEvent)
    }

    override fun onDestroy() {
        super.onDestroy()
        supervisorJob.cancel()
    }
}
