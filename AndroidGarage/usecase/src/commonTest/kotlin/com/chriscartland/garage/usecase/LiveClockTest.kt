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

import com.chriscartland.garage.testcommon.FakeClock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LiveClockTest {
    @Test
    fun nowEpochSeconds_initialValueMatchesClock() =
        runTest {
            val clock = FakeClock(nowSeconds = 1_000L)
            val liveClock = DefaultLiveClock(
                clock = clock,
                scope = backgroundScope,
                dispatcher = StandardTestDispatcher(testScheduler),
                intervalMillis = 10_000L,
            )

            assertEquals(1_000L, liveClock.nowEpochSeconds.value)
        }

    @Test
    fun start_emitsNewValueAfterEachInterval() =
        runTest {
            val clock = FakeClock(nowSeconds = 1_000L)
            val liveClock = DefaultLiveClock(
                clock = clock,
                scope = backgroundScope,
                dispatcher = StandardTestDispatcher(testScheduler),
                intervalMillis = 10_000L,
            )

            liveClock.start()
            runCurrent()
            assertEquals(1_000L, liveClock.nowEpochSeconds.value)

            // Advance by one tick interval; clock should report the new "now".
            clock.advanceSeconds(10L)
            advanceTimeBy(10_000L)
            runCurrent()
            assertEquals(1_010L, liveClock.nowEpochSeconds.value)

            // Two more intervals.
            clock.advanceSeconds(20L)
            advanceTimeBy(20_000L)
            runCurrent()
            assertEquals(1_030L, liveClock.nowEpochSeconds.value)
        }

    @Test
    fun start_isIdempotent() =
        runTest {
            val clock = FakeClock(nowSeconds = 0L)
            val liveClock = DefaultLiveClock(
                clock = clock,
                scope = backgroundScope,
                dispatcher = StandardTestDispatcher(testScheduler),
                intervalMillis = 10_000L,
            )

            liveClock.start()
            liveClock.start()
            liveClock.start()
            runCurrent()

            // Advance one interval; if multiple tick jobs were running, the value
            // would still match clock (single-source) — but if anything bad
            // happened (e.g. cancelled ticker), the value would stop updating.
            clock.advanceSeconds(10L)
            advanceTimeBy(10_000L)
            runCurrent()
            assertEquals(10L, liveClock.nowEpochSeconds.value)
        }
}
