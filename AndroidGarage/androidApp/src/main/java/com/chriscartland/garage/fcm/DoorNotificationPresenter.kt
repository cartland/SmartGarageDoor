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
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.chriscartland.garage.R
import com.chriscartland.garage.data.DoorResolvedPayload
import java.util.Locale
import java.util.TimeZone

/**
 * Renders the garage-door alerts as **app-owned** notifications on a single
 * dedicated channel + (tag, id) slot:
 *  - [showWarning] — the open-door "too long" WARNING when it arrives in the
 *    foreground (R6). The server sends the warning as a notification-payload
 *    message, which Android only renders itself when the app is backgrounded;
 *    in the foreground `FCMService.onMessageReceived` receives it and must
 *    render it, or it is silently dropped.
 *  - [show] — the additive resolved-on-close message (data-only, `door_open_v2-`
 *    topic, kind `open_door_resolved`).
 *
 * Both post to the same "Garage door" channel (HIGH importance) and the same
 * (tag, id) slot. They therefore have the same alerting *potential* (heads-up +
 * sound); note `setOnlyAlertOnce(true)` makes the resolved's in-place
 * replacement of an already-showing warning a silent update — intended, so the
 * all-clear doesn't re-buzz. The channel is created eagerly at startup
 * ([createChannel]) so the manifest `default_notification_channel_id` has a
 * real channel for the OS-rendered background warning to land on (M4) — without
 * it, background warnings fall back to the default "Miscellaneous" channel +
 * launcher icon. Generalizes the proven `TestNotificationPresenter`.
 *
 * The resolved path is flag-agnostic: it renders whatever resolved payload
 * arrives on the v2 topic. The server-side flag (`resolvedOnCloseEnabled`)
 * decides whether the server sends anything at all. See
 * docs/RESOLVED_NOTIFICATION_PLAN.md.
 */
class DoorNotificationPresenter(
    private val context: Context,
) {
    /**
     * Render the open-door warning (R6). Title/body come straight from the
     * server's notification payload (already human-readable, e.g. "Garage door
     * open" / "Open for more than 16 minutes").
     */
    fun showWarning(
        title: String,
        body: String,
    ) {
        ensureChannel()
        Logger.d { "DoorNotification: posting warning tag=$TAG title=$title" }
        post(title = title, body = body)
    }

    /** Render the additive resolved-on-close message. */
    fun show(data: Map<String, String>) {
        val content = DoorResolvedPayload.parse(data) ?: run {
            Logger.d { "DoorNotification: payload not a resolved-on-close message; ignoring" }
            return
        }
        ensureChannel()
        val body = DoorResolvedNotificationText.body(
            openTimestampSeconds = content.openTimestampSeconds,
            closeTimestampSeconds = content.closeTimestampSeconds,
            timeZone = TimeZone.getDefault(),
            locale = Locale.getDefault(),
        )
        Logger.d { "DoorNotification: posting resolved tag=$TAG body=$body" }
        post(title = DoorResolvedNotificationText.TITLE, body = body)
    }

    private fun post(
        title: String,
        body: String,
    ) {
        // The permission guard must live in the same method as notify() — lint's
        // MissingPermission check does not follow it across a helper call.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Logger.w { "DoorNotification: POST_NOTIFICATIONS not granted; skipping notify" }
            return
        }
        val notification =
            NotificationCompat
                .Builder(context, context.getString(R.string.door_notification_channel_id))
                .setSmallIcon(R.drawable.ic_notification_garage)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        // Same (tag, id) replaces the existing door notification in place — the
        // single slot shared by the warning and its resolution.
        NotificationManagerCompat.from(context).notify(TAG, NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() = createChannel(context)

    companion object {
        /** One door-alert slot; the warning and its resolution share this (tag, id) for inline replace. */
        const val TAG = "garage_door"
        const val NOTIFICATION_ID = 7001

        /**
         * Create the app-owned "Garage door" channel (HIGH importance).
         *
         * Called eagerly at startup (GarageApplication.onCreate) so the manifest
         * `default_notification_channel_id` has a real channel for the
         * OS-rendered background open-door warning to land on (M4), and so the
         * foreground warning + resolved render on it too. Creating a channel
         * that already exists is a no-op, so this is safe to call repeatedly.
         */
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel =
                    NotificationChannel(
                        context.getString(R.string.door_notification_channel_id),
                        "Garage door",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        description = "Garage door open-too-long alerts and their resolution."
                    }
                context
                    .getSystemService(NotificationManager::class.java)
                    ?.createNotificationChannel(channel)
            }
        }
    }
}
