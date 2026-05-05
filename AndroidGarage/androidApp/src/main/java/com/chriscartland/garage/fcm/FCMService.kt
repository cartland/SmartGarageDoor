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
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FCMService : FirebaseMessagingService() {
    private val handler: FcmMessageHandler by lazy {
        val component = (application as GarageApplication).component
        FcmMessageHandler(
            receiveFcmDoorEvent = component.receiveFcmDoorEventUseCase,
            applyButtonHealthFcm = component.applyButtonHealthFcmUseCase,
        )
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    override fun onNewToken(token: String) {
        Logger.d { "FCM Instance Token: $token" }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Logger.d { "onMessageReceived, from: ${remoteMessage.from}" }
        Logger.d { "Message data payload: ${remoteMessage.data}" }

        // remoteMessage.from is `/topics/<topic-name>` for topic-targeted
        // messages. Strip the prefix; treat null as empty so the dispatch
        // falls through to the default (door) branch.
        val topic = remoteMessage.from?.removePrefix("/topics/").orEmpty()

        serviceScope.launch(Dispatchers.IO) {
            try {
                handler.handleMessage(topic, remoteMessage.data)
            } catch (e: IllegalStateException) {
                Logger.e { "Failed to handle FCM message: $e" }
            }
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
