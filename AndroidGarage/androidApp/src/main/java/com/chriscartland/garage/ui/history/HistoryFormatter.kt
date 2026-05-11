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

package com.chriscartland.garage.ui.history

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure-function utilities for the History tab.
 *
 * Phase 2E of the string-resource migration plan
 * (`AndroidGarage/docs/PENDING_FOLLOWUPS.md` item #1) — locale-aware
 * formatting of times and date labels lives here, alongside the
 * [StateDurationParts] / [TransitDurationParts] decomposition that
 * mirrors `HomeStatusFormatter` for the History context's
 * different display granularities.
 *
 * No user-visible label strings are produced here. The Composable
 * layer assembles localized strings via `stringResource` +
 * `pluralStringResource`.
 */
object HistoryFormatter {
    /**
     * Format an epoch-seconds time as a locale-aware "h:mm a" string
     * (e.g. "9:47 AM"). Tests use [Locale.US] for reproducibility —
     * production renders in the device locale.
     */
    fun formatTime(
        timeSeconds: Long,
        zone: ZoneId,
    ): String =
        Instant
            .ofEpochSecond(timeSeconds)
            .atZone(zone)
            .format(timeFormatter)

    /**
     * Format a [LocalDate] as a short day-and-date string (e.g.
     * "Mon, Apr 27"). Used for [DayLabel.Date] rendering.
     */
    fun formatDate(date: LocalDate): String = date.format(dateFormatter)

    /**
     * Convert an epoch-seconds value into the local date in [zone],
     * used by [HistoryMapper.toHistoryDays] for day grouping and by
     * the Composable for [DayLabel] resolution.
     */
    fun localDate(
        timeSeconds: Long,
        zone: ZoneId,
    ): LocalDate =
        Instant
            .ofEpochSecond(timeSeconds)
            .atZone(zone)
            .toLocalDate()

    /**
     * Decompose a state-span duration in seconds into the parts the
     * Composable uses to pick a granularity:
     *  - `days >= 1` → render `"$days day [$hours hr]"` (drop hours when 0)
     *  - `hours >= 1` → render `"$hours hr [$mins min]"` (drop minutes when 0)
     *  - `minutes >= 1` → render `"$minutes min"`
     *  - else → render `"$seconds sec"`
     *
     * Mirrors `HomeStatusFormatter.durationParts` but drops sub-day
     * granularity differently — History shows "1 day 5 hr" while Home
     * shows just "1 day" (Home's "Since X" line stays compact; History's
     * "Open for X" line wants more precision).
     */
    fun stateDurationParts(seconds: Long): StateDurationParts {
        val safe = seconds.coerceAtLeast(0L)
        val days = (safe / SECONDS_PER_DAY).toInt()
        val hours = ((safe % SECONDS_PER_DAY) / SECONDS_PER_HOUR).toInt()
        val minutes = ((safe % SECONDS_PER_HOUR) / SECONDS_PER_MIN).toInt()
        val seconds = (safe % SECONDS_PER_MIN).toInt()
        return StateDurationParts(days, hours, minutes, seconds)
    }

    /**
     * Decompose a transit-span duration in seconds. Transits are
     * typically seconds, occasionally minutes for `_TOO_LONG` cases —
     * keep seconds suffix at minute scale (the precision helps), drop
     * seconds at hour scale (rare; readable).
     *
     *  - `hours >= 1` → render `"$hours hr [$mins min]"`
     *  - `minutes >= 1` → render `"$minutes min [$secs sec]"`
     *  - else → render `"$seconds sec"`
     */
    fun transitDurationParts(seconds: Long): TransitDurationParts {
        val safe = seconds.coerceAtLeast(0L)
        val hours = (safe / SECONDS_PER_HOUR).toInt()
        val minutes = ((safe % SECONDS_PER_HOUR) / SECONDS_PER_MIN).toInt()
        val seconds = (safe % SECONDS_PER_MIN).toInt()
        return TransitDurationParts(hours, minutes, seconds)
    }

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    private const val SECONDS_PER_MIN = 60L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val SECONDS_PER_DAY = 86_400L
}

/**
 * Decomposed state-span duration. The Composable renders only the
 * top-most non-zero granularity (e.g. days dominates; minutes are
 * shown alongside hours but seconds aren't shown alongside minutes).
 */
data class StateDurationParts(
    val days: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
)

/**
 * Decomposed transit-span duration. Different shape from
 * [StateDurationParts] because transits never carry day-scale data
 * (they're typically <1 minute, occasionally minutes).
 */
data class TransitDurationParts(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
)
