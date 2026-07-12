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

package com.chriscartland.garage.data.statuscache

import kotlin.test.Test
import kotlin.test.assertEquals

class StatusSnapshotTest {
    private fun snapshotAt(
        fetchedAt: Long,
        confirmedAt: Long = fetchedAt,
    ) = StatusSnapshot(
        payload = "value",
        fetchedAtEpochSeconds = fetchedAt,
        confirmedAtEpochSeconds = confirmedAt,
    )

    @Test
    fun ageIsZeroWhenTimestampEqualsNow() {
        assertEquals(0L, snapshotAt(1000L).fetchedAgeSeconds(nowEpochSeconds = 1000L))
    }

    @Test
    fun ageIsElapsedSecondsForPastTimestamp() {
        assertEquals(500L, snapshotAt(1000L).fetchedAgeSeconds(nowEpochSeconds = 1500L))
    }

    @Test
    fun smallFutureSkewIsToleratedAsAgeZero() {
        // Within CLOCK_SKEW_TOLERANCE_SECONDS: treat as fresh (age 0).
        assertEquals(
            0L,
            snapshotAt(1000L + StatusSnapshot.CLOCK_SKEW_TOLERANCE_SECONDS)
                .fetchedAgeSeconds(nowEpochSeconds = 1000L),
        )
    }

    @Test
    fun farFutureTimestampIsForcedStale() {
        // Beyond the tolerance a future timestamp means the wall clock
        // moved backwards — the snapshot must read as maximally stale
        // so it can never suppress revalidation.
        assertEquals(
            Long.MAX_VALUE,
            snapshotAt(1000L + StatusSnapshot.CLOCK_SKEW_TOLERANCE_SECONDS + 1)
                .fetchedAgeSeconds(nowEpochSeconds = 1000L),
        )
    }

    @Test
    fun confirmedAgeUsesConfirmedTimestamp() {
        val snapshot = snapshotAt(fetchedAt = 1000L, confirmedAt = 1800L)
        assertEquals(200L, snapshot.confirmedAgeSeconds(nowEpochSeconds = 2000L))
        assertEquals(1000L, snapshot.fetchedAgeSeconds(nowEpochSeconds = 2000L))
    }
}
