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
 * Clock-time and date-label formatting only. The duration-granularity
 * decomposition that used to live here moved to the shared
 * `HistoryDurationMapper` — both platforms were implementing the same two
 * ladders, so the buckets are now decided once and each platform supplies
 * the words.
 *
 * No user-visible label strings are produced here. The Composable
 * layer assembles localized strings via `stringResource` +
 * `pluralStringResource`.
 */
object HistoryFormatter {
    /**
     * Format an epoch-seconds time as "h:mm a" (e.g. "9:47 AM").
     *
     * The pattern and [Locale.US] are fixed, in production as well as in tests
     * — this is NOT locale-aware, despite what this comment used to claim. A
     * device set to 24-hour time still sees AM/PM here. iOS renders History the
     * same way, so the two platforms agree; if this is ever localized, both
     * sides should change together.
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

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
}
