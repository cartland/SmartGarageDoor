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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

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

    private fun shortTime(
        instant: Instant,
        locale: Locale,
    ): String = instant.atZone(zone).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

    @Test
    fun formatTimeOrDate_sameDay_shows_only_the_locales_time() {
        // 9:47 AM UTC on 2026-04-29, compared against the JDK's own localized
        // rendering rather than a literal: CLDR changed the space before AM/PM
        // across JDK versions, and the point here is the DECISION (time alone)
        // plus the hour cycle, not the glyph.
        val instant = Instant.parse("2026-04-29T09:47:00Z")
        val rendered = HomeStatusFormatter.formatTimeOrDate(instant, now, zone, Locale.US)
        assertEquals(shortTime(instant, Locale.US), rendered)
        assertTrue("US is a 12-hour locale: $rendered", rendered.endsWith("AM"))
    }

    @Test
    fun formatTimeOrDate_differentDay_prefixes_the_date() {
        val instant = Instant.parse("2026-04-28T21:47:00Z")
        val rendered = HomeStatusFormatter.formatTimeOrDate(instant, now, zone, Locale.US)
        assertEquals("Apr 28, " + shortTime(instant, Locale.US), rendered)
        assertTrue("US is a 12-hour locale: $rendered", rendered.endsWith("PM"))
    }

    @Test
    fun formatTimeOrDate_differentMonth() {
        val instant = Instant.parse("2026-03-15T08:05:00Z")
        val rendered = HomeStatusFormatter.formatTimeOrDate(instant, now, zone, Locale.US)
        assertEquals("Mar 15, " + shortTime(instant, Locale.US), rendered)
    }

    @Test
    fun formatTimeOrDate_follows_a_24_hour_locale() {
        // The whole reason for strategy 1.8: a device in a 24-hour locale used
        // to see AM/PM here anyway.
        val instant = Instant.parse("2026-04-29T21:47:00Z")
        assertEquals("21:47", HomeStatusFormatter.formatTimeOrDate(instant, now, zone, Locale.GERMANY))
    }

    // endregion

    // (region durationParts removed — the elapsed-bucket logic moved to the
    //  shared `presentation-model` (ADR-031); see `SinceStatusMapperTest`.)
}
