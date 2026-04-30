/*
 * Copyright 2024 Chris Cartland. All rights reserved.
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCheckInTest {
    @Test
    fun format_nullLastCheckIn_returnsNoDataLabel() {
        val display = DeviceCheckIn.format(lastCheckInSeconds = null, nowSeconds = 1_000L)
        assertEquals("No data yet", display.durationLabel)
        assertFalse(display.isStale)
    }

    @Test
    fun format_zeroAge_returnsJustNow() {
        val display = DeviceCheckIn.format(lastCheckInSeconds = 1_000L, nowSeconds = 1_000L)
        assertEquals("Just now", display.durationLabel)
    }

    @Test
    fun format_underTenSeconds_returnsJustNow() {
        // The LiveClock ticks at 10s; "Just now" covers ages < 10s so we
        // never show the awkward "9 sec ago" between ticks.
        val display = DeviceCheckIn.format(lastCheckInSeconds = 1_000L, nowSeconds = 1_009L)
        assertEquals("Just now", display.durationLabel)
    }

    @Test
    fun format_tenSeconds_returnsSecondsLabel() {
        val display = DeviceCheckIn.format(lastCheckInSeconds = 1_000L, nowSeconds = 1_010L)
        assertEquals("10 sec ago", display.durationLabel)
    }

    @Test
    fun format_thirtySeconds_returnsSecondsLabel() {
        val display = DeviceCheckIn.format(lastCheckInSeconds = 1_000L, nowSeconds = 1_030L)
        assertEquals("30 sec ago", display.durationLabel)
    }

    @Test
    fun format_oneMinuteEven_omitsSecondsComponent() {
        val display = DeviceCheckIn.format(lastCheckInSeconds = 1_000L, nowSeconds = 1_060L)
        assertEquals("1 min ago", display.durationLabel)
    }

    @Test
    fun format_oneMinuteThirtySeconds_includesSecondsComponent() {
        val display = DeviceCheckIn.format(lastCheckInSeconds = 1_000L, nowSeconds = 1_090L)
        assertEquals("1 min 30 sec ago", display.durationLabel)
    }

    @Test
    fun format_belowStaleThreshold_isNotStale() {
        // 10 minutes < 11 minute threshold
        val display = DeviceCheckIn.format(lastCheckInSeconds = 0L, nowSeconds = 600L)
        assertFalse(display.isStale)
    }

    @Test
    fun format_atStaleThreshold_isNotStale() {
        // Threshold is exclusive (> threshold, not >=)
        val display = DeviceCheckIn.format(lastCheckInSeconds = 0L, nowSeconds = 660L)
        assertFalse(display.isStale)
    }

    @Test
    fun format_aboveStaleThreshold_isStale() {
        // 11 min + 1 sec
        val display = DeviceCheckIn.format(lastCheckInSeconds = 0L, nowSeconds = 661L)
        assertTrue(display.isStale)
    }

    @Test
    fun format_oneHourEven_omitsMinutesComponent() {
        val display = DeviceCheckIn.format(lastCheckInSeconds = 0L, nowSeconds = 3_600L)
        assertEquals("1 hr ago", display.durationLabel)
    }

    @Test
    fun format_oneHourTwentyMinutes_includesMinutesComponent() {
        val display = DeviceCheckIn.format(lastCheckInSeconds = 0L, nowSeconds = 4_800L)
        assertEquals("1 hr 20 min ago", display.durationLabel)
    }

    @Test
    fun format_oneDay_returnsSingularDayLabel() {
        val display = DeviceCheckIn.format(lastCheckInSeconds = 0L, nowSeconds = 86_400L)
        assertEquals("1 day ago", display.durationLabel)
    }

    @Test
    fun format_threeDays_returnsPluralDaysLabel() {
        val display = DeviceCheckIn.format(lastCheckInSeconds = 0L, nowSeconds = 86_400L * 3)
        assertEquals("3 days ago", display.durationLabel)
    }

    @Test
    fun format_negativeAge_clampedToJustNow() {
        // Clock skew protection: if `now` is somehow earlier than the check-in,
        // we don't render "-30 sec ago".
        val display = DeviceCheckIn.format(lastCheckInSeconds = 1_000L, nowSeconds = 970L)
        assertEquals("Just now", display.durationLabel)
        assertFalse(display.isStale)
    }
}
