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
     * Same-day → "9:47 AM"; different day → "Apr 28, 9:47 PM".
     *
     * Returns a localized time / date string built via [DateTimeFormatter].
     * Pattern is locale-aware via the default Locale of the JVM at format
     * time. Tests use `Locale.US` for reproducibility.
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

    private val TIME_ONLY = DateTimeFormatter.ofPattern("h:mm a")
    private val DATE_AND_TIME = DateTimeFormatter.ofPattern("MMM d, h:mm a")
}
