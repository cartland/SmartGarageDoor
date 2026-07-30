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
import kotlin.test.assertTrue

class HistoryDurationMapperTest {
    private val minute = 60L
    private val hour = 3_600L
    private val day = 86_400L

    // ---- state span ladder ----

    @Test
    fun stateSpanPicksTheExpectedBucket() {
        assertEquals(StateSpanDuration.Seconds(0), HistoryDurationMapper.stateSpanDuration(0))
        assertEquals(StateSpanDuration.Seconds(59), HistoryDurationMapper.stateSpanDuration(59))
        assertEquals(StateSpanDuration.Minutes(1), HistoryDurationMapper.stateSpanDuration(minute))
        assertEquals(StateSpanDuration.Minutes(59), HistoryDurationMapper.stateSpanDuration(59 * minute + 59))
        assertEquals(StateSpanDuration.Hours(1), HistoryDurationMapper.stateSpanDuration(hour))
        assertEquals(StateSpanDuration.HoursMinutes(2, 14), HistoryDurationMapper.stateSpanDuration(2 * hour + 14 * minute))
        assertEquals(StateSpanDuration.Days(1), HistoryDurationMapper.stateSpanDuration(day))
        assertEquals(StateSpanDuration.DaysHours(1, 5), HistoryDurationMapper.stateSpanDuration(day + 5 * hour))
    }

    @Test
    fun stateSpanDropsSecondsAtMinuteScaleAndAbove() {
        // The defining difference from the transit ladder. A state span of
        // "2 hr 14 min 37 sec" reads as "2 hr 14 min" — the seconds are noise.
        assertEquals(
            HistoryDurationMapper.stateSpanDuration(2 * hour + 14 * minute),
            HistoryDurationMapper.stateSpanDuration(2 * hour + 14 * minute + 37),
        )
        assertEquals(
            HistoryDurationMapper.stateSpanDuration(5 * minute),
            HistoryDurationMapper.stateSpanDuration(5 * minute + 59),
        )
    }

    @Test
    fun stateSpanDropsMinutesAtDayScale() {
        assertEquals(
            HistoryDurationMapper.stateSpanDuration(3 * day + 7 * hour),
            HistoryDurationMapper.stateSpanDuration(3 * day + 7 * hour + 42 * minute),
        )
    }

    @Test
    fun stateSpanSeparatesOnTheHourFromWithLeftover() {
        // Exactly-on-the-unit gets its own arm so the platform can render
        // "2 hr" rather than "2 hr 0 min".
        assertEquals(StateSpanDuration.Hours(2), HistoryDurationMapper.stateSpanDuration(2 * hour))
        assertEquals(StateSpanDuration.HoursMinutes(2, 1), HistoryDurationMapper.stateSpanDuration(2 * hour + minute))
        assertEquals(StateSpanDuration.Days(2), HistoryDurationMapper.stateSpanDuration(2 * day))
        assertEquals(StateSpanDuration.DaysHours(2, 1), HistoryDurationMapper.stateSpanDuration(2 * day + hour))
    }

    // ---- transit span ladder ----

    @Test
    fun transitSpanPicksTheExpectedBucket() {
        assertEquals(TransitSpanDuration.Seconds(0), HistoryDurationMapper.transitSpan(0))
        assertEquals(TransitSpanDuration.Seconds(45), HistoryDurationMapper.transitSpan(45))
        assertEquals(TransitSpanDuration.Minutes(2), HistoryDurationMapper.transitSpan(2 * minute))
        assertEquals(TransitSpanDuration.MinutesSeconds(2, 30), HistoryDurationMapper.transitSpan(2 * minute + 30))
        assertEquals(TransitSpanDuration.Hours(1), HistoryDurationMapper.transitSpan(hour))
        assertEquals(TransitSpanDuration.HoursMinutes(1, 5), HistoryDurationMapper.transitSpan(hour + 5 * minute))
    }

    @Test
    fun transitSpanKeepsSecondsAtMinuteScale() {
        // The whole reason this is a separate ladder: for a slow door, the
        // seconds ARE the interesting part. Contrast the state-span test above.
        assertTrue(
            HistoryDurationMapper.transitSpan(2 * minute + 30) !=
                HistoryDurationMapper.transitSpan(2 * minute),
        )
        assertEquals(TransitSpanDuration.MinutesSeconds(2, 30), HistoryDurationMapper.transitSpan(2 * minute + 30))
    }

    @Test
    fun theTwoLaddersDisagreeAtMinuteScaleAndThatIsTheContract() {
        // Same input, deliberately different granularity. Pinned so a future
        // "cleanup" that unifies them fails here with an explanation rather
        // than silently changing every transit tag in the history list.
        val seconds = 3 * minute + 20
        assertEquals(StateSpanDuration.Minutes(3), HistoryDurationMapper.stateSpanDuration(seconds))
        assertEquals(TransitSpanDuration.MinutesSeconds(3, 20), HistoryDurationMapper.transitSpan(seconds))
    }

    // ---- clamping ----

    @Test
    fun negativeSpansClampToZero() {
        // Clock skew between device and server. Better than a negative duration.
        assertEquals(StateSpanDuration.Seconds(0), HistoryDurationMapper.stateSpanDuration(-1))
        assertEquals(StateSpanDuration.Seconds(0), HistoryDurationMapper.stateSpanDuration(-100_000))
        assertEquals(TransitSpanDuration.Seconds(0), HistoryDurationMapper.transitSpan(-1))
        assertEquals(TransitSpanDuration.Seconds(0), HistoryDurationMapper.transitSpan(-100_000))
    }

    // ---- framing ----

    @Test
    fun ongoingSpansOutrankWhichStateTheyAreIn() {
        // The precedence both platforms encoded by hand: isCurrent is checked
        // first, so an open door right now reads "and counting", not "Open for".
        listOf(true, false).forEach { isOpen ->
            assertEquals(
                StateSpanFraming.AND_COUNTING,
                HistoryDurationMapper.stateSpan(hour, isCurrent = true, isOpen = isOpen).framing,
                "isCurrent should win regardless of isOpen=$isOpen",
            )
        }
    }

    @Test
    fun completedSpansAreFramedByState() {
        assertEquals(
            StateSpanFraming.OPEN_FOR,
            HistoryDurationMapper.stateSpan(hour, isCurrent = false, isOpen = true).framing,
        )
        assertEquals(
            StateSpanFraming.CLOSED_FOR,
            HistoryDurationMapper.stateSpan(hour, isCurrent = false, isOpen = false).framing,
        )
    }

    @Test
    fun framingDoesNotDisturbTheDuration() {
        // The two halves are independent; bucketing must not depend on framing.
        val seconds = 2 * hour + 14 * minute
        val expected = HistoryDurationMapper.stateSpanDuration(seconds)
        listOf(true to true, true to false, false to true, false to false).forEach { (current, open) ->
            assertEquals(
                expected,
                HistoryDurationMapper.stateSpan(seconds, isCurrent = current, isOpen = open).duration,
            )
        }
    }
}
