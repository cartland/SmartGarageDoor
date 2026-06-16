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

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pure formatter for the resolved-on-close notification text. The server sends
 * only raw second timestamps; the client formats the human strings in the
 * DEVICE timezone (the server can't know it). Time zone + locale are explicit
 * parameters so the logic is deterministically testable.
 *
 * User-approved copy (2026-06-15):
 *   Title: "Resolved: garage door closed"
 *   Body:  "It was open for 14 minutes (2:00-2:14 PM)."
 */
object DoorResolvedNotificationText {
    const val TITLE = "Resolved: garage door closed"

    fun body(
        openTimestampSeconds: Long,
        closeTimestampSeconds: Long,
        timeZone: TimeZone,
        locale: Locale,
    ): String {
        val duration = formatDuration(closeTimestampSeconds - openTimestampSeconds)
        val range = formatRange(openTimestampSeconds, closeTimestampSeconds, timeZone, locale)
        return "It was open for $duration ($range)."
    }

    private fun formatDuration(seconds: Long): String {
        val totalMinutes = (seconds / 60).coerceAtLeast(1) // round down, floor at 1 minute
        if (totalMinutes < 60) return "$totalMinutes ${plural(totalMinutes, "minute")}"
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (minutes == 0L) {
            "$hours ${plural(hours, "hour")}"
        } else {
            "$hours ${plural(hours, "hour")} $minutes ${plural(minutes, "minute")}"
        }
    }

    private fun plural(
        n: Long,
        unit: String,
    ): String = if (n == 1L) unit else "${unit}s"

    private fun formatRange(
        openSec: Long,
        closeSec: Long,
        timeZone: TimeZone,
        locale: Locale,
    ): String {
        val start = Date(openSec * 1000)
        val end = Date(closeSec * 1000)
        val meridiem = dateFormat("a", timeZone, locale)
        val withMeridiem = dateFormat("h:mm a", timeZone, locale)
        val withoutMeridiem = dateFormat("h:mm", timeZone, locale)
        // Drop the start meridiem when it matches the end's → "2:00-2:14 PM".
        // Keep both when they differ (crossing noon/midnight) → "11:55 AM-12:10 PM".
        val sameMeridiem = meridiem.format(start) == meridiem.format(end)
        val startText = if (sameMeridiem) withoutMeridiem.format(start) else withMeridiem.format(start)
        val endText = withMeridiem.format(end)
        return "$startText-$endText"
    }

    private fun dateFormat(
        pattern: String,
        timeZone: TimeZone,
        locale: Locale,
    ): SimpleDateFormat = SimpleDateFormat(pattern, locale).apply { this.timeZone = timeZone }
}
