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

package com.chriscartland.garage.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Bucket boundaries for the remote-button offline age.
 *
 * These assert the *decision* the shared layer still owns. They deliberately
 * assert no words: the words moved to each platform so they can be translated,
 * and a shared test asserting "11 min ago" would pin English back into place.
 */
class ButtonOfflineAgeMapperTest {
    private val now = 1_000_000L

    @Test
    fun noTimestampIsUnknown() {
        assertEquals(ButtonOfflineAge.Unknown, ButtonOfflineAgeMapper.forTimestamp(null, now))
    }

    @Test
    fun aFutureTimestampClampsToJustNow() {
        // Device and server clocks disagree; a negative age is not meaningful.
        assertEquals(ButtonOfflineAge.JustNow, ButtonOfflineAgeMapper.forTimestamp(now + 100, now))
        assertEquals(ButtonOfflineAge.JustNow, ButtonOfflineAgeMapper.forTimestamp(now, now))
    }

    @Test
    fun secondsHoldUntilAFullMinute() {
        assertEquals(ButtonOfflineAge.Seconds(1), ButtonOfflineAgeMapper.forTimestamp(now - 1, now))
        assertEquals(ButtonOfflineAge.Seconds(59), ButtonOfflineAgeMapper.forTimestamp(now - 59, now))
        assertEquals(ButtonOfflineAge.Minutes(1), ButtonOfflineAgeMapper.forTimestamp(now - 60, now))
    }

    @Test
    fun minutesHoldUntilAFullHour() {
        assertEquals(ButtonOfflineAge.Minutes(59), ButtonOfflineAgeMapper.forTimestamp(now - 59 * 60, now))
        assertEquals(ButtonOfflineAge.Hours(1), ButtonOfflineAgeMapper.forTimestamp(now - 60 * 60, now))
    }

    @Test
    fun hoursHoldUntilAFullDay() {
        assertEquals(ButtonOfflineAge.Hours(23), ButtonOfflineAgeMapper.forTimestamp(now - 23 * 3600, now))
        assertEquals(ButtonOfflineAge.Days(1), ButtonOfflineAgeMapper.forTimestamp(now - 24 * 3600, now))
    }

    @Test
    fun leftoverUnitsAreDroppedNotRounded() {
        // 90 minutes is "1 hr", never "2 hr" — the pill answers "roughly how
        // long", and rounding up would overstate the outage.
        assertEquals(ButtonOfflineAge.Hours(1), ButtonOfflineAgeMapper.forTimestamp(now - 90 * 60, now))
        assertEquals(ButtonOfflineAge.Days(2), ButtonOfflineAgeMapper.forTimestamp(now - (2 * 24 + 23) * 3600, now))
    }
}
