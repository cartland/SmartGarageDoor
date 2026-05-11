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

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/**
 * Tests for [HomeStatusFormatter].
 *
 * Phase 2C of the string-resource migration plan
 * (`AndroidGarage/docs/PENDING_FOLLOWUPS.md` item #1) — `formatTimeOrDate`
 * was moved here from `HomeMapper` (along with the duration-decomposition
 * logic, now expressed as [HomeStatusFormatter.durationParts] returning a
 * typed [DurationParts]).
 *
 * These tests cover the pure-function formatting boundaries; the localized
 * "Since X · Y" assembly happens in `rememberSinceLine` (Composable, in
 * `HomeContent.kt`) and is verified via screenshot tests + the `home_*`
 * resources in `strings.xml` / `plurals.xml`.
 */
class HomeStatusFormatterTest {
    private val zone = ZoneOffset.UTC

    // 2026-04-29 12:00:00 UTC.
    private val now: Instant = Instant.parse("2026-04-29T12:00:00Z")

    // region formatTimeOrDate

    @Test
    fun formatTimeOrDate_sameDay_shows_only_time() {
        // 9:47 AM UTC on 2026-04-29.
        val instant = Instant.parse("2026-04-29T09:47:00Z")
        assertEquals("9:47 AM", HomeStatusFormatter.formatTimeOrDate(instant, now, zone))
    }

    @Test
    fun formatTimeOrDate_sameDay_pm() {
        val instant = Instant.parse("2026-04-29T11:22:00Z")
        assertEquals("11:22 AM", HomeStatusFormatter.formatTimeOrDate(instant, now, zone))
    }

    @Test
    fun formatTimeOrDate_differentDay_shows_month_day_and_time() {
        val instant = Instant.parse("2026-04-28T21:47:00Z")
        assertEquals("Apr 28, 9:47 PM", HomeStatusFormatter.formatTimeOrDate(instant, now, zone))
    }

    @Test
    fun formatTimeOrDate_differentMonth() {
        val instant = Instant.parse("2026-03-15T08:05:00Z")
        assertEquals("Mar 15, 8:05 AM", HomeStatusFormatter.formatTimeOrDate(instant, now, zone))
    }

    // endregion

    // region durationParts

    @Test
    fun durationParts_zero() {
        assertEquals(DurationParts(days = 0, hours = 0, minutes = 0, seconds = 0), HomeStatusFormatter.durationParts(0))
    }

    @Test
    fun durationParts_negative_clamped() {
        assertEquals(DurationParts(0, 0, 0, 0), HomeStatusFormatter.durationParts(-100))
    }

    @Test
    fun durationParts_seconds_under_minute() {
        assertEquals(DurationParts(0, 0, 0, 1), HomeStatusFormatter.durationParts(1))
        assertEquals(DurationParts(0, 0, 0, 38), HomeStatusFormatter.durationParts(38))
        assertEquals(DurationParts(0, 0, 0, 59), HomeStatusFormatter.durationParts(59))
    }

    @Test
    fun durationParts_minutes_under_hour() {
        assertEquals(DurationParts(0, 0, 1, 0), HomeStatusFormatter.durationParts(60))
        assertEquals(DurationParts(0, 0, 4, 0), HomeStatusFormatter.durationParts(60L * 4))
        assertEquals(DurationParts(0, 0, 38, 30), HomeStatusFormatter.durationParts(60L * 38 + 30))
        assertEquals(DurationParts(0, 0, 59, 59), HomeStatusFormatter.durationParts(60L * 59 + 59))
    }

    @Test
    fun durationParts_hours_decompose_into_hours_and_minutes() {
        assertEquals(DurationParts(0, 1, 0, 0), HomeStatusFormatter.durationParts(3_600))
        assertEquals(DurationParts(0, 2, 14, 0), HomeStatusFormatter.durationParts(2 * 3_600L + 14 * 60))
        assertEquals(DurationParts(0, 23, 59, 0), HomeStatusFormatter.durationParts(23 * 3_600L + 59 * 60))
    }

    @Test
    fun durationParts_one_day() {
        assertEquals(DurationParts(1, 0, 0, 0), HomeStatusFormatter.durationParts(86_400))
        assertEquals(DurationParts(1, 5, 0, 0), HomeStatusFormatter.durationParts(86_400 + 5 * 3_600))
    }

    @Test
    fun durationParts_multi_day() {
        assertEquals(DurationParts(2, 0, 0, 0), HomeStatusFormatter.durationParts(2 * 86_400L))
        assertEquals(DurationParts(7, 0, 0, 0), HomeStatusFormatter.durationParts(7 * 86_400L))
    }

    // endregion
}
