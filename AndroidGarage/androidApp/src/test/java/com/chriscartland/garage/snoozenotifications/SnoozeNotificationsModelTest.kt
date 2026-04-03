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

package com.chriscartland.garage.snoozenotifications

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.hours

class SnoozeNotificationsModelTest {
    @Test
    fun noneToServerHours0() {
        assertEquals(SnoozeDurationServerOption.HOURS_0, SnoozeDurationUIOption.None.toServer())
    }

    @Test
    fun oneHourToServerHours1() {
        assertEquals(SnoozeDurationServerOption.HOURS_1, SnoozeDurationUIOption.OneHour.toServer())
    }

    @Test
    fun fourHoursToServerHours4() {
        assertEquals(SnoozeDurationServerOption.HOURS_4, SnoozeDurationUIOption.FourHours.toServer())
    }

    @Test
    fun eightHoursToServerHours8() {
        assertEquals(SnoozeDurationServerOption.HOURS_8, SnoozeDurationUIOption.EightHours.toServer())
    }

    @Test
    fun twelveHoursToServerHours12() {
        assertEquals(SnoozeDurationServerOption.HOURS_12, SnoozeDurationUIOption.TwelveHours.toServer())
    }

    @Test
    fun serverOptionToParamWrapsString() {
        val param = SnoozeDurationServerOption.HOURS_4.toParam()
        assertEquals(SnoozeDurationServerOption.HOURS_4.toParam(), param)
    }

    @Test
    fun uiOptionDurationsAreCorrect() {
        assertEquals(0.hours, SnoozeDurationUIOption.None.duration)
        assertEquals(1.hours, SnoozeDurationUIOption.OneHour.duration)
        assertEquals(4.hours, SnoozeDurationUIOption.FourHours.duration)
        assertEquals(8.hours, SnoozeDurationUIOption.EightHours.duration)
        assertEquals(12.hours, SnoozeDurationUIOption.TwelveHours.duration)
    }
}
