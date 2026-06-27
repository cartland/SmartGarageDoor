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
 * Typed elapsed-time bucket for the Home "Since … · 2 hr 14 min" status line
 * (ADR-031 shared presentation model).
 *
 * The granularity decision — show days, hours+minutes, just minutes, or seconds
 * — is the non-trivial logic that would otherwise drift if each UI reimplemented
 * it. It lives here as a typed value so both Compose and SwiftUI render the same
 * choice; each UI supplies its own localized unit strings (Compose plurals;
 * SwiftUI formatters). Only the relevant fields are carried per bucket, matching
 * what the status line actually shows (e.g. at day granularity the leftover
 * hours are intentionally dropped).
 */
sealed interface ElapsedDuration {
    /** 1+ whole days. Leftover hours/minutes are dropped at this granularity. */
    data class Days(
        val days: Int,
    ) : ElapsedDuration

    /** Under a day, 1+ hours. Rendered as "H hr M min"; leftover seconds dropped. */
    data class HoursMinutes(
        val hours: Int,
        val minutes: Int,
    ) : ElapsedDuration

    /** Under an hour, 1+ minutes. Leftover seconds dropped. */
    data class Minutes(
        val minutes: Int,
    ) : ElapsedDuration

    /** Under a minute. */
    data class Seconds(
        val seconds: Int,
    ) : ElapsedDuration
}

/**
 * Typed data for the Home status line. `null` (from [SinceStatusMapper.forEvent])
 * means the door's last-change time is unknown — each UI renders its own
 * "last change time unknown" copy.
 *
 * Clock-time formatting of [sinceEpochSeconds] (e.g. "9:47 AM" vs "Apr 28, 9:47
 * PM") stays per-UI: it's locale/timezone formatting (Compose `DateTimeFormatter`;
 * SwiftUI `Date.FormatStyle`), not logic worth sharing.
 */
data class SinceStatus(
    /** Epoch seconds of the door's last position change; format per-UI. */
    val sinceEpochSeconds: Long,
    /** Elapsed time since [sinceEpochSeconds], bucketed for display. */
    val elapsed: ElapsedDuration,
)

/**
 * Pure mapping from a door event's last-change timestamp + the current wall
 * clock to the typed [SinceStatus]. Replaces the decomposition that lived in
 * `androidApp`'s `HomeStatusFormatter.durationParts`; the granularity rule is
 * now shared (ADR-031) and unit-tested in `commonTest`.
 */
object SinceStatusMapper {
    fun forEvent(
        lastChangeEpochSeconds: Long?,
        nowEpochSeconds: Long,
    ): SinceStatus? {
        if (lastChangeEpochSeconds == null) return null
        val totalSeconds = (nowEpochSeconds - lastChangeEpochSeconds).coerceAtLeast(0L)
        return SinceStatus(
            sinceEpochSeconds = lastChangeEpochSeconds,
            elapsed = elapsedOf(totalSeconds),
        )
    }

    /**
     * Picks the coarsest sensible unit. Negative inputs are clamped by the
     * caller. A 90061-second span (1d 1h 1m 1s) renders as [ElapsedDuration.Days]
     * `(1)` because the status line shows only the leading unit past an hour.
     */
    private fun elapsedOf(totalSeconds: Long): ElapsedDuration {
        val days = (totalSeconds / SECONDS_PER_DAY).toInt()
        if (days >= 1) return ElapsedDuration.Days(days)
        val hours = (totalSeconds / SECONDS_PER_HOUR).toInt()
        if (hours >= 1) {
            val minutes = ((totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MIN).toInt()
            return ElapsedDuration.HoursMinutes(hours, minutes)
        }
        val minutes = (totalSeconds / SECONDS_PER_MIN).toInt()
        if (minutes >= 1) return ElapsedDuration.Minutes(minutes)
        return ElapsedDuration.Seconds(totalSeconds.toInt())
    }

    private const val SECONDS_PER_MIN = 60L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val SECONDS_PER_DAY = 86_400L
}
