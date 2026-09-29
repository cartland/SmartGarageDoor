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
import com.chriscartland.garage.R
import com.chriscartland.garage.data.DoorResolvedPayload
import com.chriscartland.garage.domain.model.SnoozeAction
import com.chriscartland.garage.ui.home.HomeStatusFormatter
import java.time.Instant
import java.time.ZoneId
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
        post(title = title, body = body, action = snoozeAction())
    }

    /**
     * Say how the warning's Snooze action went, where the warning was
     * (strategy 3.5). A success REPLACES the warning card — the door is still
     * open, but the user just said they know — so it lands on the same slot.
     * A failure sits BESIDE it on its own slot, because the warning still
     * stands and the card explains why the snooze did not. Which is which,
     * and the words, are `SnoozeOutcomeWords`.
     */
    fun showSnoozeOutcome(
        action: SnoozeAction,
        now: Instant = Instant.now(),
    ) {
        val titleRes = SnoozeOutcomeWords.title(action) ?: return
        val bodyRes = SnoozeOutcomeWords.body(action) ?: return
        val body =
            if (action is SnoozeAction.Succeeded.Set) {
                val until = Instant.ofEpochSecond(action.untilEpochSeconds)
                context.getString(bodyRes, HomeStatusFormatter.formatTimeOrDate(until, now, ZoneId.systemDefault()))
            } else {
                context.getString(bodyRes)
            }
        val id = if (SnoozeOutcomeWords.replacesWarning(action)) NOTIFICATION_ID else SNOOZE_OUTCOME_NOTIFICATION_ID
        ensureChannel()
        Logger.d { "DoorNotification: posting snooze outcome $action on id=$id" }
        post(title = context.getString(titleRes), body = body, id = id)
    }

    /**
     * Render the additive resolved-on-close message.
     *
     * The rich body (device-local start/end times) is computed client-side from
     * the raw timestamps in [data]. [fallbackTitle]/[fallbackBody] are the server's
     * notification-block strings, present only for the relaxed-A COMBINED resolved
     * (`resolvedNotificationPayloadEnabled`); the data-only resolved carries none.
     *
     * Failure mode — defensive, SHOULD NOT happen. The server always sends a
     * well-formed data block (pinned byte-for-byte to
     * `wire-contracts/openDoorResolved/`), so [DoorResolvedPayload.parse] should
     * never return null for a real resolved. If it somehow does AND a server
     * notification block was present, render that instead of silently dropping the
     * notification. The ONLY UI degradation is the missing device-local start/end
     * clock times ("2:00-2:14 PM") — the server body still states the door closed
     * and the open duration. See docs/RESOLVED_NOTIFICATION_NO_COMPROMISE.md §9.4.
     */
    fun show(
        data: Map<String, String>,
        fallbackTitle: String? = null,
        fallbackBody: String? = null,
    ) {
        val content = DoorResolvedPayload.parse(data)
        if (content == null) {
            if (!fallbackTitle.isNullOrBlank() || !fallbackBody.isNullOrBlank()) {
                Logger.w {
                    "DoorNotification: resolved data-parse failed; rendering server " +
                        "notification fallback (degraded: missing device-local times)"
                }
                ensureChannel()
                post(title = fallbackTitle.orEmpty(), body = fallbackBody.orEmpty())
            } else {
                Logger.d { "DoorNotification: payload not a resolved-on-close message; ignoring" }
            }
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
        id: Int = NOTIFICATION_ID,
        action: NotificationCompat.Action? = null,
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
                .setContentIntent(launchAppIntent())
                .apply { if (action != null) addAction(action) }
                .build()
        // Same (tag, id) replaces the existing door notification in place — the
        // single slot shared by the warning and its resolution. A failed snooze
        // passes its own id so the warning it could not silence stays.
        NotificationManagerCompat.from(context).notify(TAG, id, notification)
    }

    /**
     * The warning's one action (strategy 3.5): snooze for an hour, from the
     * card. Snooze is not a press — it changes what the server will say for
     * an hour, never what the door does — which is why it may live here while
     * the button may not. Handled by [SnoozeActionReceiver].
     */
    private fun snoozeAction(): NotificationCompat.Action =
        NotificationCompat.Action
            .Builder(
                0,
                context.getString(R.string.notification_action_snooze_one_hour),
                SnoozeActionReceiver.pendingIntent(context),
            )
            // An unlocked device, as on iOS (`.authenticationRequired`): an
            // hour of silence is not something a pocket should decide.
            .setAuthenticationRequired(true)
            .build()

    /**
     * Tap target: open the app. App-built notifications get NO tap action by
     * default — FCM only auto-attaches a launch intent to OS-rendered
     * notification-payload messages, so without this, tapping does nothing.
     * FLAG_IMMUTABLE is mandatory at the app's target SDK.
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

    private fun ensureChannel() = createChannel(context)

    companion object {
        /** One door-alert slot; the warning and its resolution share this (tag, id) for inline replace. */
        const val TAG = "garage_door"
        const val NOTIFICATION_ID = 7001

        /** A failed snooze's card: beside the warning, never over it. */
        const val SNOOZE_OUTCOME_NOTIFICATION_ID = 7002

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
