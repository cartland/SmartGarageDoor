package com.chriscartland.garage.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class StalenessTest {
    @Test
    fun notStaleWhenRecent() {
        assertFalse(
            Staleness.isStale(
                lastCheckInTimeSeconds = 1000L,
                maxAge = 5.minutes,
                nowEpochSeconds = 1100L, // 100s ago, under 5min
            ),
        )
    }

    @Test
    fun staleWhenOld() {
        assertTrue(
            Staleness.isStale(
                lastCheckInTimeSeconds = 1000L,
                maxAge = 5.minutes,
                nowEpochSeconds = 1400L, // 400s ago, over 5min
            ),
        )
    }

    @Test
    fun exactlyAtBoundaryIsNotStale() {
        assertFalse(
            Staleness.isStale(
                lastCheckInTimeSeconds = 1000L,
                maxAge = 5.minutes,
                nowEpochSeconds = 1300L, // exactly 5min
            ),
        )
    }

    @Test
    fun nullTimestampIsNotStale() {
        assertFalse(
            Staleness.isStale(
                lastCheckInTimeSeconds = null,
                maxAge = 5.minutes,
                nowEpochSeconds = 9999L,
            ),
        )
    }
}
