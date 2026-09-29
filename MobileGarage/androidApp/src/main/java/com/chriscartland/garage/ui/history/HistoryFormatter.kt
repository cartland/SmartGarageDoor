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
import java.time.format.FormatStyle
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
     * Format an epoch-seconds time in the locale's short time style: "9:47 AM",
     * or "21:47" in a 24-hour locale.
     *
     * Until strategy 1.8 this pinned "h:mm a" + [Locale.US] and its KDoc said
     * iOS rendered History the same way. iOS never did: it uses a localized
     * template that follows the device's hour cycle, and ADR-035 names 12- vs
     * 24-hour a locale property rather than a product decision. Tests pass an
     * explicit [locale] so they do not depend on the JVM's default.
     */
    fun formatTime(
        timeSeconds: Long,
        zone: ZoneId,
        locale: Locale = Locale.getDefault(),
    ): String =
        Instant
            .ofEpochSecond(timeSeconds)
            .atZone(zone)
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

    /**
     * Format a [LocalDate] as a short day-and-date string (e.g.
     * "Mon, Apr 27"). Used for [DayLabel.Date] rendering. The day and month
     * names follow [locale]; the field order is fixed.
     */
    fun formatDate(
        date: LocalDate,
        locale: Locale = Locale.getDefault(),
    ): String = date.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
}
