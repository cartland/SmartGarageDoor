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

package com.chriscartland.garage.wear.ongoing

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.chriscartland.garage.wear.MainActivity
import com.chriscartland.garage.wear.R

/**
 * The watch face still says "Waiting for the door" after the wrist drops.
 *
 * A press is not finished when the screen goes dark. Between the hold and the
 * door answering there are seconds of network and mechanism, and a watch whose
 * screen has timed out shows the face — with nothing on it about the press the
 * user just made. Wear's answer is an ongoing activity: an ongoing notification
 * from a foreground service, which the system draws as a tappable chip on the
 * watch face and in Recents, worded from [androidx.wear.ongoing.Status]
 * (strategy 3.3).
 *
 * What it is and is not:
 *
 * - **Driven by the same decision as the screen wake.** `WearApp` shows it
 *   while `WearHomeViewModel.doorActivity` is non-null and hides it the moment
 *   it is null, and that value is bounded by the ViewModel's
 *   `KEEP_SCREEN_ON_MILLIS` window. The chip cannot outlive the app's reason to
 *   keep watching; `shortService` is the platform's own cap on top (three
 *   minutes), and [onTimeout] stops cleanly if it is ever reached.
 * - **Read-only, like every glance surface.** Tapping opens the app. It holds
 *   nothing that could press the button (`DoorActivitySafetyTest`), it is not
 *   exported, and it has no intent filter: only the app starts it.
 * - **Silent.** The channel is `IMPORTANCE_LOW`: this is a status line, not an
 *   alert, and the outcome haptics already say when something happened.
 * - **Needs `POST_NOTIFICATIONS` on Android 13+**, requested from Settings
 *   ("Door progress"), never mid-press. Without it the service still runs and
 *   the notification is simply not shown, so nothing else changes.
 */
class DoorActivityService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val statusRes = intent?.getIntExtra(EXTRA_STATUS_RES, 0) ?: 0
        if (statusRes == 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        val status = getString(statusRes)
        val touch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(touch)
            .setOngoing(true)
        // Applied BEFORE the notification is built, as the library requires:
        // it writes the ongoing-activity extras into the builder.
        OngoingActivity
            .Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_launcher_monochrome)
            .setStatus(Status.forPart(Status.TextPart(status)))
            // Without this the chip is read out as the app's name ("Garage");
            // the words on it are the whole point.
            .setContentDescription(status)
            .setTouchIntent(touch)
            .build()
            .apply(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(NOTIFICATION_ID, builder.build())
        }
        return START_NOT_STICKY
    }

    /** The platform's short-service budget ran out; stop before it stops us. */
    override fun onTimeout(startId: Int) {
        stopSelf()
    }

    companion object {
        const val CHANNEL_ID = "door_activity"
        const val NOTIFICATION_ID = 7101
        const val EXTRA_STATUS_RES = "statusRes"

        /** Idempotent. Where the platform files the chip's notification, quietly. */
        fun ensureChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.door_activity_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }

        /** Whether the platform will show the chip at all: a runtime permission from Android 13. */
        fun permissionRequired(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        fun notificationsGranted(context: Context): Boolean =
            !permissionRequired() ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        /** Show (or re-word) the chip. Starting an already-running service just re-runs [onStartCommand]. */
        fun show(
            context: Context,
            @StringRes statusRes: Int,
        ) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DoorActivityService::class.java).putExtra(EXTRA_STATUS_RES, statusRes),
            )
        }

        fun hide(context: Context) {
            context.stopService(Intent(context, DoorActivityService::class.java))
        }
    }
}
