/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import com.chriscartland.garage.GarageApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Answers the warning's "Snooze 1 hour" action (strategy 3.5). No screen
 * opens: the door on disk is snoozed through the shared use case and the
 * outcome is said where the warning was (`DoorNotificationPresenter`).
 *
 * A UI-less caller (ADR-033 a), so it reaches the use case straight off the
 * component, like the FCM service does. Private, with no intent filter: only
 * our own `PendingIntent` can reach it, which `SnoozeActionReceiverSafetyTest`
 * pins along with the absence of any route to the button.
 */
class SnoozeActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_SNOOZE_ONE_HOUR) {
            Logger.w { "SnoozeActionReceiver: ignoring ${intent.action}" }
            return
        }
        val component = (context.applicationContext as GarageApplication).component
        val presenter = DoorNotificationPresenter(context.applicationContext)
        // goAsync: one network call, well inside the receiver's budget, and
        // the process may be kept alive only for it.
        val pending = goAsync()
        component.applicationScope.launch(Dispatchers.IO) {
            try {
                val action =
                    SnoozeFromNotification.perform(
                        localDoorDataSource = component.localDoorDataSource,
                        snoozeNotifications = component.snoozeNotificationsUseCase,
                    )
                Logger.d { "SnoozeActionReceiver: $action" }
                presenter.showSnoozeOutcome(action)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE_ONE_HOUR = "com.chriscartland.garage.SNOOZE_ONE_HOUR"

        /** The action's tap target, built where the warning is posted. */
        fun pendingIntent(context: Context): PendingIntent {
            val intent =
                Intent(context, SnoozeActionReceiver::class.java)
                    .setAction(ACTION_SNOOZE_ONE_HOUR)
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
