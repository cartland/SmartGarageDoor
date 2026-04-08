package com.chriscartland.garage.domain.model

import kotlin.time.Duration

/**
 * Format a [Duration] as a human-readable string.
 *
 * Examples: "0s", "45s", "5m 30s", "2h 15m 30s", "1 day, 0h 0m 0s", "3 days, 5h 30m 0s"
 *
 * Pure arithmetic — no locale or platform dependencies.
 */
object FriendlyDuration {
    fun format(duration: Duration): String {
        val totalSeconds = duration.inWholeSeconds.coerceAtLeast(0L)
        val days = totalSeconds / 86400
        val hours = (totalSeconds % 86400) / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            days > 0 -> "$days day${if (days > 1) "s" else ""}, ${hours}h ${minutes}m ${seconds}s"
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}
