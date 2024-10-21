package com.chriscartland.garage.ui

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Convert Unix timestamp seconds to a human-readable date string.
 */
fun Long.toFriendlyDate(): String =
    Instant.ofEpochSecond(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/**
 * Convert Unix timestamp seconds to a human-readable time string.
 */
fun Long.toFriendlyTime(): String? =
    Instant.ofEpochSecond(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM))

/**
 * Convert Unix timestamp seconds to a human-readable duration string.
 *
 * 0:59
 * 59:59
 * 23:59:59
 * 1 day, 23:59:59
 * 2 days, 23:59:59
 */
fun Duration.toFriendlyDuration(): String {
    val days = toDays().coerceAtLeast(0L)
    val hours = (toHours() % 24).coerceAtLeast(0L)
    val minutes = (toMinutes() % 60).coerceAtLeast(0L)
    val seconds = (seconds % 60).coerceAtLeast(0L)

    return when {
        days > 0 -> String.format("%d day%s, %02d:%02d:%02d", days, if (days > 1) "s" else "", hours, minutes, seconds)
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
        else -> String.format("%d:%02d", minutes, seconds)
    }
}
