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

import com.chriscartland.garage.R
import com.chriscartland.garage.presentation.CheckInAge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Which resource each age picks — the decision, pinned on the JVM. The
 * resolved words are the string resources' business, and were the literals
 * `DeviceCheckInTest` used to assert ("5 min 20 sec ago" and friends) before
 * strategy 4.6 moved them out of Kotlin.
 */
class DeviceCheckInWordsTest {
    @Test
    fun justNowIsAPlainString() {
        assertEquals(CheckInAgeResource.Plain(R.string.check_in_just_now), DeviceCheckInWords.resource(CheckInAge.JustNow))
    }

    @Test
    fun secondsAreAPlural() {
        assertEquals(
            CheckInAgeResource.Plural(R.plurals.check_in_seconds_ago, 30),
            DeviceCheckInWords.resource(CheckInAge.Seconds(30)),
        )
    }

    @Test
    fun evenMinutesOmitTheSecondsAndUnevenOnesKeepThem() {
        assertEquals(
            CheckInAgeResource.Plural(R.plurals.check_in_minutes_ago, 5),
            DeviceCheckInWords.resource(CheckInAge.Minutes(minutes = 5, seconds = 0)),
        )
        assertEquals(
            CheckInAgeResource.TwoPart(R.string.check_in_minutes_seconds_ago, 5, 20),
            DeviceCheckInWords.resource(CheckInAge.Minutes(minutes = 5, seconds = 20)),
        )
    }

    @Test
    fun evenHoursOmitTheMinutesAndUnevenOnesKeepThem() {
        assertEquals(
            CheckInAgeResource.Plural(R.plurals.check_in_hours_ago, 1),
            DeviceCheckInWords.resource(CheckInAge.Hours(hours = 1, minutes = 0)),
        )
        assertEquals(
            CheckInAgeResource.TwoPart(R.string.check_in_hours_minutes_ago, 1, 20),
            DeviceCheckInWords.resource(CheckInAge.Hours(hours = 1, minutes = 20)),
        )
    }

    @Test
    fun daysAreAPluralSoOneDayIsSingular() {
        assertEquals(CheckInAgeResource.Plural(R.plurals.check_in_days_ago, 1), DeviceCheckInWords.resource(CheckInAge.Days(1)))
        assertEquals(CheckInAgeResource.Plural(R.plurals.check_in_days_ago, 3), DeviceCheckInWords.resource(CheckInAge.Days(3)))
    }

    @Test
    fun theAgesDoNotAllPickTheSameResource() {
        // Positive control for the equalities above.
        assertNotEquals(
            DeviceCheckInWords.resource(CheckInAge.Seconds(30)),
            DeviceCheckInWords.resource(CheckInAge.Minutes(minutes = 30, seconds = 0)),
        )
    }
}
