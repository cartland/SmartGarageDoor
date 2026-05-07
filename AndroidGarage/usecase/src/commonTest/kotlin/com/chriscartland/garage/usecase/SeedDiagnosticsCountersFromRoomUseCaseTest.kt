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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeDiagnosticsCountersRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeedDiagnosticsCountersFromRoomUseCaseTest {
    @Test
    fun seedsCountersFromRoomRowCounts_onFirstCall() =
        runTest {
            val appLogger = FakeAppLoggerRepository()
            val counters = FakeDiagnosticsCountersRepository()
            // Seed Room with rows for two keys (3 of "a", 1 of "b").
            repeat(3) { appLogger.log("a") }
            appLogger.log("b")

            val seeded = SeedDiagnosticsCountersFromRoomUseCase(appLogger, counters)()

            assertTrue(seeded, "first invocation should seed")
            assertEquals(3L, counters.observeCount("a").first())
            assertEquals(1L, counters.observeCount("b").first())
            assertTrue(counters.seededFromRoom)
        }

    @Test
    fun secondCallIsNoOp() =
        runTest {
            val appLogger = FakeAppLoggerRepository()
            val counters = FakeDiagnosticsCountersRepository()
            appLogger.log("a")
            val useCase = SeedDiagnosticsCountersFromRoomUseCase(appLogger, counters)

            val first = useCase()
            val second = useCase()

            assertTrue(first)
            assertFalse(second, "second invocation must return false (already seeded)")
            // Counter still shows the seeded value, untouched by the second call.
            assertEquals(1L, counters.observeCount("a").first())
        }

    @Test
    fun preservesExistingCounterValueWhenHigherThanRoomCount() =
        runTest {
            val appLogger = FakeAppLoggerRepository()
            val counters = FakeDiagnosticsCountersRepository()
            // User had been on the new code briefly: counter is at 100 already
            // (from real log() calls), but Room only has 5 rows for that key
            // (because the user used the app once after install).
            repeat(100) { counters.increment("a") }
            repeat(5) { appLogger.log("a") }

            SeedDiagnosticsCountersFromRoomUseCase(appLogger, counters)()

            // max(100, 5) = 100. The pre-existing higher value is preserved.
            assertEquals(100L, counters.observeCount("a").first())
        }

    @Test
    fun emptyRoomIsNoOpButStillFlipsSeededFlag() =
        runTest {
            val appLogger = FakeAppLoggerRepository()
            val counters = FakeDiagnosticsCountersRepository()
            // Room is empty (fresh install).

            val seeded = SeedDiagnosticsCountersFromRoomUseCase(appLogger, counters)()

            assertTrue(seeded)
            assertEquals(0L, counters.observeCount("a").first())
            // Flag flipped → next call is a no-op even if Room later fills.
            assertTrue(counters.seededFromRoom)
        }

    @Test
    fun seedsAfterClearOnlyIfRoomHasRowsAgain() =
        runTest {
            val appLogger = FakeAppLoggerRepository()
            val counters = FakeDiagnosticsCountersRepository()
            val useCase = SeedDiagnosticsCountersFromRoomUseCase(appLogger, counters)

            // First-launch seed runs, then user fires Clear which wipes both
            // stores AND the seeded flag (matching production semantics).
            appLogger.log("a")
            useCase()
            assertEquals(1L, counters.observeCount("a").first())

            appLogger.deleteAll()
            counters.resetAll()

            // After Clear: counter at 0, Room empty, flag reset. Seed runs
            // again on the next launch but finds no rows to seed from.
            useCase()
            assertEquals(0L, counters.observeCount("a").first())
        }
}
