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
import com.chriscartland.garage.data.FcmPayloadParser
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.repository.AppLoggerRepository
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

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    override fun onNewToken(token: String) {
        Logger.d { "FCM Instance Token: $token" }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Logger.d { "onMessageReceived, from: ${remoteMessage.from}" }

        if (remoteMessage.data.isEmpty()) {
            Logger.d { "Message data payload is empty" }
            return
        }

        Logger.d { "Message data payload: ${remoteMessage.data}" }
        val doorEvent = FcmPayloadParser.parseDoorEvent(remoteMessage.data)
        if (doorEvent == null) {
            Logger.e { "Failed to parse FCM payload: ${remoteMessage.data.entries.joinToString()}" }
            return
        }

        Logger.d { "DoorData: $doorEvent" }
        serviceScope.launch(Dispatchers.IO) {
            appLoggerRepository.log(AppLoggerKeys.FCM_DOOR_RECEIVED)
        }

        try {
            doorRepository.insertDoorEvent(doorEvent)
        } catch (e: IllegalStateException) {
            Logger.e { "Failed to insert door event: $e" }
        }

        remoteMessage.notification?.let {
            Logger.d { "Message Notification Body: ${it.body}" }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
