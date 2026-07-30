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

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Tests for [HistoryFormatter].
 *
 * Covers clock-time and date-label formatting. The duration-bucket tests
 * that used to live here moved to `HistoryDurationMapperTest` in
 * `presentation-model` along with the logic — the ladders are shared now, so
 * testing them once covers both platforms.
 */
class HistoryFormatterTest {
    // ---------- formatTime ----------

    @Test
    fun formatTime_midnightUTC() {
        val t = Instant.parse("2026-04-29T00:00:00Z").epochSecond
        assertEquals("12:00 AM", HistoryFormatter.formatTime(t, ZoneOffset.UTC))
    }

    @Test
    fun formatTime_noonUTC() {
        val t = Instant.parse("2026-04-29T12:00:00Z").epochSecond
        assertEquals("12:00 PM", HistoryFormatter.formatTime(t, ZoneOffset.UTC))
    }

    @Test
    fun formatTime_morningUTC() {
        val t = Instant.parse("2026-04-29T10:15:00Z").epochSecond
        assertEquals("10:15 AM", HistoryFormatter.formatTime(t, ZoneOffset.UTC))
    }

    @Test
    fun formatTime_eveningUTC() {
        val t = Instant.parse("2026-04-28T20:30:00Z").epochSecond
        assertEquals("8:30 PM", HistoryFormatter.formatTime(t, ZoneOffset.UTC))
    }

    @Test
    fun formatTime_zoneOffsetShifts() {
        // 10:15 AM in UTC = 7:15 AM in UTC-3
        val t = Instant.parse("2026-04-29T10:15:00Z").epochSecond
        assertEquals("7:15 AM", HistoryFormatter.formatTime(t, ZoneOffset.ofHours(-3)))
    }

    // ---------- formatDate ----------

    @Test
    fun formatDate_monday() {
        assertEquals("Mon, Apr 27", HistoryFormatter.formatDate(LocalDate.parse("2026-04-27")))
    }

    @Test
    fun formatDate_wednesday() {
        assertEquals("Wed, Apr 22", HistoryFormatter.formatDate(LocalDate.parse("2026-04-22")))
    }
}
