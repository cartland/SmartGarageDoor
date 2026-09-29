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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

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

    private fun shortTime(
        epochSeconds: Long,
        zone: ZoneOffset,
        locale: Locale,
    ): String =
        Instant
            .ofEpochSecond(epochSeconds)
            .atZone(zone)
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

    @Test
    fun formatTime_rendersTheLocalesShortTime() {
        // Compared against the JDK's own localized rendering rather than a
        // literal: CLDR's AM/PM spacing changed across JDK versions, and the
        // property under test is the hour cycle, not the glyph.
        val t = Instant.parse("2026-04-29T10:15:00Z").epochSecond
        val rendered = HistoryFormatter.formatTime(t, ZoneOffset.UTC, Locale.US)
        assertEquals(shortTime(t, ZoneOffset.UTC, Locale.US), rendered)
        assertTrue("US is a 12-hour locale: $rendered", rendered.endsWith("AM"))
    }

    @Test
    fun formatTime_zoneOffsetShifts() {
        // 10:15 UTC is 7:15 at UTC-3
        val t = Instant.parse("2026-04-29T10:15:00Z").epochSecond
        val rendered = HistoryFormatter.formatTime(t, ZoneOffset.ofHours(-3), Locale.US)
        assertEquals(shortTime(t, ZoneOffset.ofHours(-3), Locale.US), rendered)
        assertTrue(rendered, rendered.startsWith("7:15"))
    }

    @Test
    fun formatTime_followsA24HourLocale() {
        // Strategy 1.8: a 24-hour device used to see AM/PM here regardless.
        val t = Instant.parse("2026-04-29T20:30:00Z").epochSecond
        assertEquals("20:30", HistoryFormatter.formatTime(t, ZoneOffset.UTC, Locale.GERMANY))
    }

    // ---------- formatDate ----------

    @Test
    fun formatDate_monday() {
        assertEquals("Mon, Apr 27", HistoryFormatter.formatDate(LocalDate.parse("2026-04-27"), Locale.US))
    }

    @Test
    fun formatDate_wednesday() {
        assertEquals("Wed, Apr 22", HistoryFormatter.formatDate(LocalDate.parse("2026-04-22"), Locale.US))
    }
}
