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

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.chriscartland.garage.MainActivity

/**
 * Renders test-notification-sandbox messages as **app-owned** notifications.
 *
 * This is the isolated prototype of the app-built notification infrastructure
 * the reliable open-door "Resolved" feature will later need: a dedicated
 * channel, `tag`-based inline replacement, and uniform foreground/background
 * rendering (a data message always reaches `onMessageReceived`). It is driven
 * ONLY by the diagnostic `testNotification-*` topic and touches nothing in the
 * production door-notification path.
 */
class TestNotificationPresenter(
    private val context: Context,
) {
    fun show(data: Map<String, String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Logger.w { "TestNotification: POST_NOTIFICATIONS not granted; skipping notify" }
            return
        }
        ensureChannel()
        val content = TestNotificationPayload.parse(data)
        Logger.d { "TestNotification: posting tag=${content.tag} title=${content.title}" }
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(content.title)
                .setContentText(content.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(launchAppIntent())
                .build()
        // Same (tag, id) replaces the existing notification in place.
        NotificationManagerCompat.from(context).notify(content.tag, NOTIFICATION_ID, notification)
    }

    /**
     * Tap target: open the app. App-built notifications get no tap action by
     * default (FCM only auto-attaches a launch intent to OS-rendered
     * notification-payload messages). FLAG_IMMUTABLE is mandatory at target SDK.
     */
    private fun launchAppIntent(): PendingIntent {
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Test notifications (diagnostic)",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Sandbox channel for the test-notification diagnostic feature."
                }
            context
                .getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "test_notification_sandbox"

        /** Fixed id; the payload `tag` is what differentiates / replaces notifications. */
        const val NOTIFICATION_ID = 4242
    }
}
