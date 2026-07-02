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

package com.chriscartland.garage.fcm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class DoorResolvedNotificationTextTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val us = Locale.US

    // 2026-06-15 14:00:00 UTC.
    private val openSec = 1781532000L

    private fun body(
        openSec: Long,
        closeSec: Long,
    ): String = DoorResolvedNotificationText.body(openSec, closeSec, utc, us)

    @Test
    fun matchesApprovedCopy_fourteenMinutes() {
        // The canonical example: "It was open for 14 minutes (2:00-2:14 PM)."
        val close = openSec + 14 * 60
        assertEquals("It was open for 14 minutes (2:00-2:14 PM).", body(openSec, close))
    }

    @Test
    fun singularMinuteHasNoPluralS() {
        val close = openSec + 60
        assertEquals("It was open for 1 minute (2:00-2:01 PM).", body(openSec, close))
    }

    @Test
    fun subMinuteFloorsToOneMinute() {
        val close = openSec + 30
        assertEquals("It was open for 1 minute (2:00-2:00 PM).", body(openSec, close))
    }

    @Test
    fun exactlyOneHour() {
        val close = openSec + 60 * 60
        assertEquals("It was open for 1 hour (2:00-3:00 PM).", body(openSec, close))
    }

    @Test
    fun hoursAndMinutes() {
        val close = openSec + (2 * 60 + 5) * 60
        assertEquals("It was open for 2 hours 5 minutes (2:00-4:05 PM).", body(openSec, close))
    }

    @Test
    fun crossingNoonShowsBothMeridiems() {
        // 11:50 AM open, 12:10 PM close → meridiems differ, so the start keeps its.
        val open1150 = 1781524200L // 2026-06-15 11:50:00 UTC
        val close = open1150 + 20 * 60
        assertEquals("It was open for 20 minutes (11:50 AM-12:10 PM).", body(open1150, close))
    }

    @Test
    fun titleIsApprovedCopy() {
        assertEquals("Resolved: garage door closed", DoorResolvedNotificationText.TITLE)
    }
}
