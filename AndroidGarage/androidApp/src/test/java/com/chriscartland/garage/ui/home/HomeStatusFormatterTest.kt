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
 * Covers the Android-only clock-time formatting. The duration-decomposition
 * logic moved to the shared `presentation-model` (`SinceStatusMapper` →
 * `ElapsedDuration`) in the presentation-model realization (ADR-031) and is
 * tested by `SinceStatusMapperTest` in that module's commonTest, so it now runs
 * on every platform.
 *
 * The localized "Since X · Y" assembly happens in `rememberSinceLine`
 * (Composable, in `HomeContent.kt`) and is verified via screenshot tests + the
 * `home_*` resources in `strings.xml` / `plurals.xml`.
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

    // (region durationParts removed — the elapsed-bucket logic moved to the
    //  shared `presentation-model` (ADR-031); see `SinceStatusMapperTest`.)
}
