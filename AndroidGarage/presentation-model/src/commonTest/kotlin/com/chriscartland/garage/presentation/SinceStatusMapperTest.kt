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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Shared (commonTest) contract for [SinceStatusMapper] — the Home status-line
 * elapsed-bucket logic. Ported from `androidApp`'s `HomeStatusFormatterTest`
 * `durationParts` region in the presentation-model realization (ADR-031); the
 * granularity rule now runs on every platform (Android + iOS).
 */
class SinceStatusMapperTest {
    private fun elapsed(totalSeconds: Long): ElapsedDuration =
        SinceStatusMapper.forEvent(lastChangeEpochSeconds = 0L, nowEpochSeconds = totalSeconds)!!.elapsed

    @Test
    fun null_timestamp_returns_null() = assertNull(SinceStatusMapper.forEvent(lastChangeEpochSeconds = null, nowEpochSeconds = 1_000L))

    @Test
    fun carries_the_since_timestamp() {
        val status = SinceStatusMapper.forEvent(lastChangeEpochSeconds = 900L, nowEpochSeconds = 1_000L)
        assertEquals(900L, status!!.sinceEpochSeconds)
    }

    @Test
    fun zero_is_seconds_zero() = assertEquals(ElapsedDuration.Seconds(0), elapsed(0))

    @Test
    fun negative_is_clamped_to_zero() =
        // now < since (wall-clock skew) must not produce a negative duration.
        assertEquals(ElapsedDuration.Seconds(0), SinceStatusMapper.forEvent(1_000L, 900L)!!.elapsed)

    @Test
    fun seconds_under_a_minute() {
        assertEquals(ElapsedDuration.Seconds(1), elapsed(1))
        assertEquals(ElapsedDuration.Seconds(38), elapsed(38))
        assertEquals(ElapsedDuration.Seconds(59), elapsed(59))
    }

    @Test
    fun minutes_under_an_hour_drop_seconds() {
        assertEquals(ElapsedDuration.Minutes(1), elapsed(60))
        assertEquals(ElapsedDuration.Minutes(4), elapsed(60L * 4))
        // 38 min 30 sec → just "38 min" (the line drops sub-minute at this granularity).
        assertEquals(ElapsedDuration.Minutes(38), elapsed(60L * 38 + 30))
        assertEquals(ElapsedDuration.Minutes(59), elapsed(60L * 59 + 59))
    }

    @Test
    fun hours_decompose_into_hours_and_minutes() {
        assertEquals(ElapsedDuration.HoursMinutes(1, 0), elapsed(3_600))
        assertEquals(ElapsedDuration.HoursMinutes(2, 14), elapsed(2 * 3_600L + 14 * 60))
        assertEquals(ElapsedDuration.HoursMinutes(23, 59), elapsed(23 * 3_600L + 59 * 60))
    }

    @Test
    fun one_day_drops_leftover_hours() {
        assertEquals(ElapsedDuration.Days(1), elapsed(86_400))
        // 1 day 5 hours → just "1 day".
        assertEquals(ElapsedDuration.Days(1), elapsed(86_400 + 5 * 3_600))
    }

    @Test
    fun multi_day() {
        assertEquals(ElapsedDuration.Days(2), elapsed(2 * 86_400L))
        assertEquals(ElapsedDuration.Days(7), elapsed(7 * 86_400L))
    }
}
