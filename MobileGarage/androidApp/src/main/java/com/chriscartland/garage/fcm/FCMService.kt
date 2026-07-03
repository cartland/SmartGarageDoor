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
import com.chriscartland.garage.domain.model.DOOR_FCM_TOPIC_PREFIX
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FCMService : FirebaseMessagingService() {
    private val testNotificationPresenter: TestNotificationPresenter by lazy {
        TestNotificationPresenter(applicationContext)
    }

    private val doorNotificationPresenter: DoorNotificationPresenter by lazy {
        DoorNotificationPresenter(applicationContext)
    }

    private val handler: FcmMessageHandler by lazy {
        val component = (application as GarageApplication).component
        FcmMessageHandler(
            receiveFcmDoorEvent = component.receiveFcmDoorEventUseCase,
            applyButtonHealthFcm = component.applyButtonHealthFcmUseCase,
            showTestNotification = testNotificationPresenter::show,
            showDoorNotification = doorNotificationPresenter::show,
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
                // Forward the server notification title/body (present on the
                // relaxed-A combined resolved) so the resolved presenter can fall
                // back to them if its data block ever fails to parse.
                handler.handleMessage(
                    topic,
                    remoteMessage.data,
                    remoteMessage.notification?.title,
                    remoteMessage.notification?.body,
                )
            } catch (e: IllegalStateException) {
                Logger.e { "Failed to handle FCM message: $e" }
            }
        }

        // R6: the open-door WARNING is a notification-payload message. Android
        // renders it itself when the app is backgrounded (this callback is not
        // invoked then), but in the FOREGROUND this callback fires and the OS
        // does NOT display it — so render it here on the app-owned "Garage door"
        // channel/slot. Without this the warning is silently dropped whenever the
        // app is in the foreground. Today only the warning carries a notification
        // block (state-sync, button-health, resolved are all data-only), but we
        // still gate on the door topic so a future notification-block message on
        // another topic can never hijack the production door slot.
        remoteMessage.notification?.let { notification ->
            val title = notification.title.orEmpty()
            val body = notification.body.orEmpty()
            Logger.d { "Foreground notification on topic '$topic': $title / $body" }
            if (!topic.startsWith(DOOR_FCM_TOPIC_PREFIX)) {
                Logger.d { "Foreground notification not on the door topic; not rendering as a warning" }
            } else if (title.isBlank() && body.isBlank()) {
                Logger.w { "Door foreground notification had blank title and body; skipping" }
            } else {
                doorNotificationPresenter.showWarning(title = title, body = body)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
