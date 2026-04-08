package com.chriscartland.garage.domain.model

import kotlin.time.Duration

/**
 * Check whether a [DoorEvent] is stale (last check-in too long ago).
 *
 * Pure arithmetic on Unix timestamps — no platform dependencies.
 *
 * @param maxAge Maximum acceptable age before data is considered stale.
 * @param nowEpochSeconds Current time as Unix epoch seconds.
 */
object Staleness {
    fun isStale(
        lastCheckInTimeSeconds: Long?,
        maxAge: Duration,
        nowEpochSeconds: Long,
    ): Boolean {
        if (lastCheckInTimeSeconds == null) return false
        val limitSeconds = nowEpochSeconds - maxAge.inWholeSeconds
        return lastCheckInTimeSeconds < limitSeconds
    }
}
