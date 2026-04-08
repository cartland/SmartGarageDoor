package com.chriscartland.garage.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class FriendlyDurationTest {
    @Test
    fun zeroDuration() {
        assertEquals("0s", FriendlyDuration.format(0.seconds))
    }

    @Test
    fun secondsOnly() {
        assertEquals("45s", FriendlyDuration.format(45.seconds))
    }

    @Test
    fun minutesAndSeconds() {
        assertEquals("5m 30s", FriendlyDuration.format(5.minutes + 30.seconds))
    }

    @Test
    fun hoursMinutesSeconds() {
        assertEquals("2h 15m 30s", FriendlyDuration.format(2.hours + 15.minutes + 30.seconds))
    }

    @Test
    fun exactlyOneHour() {
        assertEquals("1h 0m 0s", FriendlyDuration.format(1.hours))
    }

    @Test
    fun exactlyOneMinute() {
        assertEquals("1m 0s", FriendlyDuration.format(1.minutes))
    }

    @Test
    fun oneDay() {
        assertEquals("1 day, 0h 0m 0s", FriendlyDuration.format(1.days))
    }

    @Test
    fun multipleDays() {
        assertEquals("3 days, 5h 30m 0s", FriendlyDuration.format(3.days + 5.hours + 30.minutes))
    }

    @Test
    fun negativeDurationTreatedAsZero() {
        assertEquals("0s", FriendlyDuration.format((-5).seconds))
    }
}
