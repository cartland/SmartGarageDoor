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

package com.chriscartland.garage.presentation

/**
 * Typed bucket for "how long since the device last checked in" — the data behind
 * the Home "Status" header pill (ADR-031 shared presentation model).
 *
 * The granularity decision (the sub-10s "Just now" floor; when leftover
 * seconds/minutes are carried) is the non-trivial product choice worth sharing,
 * so Compose and SwiftUI render the same buckets; each UI supplies its own
 * localized "… ago" wording. Distinct from [ElapsedDuration] (the Home status
 * line): check-in is more precise near the present — it carries leftover seconds
 * in the minute bucket and has a "Just now" floor — because a *recent* heartbeat
 * is the signal the pill conveys.
 */
sealed interface CheckInAge {
    /** Under 10 seconds — rendered "Just now" (avoids per-tick second jitter). */
    data object JustNow : CheckInAge

    /** 10–59 seconds. */
    data class Seconds(
        val seconds: Int,
    ) : CheckInAge

    /** 1–59 minutes; leftover [seconds] carried ("1 min 30 sec ago"). */
    data class Minutes(
        val minutes: Int,
        val seconds: Int,
    ) : CheckInAge

    /** 1–23 hours; leftover [minutes] carried ("1 hr 20 min ago"). */
    data class Hours(
        val hours: Int,
        val minutes: Int,
    ) : CheckInAge

    /** 1+ whole days. Leftover hours dropped at this granularity. */
    data class Days(
        val days: Int,
    ) : CheckInAge
}

/**
 * Typed display state for the device check-in pill.
 *
 * [NoData] means no heartbeat has been observed yet (each UI renders an
 * icon-only pill). [Reported] carries the bucketed [Reported.age] plus
 * [Reported.isStale] (heartbeat older than the staleness threshold — drives the
 * icon flip and error coloring).
 */
sealed interface CheckInStatus {
    data object NoData : CheckInStatus

    data class Reported(
        val age: CheckInAge,
        val isStale: Boolean,
    ) : CheckInStatus
}

/**
 * Pure mapping from the device's last check-in timestamp + the current wall clock
 * to the typed [CheckInStatus]. Replaces the bucketing that lived in
 * `androidApp`'s `DeviceCheckIn.format`; the granularity + staleness rule is now
 * shared (ADR-031) and unit-tested in `commonTest`. Each UI formats the "… ago"
 * string itself (Compose in `DeviceCheckIn.format`; SwiftUI in the Home wrapper).
 */
object CheckInStatusMapper {
    /**
     * Heartbeat age (seconds) past which the pill flips to stale. Mirrors
     * `CheckInStalenessManager.CHECK_IN_STALE_THRESHOLD_SECONDS` (11 min) — the
     * two live in different modules and are kept in sync by hand.
     */
    const val STALE_THRESHOLD_SECONDS = 11L * 60

    /**
     * Maps using the default [STALE_THRESHOLD_SECONDS]. This is a separate
     * overload rather than a Kotlin default argument so the Swift bridge exposes
     * a clean two-argument call — SKIE does not bridge default arguments, and the
     * iOS wrapper would otherwise fail to compile ("missing argument for
     * parameter 'staleThresholdSeconds'").
     */
    fun forCheckIn(
        lastCheckInEpochSeconds: Long?,
        nowEpochSeconds: Long,
    ): CheckInStatus = forCheckIn(lastCheckInEpochSeconds, nowEpochSeconds, STALE_THRESHOLD_SECONDS)

    fun forCheckIn(
        lastCheckInEpochSeconds: Long?,
        nowEpochSeconds: Long,
        staleThresholdSeconds: Long,
    ): CheckInStatus {
        if (lastCheckInEpochSeconds == null) return CheckInStatus.NoData
        val age = (nowEpochSeconds - lastCheckInEpochSeconds).coerceAtLeast(0L)
        return CheckInStatus.Reported(
            age = ageOf(age),
            isStale = age > staleThresholdSeconds,
        )
    }

    private fun ageOf(age: Long): CheckInAge =
        when {
            age < JUST_NOW_FLOOR_SECONDS -> CheckInAge.JustNow
            age < SECONDS_PER_MIN -> CheckInAge.Seconds(age.toInt())
            age < SECONDS_PER_HOUR ->
                CheckInAge.Minutes(
                    minutes = (age / SECONDS_PER_MIN).toInt(),
                    seconds = (age % SECONDS_PER_MIN).toInt(),
                )
            age < SECONDS_PER_DAY ->
                CheckInAge.Hours(
                    hours = (age / SECONDS_PER_HOUR).toInt(),
                    minutes = ((age % SECONDS_PER_HOUR) / SECONDS_PER_MIN).toInt(),
                )
            else -> CheckInAge.Days((age / SECONDS_PER_DAY).toInt())
        }

    private const val JUST_NOW_FLOOR_SECONDS = 10L
    private const val SECONDS_PER_MIN = 60L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val SECONDS_PER_DAY = 86_400L
}
