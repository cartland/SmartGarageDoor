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

package com.chriscartland.garage.ui.home

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pure-function utilities for the Home tab's "Since X · Y" status line.
 *
 * Phase 2C of the string-resource migration plan
 * (`AndroidGarage/docs/PENDING_FOLLOWUPS.md` item #1) — the time / date
 * formatting and duration breakdown live here as testable pure functions;
 * the Composable layer assembles the final localized string via
 * `stringResource` + `pluralStringResource`.
 *
 * No user-visible strings are produced here. [DurationParts] returns
 * raw integer counts; the Composable picks the granularity and renders.
 *
 * Replaces the previous string-emitting helpers in `HomeMapper`
 * (`formatTimeOrDate`, `formatDuration`, `sinceLine`).
 */
object HomeStatusFormatter {
    /**
     * Same-day → "9:47 AM"; different day → "Apr 28, 9:47 PM".
     *
     * Returns a localized time / date string built via [DateTimeFormatter].
     * Pattern is locale-aware via the default Locale of the JVM at format
     * time. Tests use `Locale.US` reproducibility — this matches the
     * legacy `HomeMapper.formatTimeOrDate` behavior.
     */
    fun formatTimeOrDate(
        instant: Instant,
        now: Instant,
        zone: ZoneId,
    ): String {
        val zonedTime = instant.atZone(zone)
        val zonedNow = now.atZone(zone)
        val sameDay = zonedTime.toLocalDate() == zonedNow.toLocalDate()
        return zonedTime.format(if (sameDay) TIME_ONLY else DATE_AND_TIME)
    }

    /**
     * Decompose a duration in seconds into days/hours/minutes/seconds parts.
     *
     * Negative inputs are clamped to zero so wall-clock skew can't produce
     * "-3 sec" durations. Total seconds modular-decomposed; a 90061-second
     * duration becomes [DurationParts(days = 1, hours = 1, minutes = 1, seconds = 1)].
     *
     * The Composable layer picks the display granularity:
     *  - days >= 1 → render days (special-case 1 vs 2+)
     *  - hours >= 1 → render "X hr Y min"
     *  - minutes >= 1 → render "X min"
     *  - else → render "X sec"
     */
    fun durationParts(totalSeconds: Long): DurationParts {
        val safe = totalSeconds.coerceAtLeast(0L)
        val days = (safe / SECONDS_PER_DAY).toInt()
        val hours = ((safe % SECONDS_PER_DAY) / SECONDS_PER_HOUR).toInt()
        val minutes = ((safe % SECONDS_PER_HOUR) / SECONDS_PER_MIN).toInt()
        val seconds = (safe % SECONDS_PER_MIN).toInt()
        return DurationParts(days, hours, minutes, seconds)
    }

    private val TIME_ONLY = DateTimeFormatter.ofPattern("h:mm a")
    private val DATE_AND_TIME = DateTimeFormatter.ofPattern("MMM d, h:mm a")
    private const val SECONDS_PER_MIN = 60L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val SECONDS_PER_DAY = 86_400L
}

/**
 * Decomposed duration. Each field is an Int because the values are bounded:
 * `seconds` < 60, `minutes` < 60, `hours` < 24, `days` < ~25K-year limit
 * before overflow (well past anything the app would ever display).
 */
data class DurationParts(
    val days: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
)
