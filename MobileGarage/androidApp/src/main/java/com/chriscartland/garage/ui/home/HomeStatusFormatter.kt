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

import com.chriscartland.garage.presentation.SinceClock
import com.chriscartland.garage.presentation.SinceStatusMapper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Pure-function clock-formatting helper for the Home tab's "Since X · Y" status
 * line.
 *
 * Scope narrowed in the presentation-model realization (ADR-031): the elapsed
 * breakdown / granularity logic moved to the shared `presentation-model`
 * (`SinceStatusMapper` → `ElapsedDuration`), so this Android-only helper now
 * owns just the locale/timezone clock-time formatting. The Composable
 * (`rememberSinceLine`) assembles the final localized string.
 */
object HomeStatusFormatter {
    /**
     * Same-day → the time alone ("9:47 AM", or "21:47" in a 24-hour locale);
     * different day → the date as well ("Apr 28, 9:47 PM").
     *
     * WHICH of those is the shared [SinceStatusMapper.clockFor] decision, so
     * iOS cannot draw the day line differently. The FORMAT is the locale's:
     * `ofLocalizedTime(SHORT)` follows the locale's hour cycle, which a pinned
     * "h:mm a" used to override (ADR-035: 12- vs 24-hour is a locale property,
     * not a product decision). Tests pass an explicit [locale] so they do not
     * depend on the JVM's default.
     */
    fun formatTimeOrDate(
        instant: Instant,
        now: Instant,
        zone: ZoneId,
        locale: Locale = Locale.getDefault(),
    ): String {
        val zonedTime = instant.atZone(zone)
        val time = zonedTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
        return when (SinceStatusMapper.clockFor(instant.epochSecond, now.epochSecond, zone.id)) {
            SinceClock.TIME_ONLY -> time
            SinceClock.DATE_AND_TIME -> {
                val date = zonedTime.format(DateTimeFormatter.ofPattern("MMM d", locale))
                "$date, $time"
            }
        }
    }
}
