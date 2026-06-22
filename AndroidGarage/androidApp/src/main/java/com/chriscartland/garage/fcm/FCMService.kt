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
                handler.handleMessage(topic, remoteMessage.data)
            } catch (e: IllegalStateException) {
                Logger.e { "Failed to handle FCM message: $e" }
            }
        }

        // R6: the open-door WARNING is a notification-payload message. Android
        // renders it itself when the app is backgrounded (this callback is not
        // invoked then), but in the FOREGROUND this callback fires and the OS
        // does NOT display it — so render it here on the app-owned "Garage door"
        // channel/slot. Without this the warning is silently dropped whenever the
        // app is in the foreground. Only the warning carries a notification block
        // (state-sync, button-health, resolved are all data-only), so this never
        // double-renders with an OS-shown notification.
        remoteMessage.notification?.let { notification ->
            Logger.d { "Foreground notification: ${notification.title} / ${notification.body}" }
            doorNotificationPresenter.showWarning(
                title = notification.title.orEmpty(),
                body = notification.body.orEmpty(),
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
