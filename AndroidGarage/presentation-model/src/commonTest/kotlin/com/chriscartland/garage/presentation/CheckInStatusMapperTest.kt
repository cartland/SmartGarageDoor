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

package com.chriscartland.garage.presentation

import com.chriscartland.garage.presentation.CheckInStatus.NoData
import com.chriscartland.garage.presentation.CheckInStatus.Reported
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Shared contract for the device check-in bucketing + staleness rule. Ported
 * from `androidApp`'s `DeviceCheckInTest` at the typed level (ADR-031) so the
 * decision is guarded on every platform; Android keeps `DeviceCheckInTest` to
 * cover its "… ago" string formatting, and iOS mirrors that formatting in Swift.
 */
class CheckInStatusMapperTest {
    private fun status(
        lastCheckIn: Long?,
        now: Long,
    ): CheckInStatus = CheckInStatusMapper.forCheckIn(lastCheckInEpochSeconds = lastCheckIn, nowEpochSeconds = now)

    private fun age(
        lastCheckIn: Long?,
        now: Long,
    ): CheckInAge = (status(lastCheckIn, now) as Reported).age

    @Test
    fun nullLastCheckIn_isNoData() {
        assertEquals(NoData, status(null, 1_000L))
    }

    @Test
    fun zeroAge_isJustNow() {
        assertEquals(CheckInAge.JustNow, age(1_000L, 1_000L))
    }

    @Test
    fun underTenSeconds_isJustNow() {
        // "Just now" covers ages < 10s so we never show "9 sec ago" between ticks.
        assertEquals(CheckInAge.JustNow, age(1_000L, 1_009L))
    }

    @Test
    fun tenSeconds_isSeconds() {
        assertEquals(CheckInAge.Seconds(10), age(1_000L, 1_010L))
    }

    @Test
    fun thirtySeconds_isSeconds() {
        assertEquals(CheckInAge.Seconds(30), age(1_000L, 1_030L))
    }

    @Test
    fun oneMinuteEven_carriesZeroSeconds() {
        assertEquals(CheckInAge.Minutes(minutes = 1, seconds = 0), age(1_000L, 1_060L))
    }

    @Test
    fun oneMinuteThirty_carriesLeftoverSeconds() {
        assertEquals(CheckInAge.Minutes(minutes = 1, seconds = 30), age(1_000L, 1_090L))
    }

    @Test
    fun belowStaleThreshold_isNotStale() {
        // 10 min < 11 min threshold
        assertFalse((status(0L, 600L) as Reported).isStale)
    }

    @Test
    fun atStaleThreshold_isNotStale() {
        // Threshold is exclusive (> threshold, not >=)
        assertFalse((status(0L, 660L) as Reported).isStale)
    }

    @Test
    fun aboveStaleThreshold_isStale() {
        // 11 min + 1 sec
        assertTrue((status(0L, 661L) as Reported).isStale)
    }

    @Test
    fun oneHourEven_carriesZeroMinutes() {
        assertEquals(CheckInAge.Hours(hours = 1, minutes = 0), age(0L, 3_600L))
    }

    @Test
    fun oneHourTwenty_carriesLeftoverMinutes() {
        assertEquals(CheckInAge.Hours(hours = 1, minutes = 20), age(0L, 4_800L))
    }

    @Test
    fun oneDay_isSingularDay() {
        assertEquals(CheckInAge.Days(1), age(0L, 86_400L))
    }

    @Test
    fun threeDays_isPluralDays() {
        assertEquals(CheckInAge.Days(3), age(0L, 86_400L * 3))
    }

    @Test
    fun negativeAge_clampedToJustNow() {
        // Clock-skew protection: never render "-30 sec ago".
        val reported = status(1_000L, 970L) as Reported
        assertEquals(CheckInAge.JustNow, reported.age)
        assertFalse(reported.isStale)
    }

    @Test
    fun customStaleThreshold_respected() {
        val reported =
            CheckInStatusMapper.forCheckIn(
                lastCheckInEpochSeconds = 0L,
                nowEpochSeconds = 100L,
                staleThresholdSeconds = 50L,
            ) as Reported
        assertTrue(reported.isStale)
    }
}
