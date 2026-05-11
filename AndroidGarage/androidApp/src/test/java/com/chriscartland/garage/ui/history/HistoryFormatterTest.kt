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
 * Phase 2E of the string-resource migration plan
 * (`AndroidGarage/docs/PENDING_FOLLOWUPS.md` item #1) — `formatTime`,
 * `stateDurationParts`, `transitDurationParts`, and `formatDate` were
 * moved here from `HistoryMapper`. The Composable layer assembles the
 * final localized strings via `stringResource` + `pluralStringResource`.
 *
 * Tests cover the pure-function decomposition; the localized
 * "Open for X" / "Took Y to open" assembly is screenshot-tested in the
 * `HistoryContent` previews.
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

    // ---------- stateDurationParts ----------

    @Test
    fun stateDurationParts_zero() {
        assertEquals(StateDurationParts(0, 0, 0, 0), HistoryFormatter.stateDurationParts(0))
    }

    @Test
    fun stateDurationParts_negativeClampsToZero() {
        assertEquals(StateDurationParts(0, 0, 0, 0), HistoryFormatter.stateDurationParts(-100))
    }

    @Test
    fun stateDurationParts_secondsOnly() {
        assertEquals(StateDurationParts(0, 0, 0, 30), HistoryFormatter.stateDurationParts(30))
        assertEquals(StateDurationParts(0, 0, 0, 59), HistoryFormatter.stateDurationParts(59))
    }

    @Test
    fun stateDurationParts_minutesOnly() {
        assertEquals(StateDurationParts(0, 0, 1, 0), HistoryFormatter.stateDurationParts(60))
        assertEquals(StateDurationParts(0, 0, 6, 0), HistoryFormatter.stateDurationParts(6 * 60))
        assertEquals(StateDurationParts(0, 0, 59, 0), HistoryFormatter.stateDurationParts(59 * 60))
    }

    @Test
    fun stateDurationParts_hours() {
        assertEquals(StateDurationParts(0, 1, 0, 0), HistoryFormatter.stateDurationParts(60 * 60))
        assertEquals(StateDurationParts(0, 1, 30, 0), HistoryFormatter.stateDurationParts(90 * 60))
        assertEquals(StateDurationParts(0, 13, 17, 0), HistoryFormatter.stateDurationParts((13 * 60 + 17) * 60L))
    }

    @Test
    fun stateDurationParts_days() {
        assertEquals(StateDurationParts(1, 0, 0, 0), HistoryFormatter.stateDurationParts(24 * 60 * 60))
        assertEquals(StateDurationParts(1, 1, 0, 0), HistoryFormatter.stateDurationParts(25 * 60 * 60))
        assertEquals(StateDurationParts(3, 0, 0, 0), HistoryFormatter.stateDurationParts(3 * 24 * 60 * 60))
    }

    // ---------- transitDurationParts ----------

    @Test
    fun transitDurationParts_zero() {
        assertEquals(TransitDurationParts(0, 0, 0), HistoryFormatter.transitDurationParts(0))
    }

    @Test
    fun transitDurationParts_negativeClamps() {
        assertEquals(TransitDurationParts(0, 0, 0), HistoryFormatter.transitDurationParts(-50))
    }

    @Test
    fun transitDurationParts_secondsOnly() {
        assertEquals(TransitDurationParts(0, 0, 30), HistoryFormatter.transitDurationParts(30))
    }

    @Test
    fun transitDurationParts_minutes() {
        assertEquals(TransitDurationParts(0, 1, 0), HistoryFormatter.transitDurationParts(60))
        assertEquals(TransitDurationParts(0, 1, 30), HistoryFormatter.transitDurationParts(90))
        assertEquals(TransitDurationParts(0, 4, 0), HistoryFormatter.transitDurationParts(240))
    }

    @Test
    fun transitDurationParts_hours() {
        assertEquals(TransitDurationParts(1, 0, 0), HistoryFormatter.transitDurationParts(60 * 60))
        assertEquals(TransitDurationParts(1, 30, 0), HistoryFormatter.transitDurationParts(90 * 60))
        assertEquals(TransitDurationParts(14, 30, 8), HistoryFormatter.transitDurationParts(14 * 3600 + 30 * 60 + 8L))
    }
}
