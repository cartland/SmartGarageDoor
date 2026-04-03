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

package com.chriscartland.garage.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class TimeFormatsTest {
    @Test
    fun zeroSecondsDuration() {
        assertEquals("0s", Duration.ZERO.toFriendlyDuration())
    }

    @Test
    fun secondsOnlyDuration() {
        assertEquals("45s", Duration.ofSeconds(45).toFriendlyDuration())
    }

    @Test
    fun minutesAndSecondsDuration() {
        assertEquals("5m 30s", Duration.ofSeconds(5 * 60 + 30).toFriendlyDuration())
    }

    @Test
    fun hoursMinutesSecondsDuration() {
        assertEquals(
            "2h 15m 30s",
            Duration.ofSeconds(2 * 3600 + 15 * 60 + 30).toFriendlyDuration(),
        )
    }

    @Test
    fun exactlyOneHourDuration() {
        assertEquals("1h 0m 0s", Duration.ofHours(1).toFriendlyDuration())
    }

    @Test
    fun exactlyOneMinuteDuration() {
        assertEquals("1m 0s", Duration.ofMinutes(1).toFriendlyDuration())
    }

    @Test
    fun oneDayDuration() {
        val duration = Duration.ofDays(1)
        assertEquals("1 day, 0h 0m 0s", duration.toFriendlyDuration())
    }

    @Test
    fun multipleDaysDuration() {
        val duration = Duration.ofDays(3).plusHours(5).plusMinutes(30)
        assertEquals("3 days, 5h 30m 0s", duration.toFriendlyDuration())
    }

    @Test
    fun toFriendlyDateReturnsNonEmpty() {
        // 2024-01-15 00:00:00 UTC
        val timestamp = 1705276800L
        val result = timestamp.toFriendlyDate()
        assertTrue("toFriendlyDate should return non-empty string", result.isNotEmpty())
    }

    @Test
    fun toFriendlyTimeReturnsNonEmpty() {
        val timestamp = 1705276800L
        val result = timestamp.toFriendlyTime()
        assertTrue("toFriendlyTime should return non-empty string", !result.isNullOrEmpty())
    }
}
