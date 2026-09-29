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

import com.chriscartland.garage.presentation.CheckInAge
import com.chriscartland.garage.presentation.CheckInStatus
import com.chriscartland.garage.presentation.DataFreshness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The display carries the shared typed verdict; these pin which age it
 * carries for each heartbeat and when it may alarm. The WORDS for an age
 * are [DeviceCheckInWordsTest]'s to pin.
 */
class DeviceCheckInTest {
    @Test
    fun format_nullLastCheckIn_isNoDataAndNeverAlarms() {
        val display = DeviceCheckIn.format(lastCheckInSeconds = null, nowSeconds = 1_000L)
        assertEquals(CheckInStatus.NoData, display.status)
        assertFalse(display.isStale)
    }

    @Test
    fun format_zeroAge_isJustNow() {
        assertEquals(CheckInAge.JustNow, ageOf(lastCheckInSeconds = 1_000L, nowSeconds = 1_000L))
    }

    @Test
    fun format_underTenSeconds_isJustNow() {
        // The LiveClock ticks at 10s; "Just now" covers ages < 10s so we
        // never show the awkward "9 sec ago" between ticks.
        assertEquals(CheckInAge.JustNow, ageOf(lastCheckInSeconds = 1_000L, nowSeconds = 1_009L))
    }

    @Test
    fun format_tenSeconds_isSeconds() {
        assertEquals(CheckInAge.Seconds(10), ageOf(lastCheckInSeconds = 1_000L, nowSeconds = 1_010L))
    }

    @Test
    fun format_thirtySeconds_isSeconds() {
        assertEquals(CheckInAge.Seconds(30), ageOf(lastCheckInSeconds = 1_000L, nowSeconds = 1_030L))
    }

    @Test
    fun format_oneMinuteEven_hasNoSecondsComponent() {
        assertEquals(CheckInAge.Minutes(minutes = 1, seconds = 0), ageOf(lastCheckInSeconds = 1_000L, nowSeconds = 1_060L))
    }

    @Test
    fun format_oneMinuteThirtySeconds_keepsTheSecondsComponent() {
        assertEquals(CheckInAge.Minutes(minutes = 1, seconds = 30), ageOf(lastCheckInSeconds = 1_000L, nowSeconds = 1_090L))
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
    fun format_oneHourEven_hasNoMinutesComponent() {
        assertEquals(CheckInAge.Hours(hours = 1, minutes = 0), ageOf(lastCheckInSeconds = 0L, nowSeconds = 3_600L))
    }

    @Test
    fun format_oneHourTwentyMinutes_keepsTheMinutesComponent() {
        assertEquals(CheckInAge.Hours(hours = 1, minutes = 20), ageOf(lastCheckInSeconds = 0L, nowSeconds = 4_800L))
    }

    @Test
    fun format_oneDay_isOneDay() {
        assertEquals(CheckInAge.Days(1), ageOf(lastCheckInSeconds = 0L, nowSeconds = 86_400L))
    }

    @Test
    fun format_threeDays_isThreeDays() {
        assertEquals(CheckInAge.Days(3), ageOf(lastCheckInSeconds = 0L, nowSeconds = 3 * 86_400L))
    }

    @Test
    fun format_negativeAge_clampedToJustNow() {
        // A check-in "from the future" (clock skew) reads as just now, and
        // never alarms.
        val display = DeviceCheckIn.format(lastCheckInSeconds = 2_000L, nowSeconds = 1_000L)
        assertEquals(CheckInAge.JustNow, (display.status as CheckInStatus.Reported).age)
        assertFalse(display.isStale)
    }

    /**
     * The settle window: an aged heartbeat keeps its AGE (the same words
     * the user would read once the window closes, since `freshness` is
     * STALE by default) and only the alarm is held.
     */
    @Test
    fun format_settling_keepsTheAgeButDropsTheAlarm() {
        val display =
            DeviceCheckIn.format(
                lastCheckInSeconds = 0L,
                nowSeconds = 661L,
                freshness = DataFreshness.SETTLING,
            )
        val reported = display.status as CheckInStatus.Reported
        assertEquals(CheckInAge.Minutes(minutes = 11, seconds = 1), reported.age)
        assertTrue("the shared verdict still knows the heartbeat is stale", reported.isStale)
        assertFalse("the pill must not alarm inside the settle window", display.isStale)
    }

    private fun ageOf(
        lastCheckInSeconds: Long,
        nowSeconds: Long,
    ): CheckInAge {
        val status = DeviceCheckIn.format(lastCheckInSeconds = lastCheckInSeconds, nowSeconds = nowSeconds).status
        return (status as CheckInStatus.Reported).age
    }
}
