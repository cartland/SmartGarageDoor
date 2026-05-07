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

import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeClock
import com.chriscartland.garage.testcommon.FakeDiagnosticsCountersRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val THRESHOLD = CheckInStalenessManager.CHECK_IN_STALE_THRESHOLD_SECONDS
private val INTERVAL = CheckInStalenessManager.STALE_CHECK_INTERVAL_MS

@OptIn(ExperimentalCoroutinesApi::class)
class CheckInStalenessManagerTest {
    private lateinit var doorRepository: FakeDoorRepository
    private lateinit var logger: FakeAppLoggerRepository

    @BeforeTest
    fun setup() {
        doorRepository = FakeDoorRepository()
        logger = FakeAppLoggerRepository()
    }

    /**
     * Build the manager wired to the same scheduler as the test scope.
     * Following the FcmRegistrationManagerTest pattern: dispatcher is built
     * from `testScheduler` so it shares the scheduler with `backgroundScope`.
     */
    private fun TestScope.createManager(clock: FakeClock): CheckInStalenessManager =
        CheckInStalenessManager(
            observeDoorEvents = ObserveDoorEventsUseCase(doorRepository),
            logAppEvent = LogAppEventUseCase(logger, FakeDiagnosticsCountersRepository()),
            scope = backgroundScope,
            dispatcher = StandardTestDispatcher(testScheduler),
            clock = clock,
        )

    private fun makeEvent(checkInTime: Long): DoorEvent =
        DoorEvent(
            doorPosition = DoorPosition.CLOSED,
            lastCheckInTimeSeconds = checkInTime,
            lastChangeTimeSeconds = checkInTime,
        )

    @Test
    fun isCheckInStale_isFalse_whenCheckInIsRecent() =
        runTest {
            val clock = FakeClock(nowSeconds = 1000L)
            doorRepository.setCurrentDoorEvent(makeEvent(checkInTime = 1000L))
            val manager = createManager(clock = clock)

            manager.start()
            testScheduler.runCurrent()

            assertFalse(manager.isCheckInStale.first())
        }

    @Test
    fun isCheckInStale_isTrue_whenStaleEventArrives() =
        runTest {
            // Start with a fresh check-in.
            val clock = FakeClock(nowSeconds = 1000L)
            doorRepository.setCurrentDoorEvent(makeEvent(checkInTime = 1000L))
            val manager = createManager(clock = clock)
            manager.start()
            testScheduler.runCurrent()
            assertFalse(manager.isCheckInStale.first())

            // New event with old check-in — reactive path (no ticker advance needed).
            doorRepository.setCurrentDoorEvent(
                makeEvent(checkInTime = clock.nowEpochSeconds() - THRESHOLD - 1),
            )
            testScheduler.runCurrent()

            assertTrue(manager.isCheckInStale.first())
        }

    @Test
    fun isCheckInStale_becomesTrue_afterClockAdvances_viaTicker() =
        runTest {
            val checkInTime = 1000L
            val clock = FakeClock(nowSeconds = checkInTime)
            doorRepository.setCurrentDoorEvent(makeEvent(checkInTime = checkInTime))
            val manager = createManager(clock = clock)
            manager.start()
            testScheduler.runCurrent()
            assertFalse(manager.isCheckInStale.first())

            // Wall clock moves past threshold, but no new door event arrives.
            clock.advanceSeconds(THRESHOLD + 1)
            // Periodic ticker fires.
            advanceTimeBy(INTERVAL + 1)
            testScheduler.runCurrent()

            assertTrue(manager.isCheckInStale.first())
        }

    @Test
    fun isCheckInStale_becomesFresh_whenNewEventArrives() =
        runTest {
            val checkInTime = 1000L
            val clock = FakeClock(nowSeconds = checkInTime + THRESHOLD + 1)
            doorRepository.setCurrentDoorEvent(makeEvent(checkInTime = checkInTime))
            val manager = createManager(clock = clock)
            manager.start()
            testScheduler.runCurrent()
            assertTrue(manager.isCheckInStale.first())

            // New fresh event arrives.
            doorRepository.setCurrentDoorEvent(makeEvent(checkInTime = clock.nowEpochSeconds()))
            testScheduler.runCurrent()

            assertFalse(manager.isCheckInStale.first())
        }

    @Test
    fun logsStaleEvent_whenCheckInBecomesStale() =
        runTest {
            val checkInTime = 1000L
            val clock = FakeClock(nowSeconds = checkInTime)
            doorRepository.setCurrentDoorEvent(makeEvent(checkInTime = checkInTime))
            val manager = createManager(clock = clock)
            manager.start()
            testScheduler.runCurrent()

            // Advance clock + ticker to trigger stale.
            clock.advanceSeconds(THRESHOLD + 1)
            advanceTimeBy(INTERVAL + 1)
            testScheduler.runCurrent()

            assertTrue(
                logger.loggedKeys.contains(AppLoggerKeys.EXCEEDED_EXPECTED_TIME_WITHOUT_FCM),
                "Expected staleness log, got: ${logger.loggedKeys}",
            )
        }

    @Test
    fun doesNotLogFresh_onFirstEmission() =
        runTest {
            val clock = FakeClock(nowSeconds = 1000L)
            doorRepository.setCurrentDoorEvent(makeEvent(checkInTime = 1000L))
            val manager = createManager(clock = clock)

            manager.start()
            testScheduler.runCurrent()

            // First emission is false (fresh) — should NOT log "in range".
            val freshLogs = logger.loggedKeys.filter {
                it == AppLoggerKeys.TIME_WITHOUT_FCM_IN_EXPECTED_RANGE
            }
            assertTrue(
                freshLogs.isEmpty(),
                "Should not log fresh on first emission, got: ${logger.loggedKeys}",
            )
        }

    @Test
    fun start_isIdempotent() =
        runTest {
            val clock = FakeClock(nowSeconds = 1000L)
            doorRepository.setCurrentDoorEvent(makeEvent(checkInTime = 1000L))
            val manager = createManager(clock = clock)

            manager.start()
            manager.start() // second call should be a no-op
            testScheduler.runCurrent()

            // Move forward, expect single transition log (not duplicates).
            clock.advanceSeconds(THRESHOLD + 1)
            advanceTimeBy(INTERVAL + 1)
            testScheduler.runCurrent()

            val staleCount = logger.loggedKeys.count {
                it == AppLoggerKeys.EXCEEDED_EXPECTED_TIME_WITHOUT_FCM
            }
            assertEquals(1, staleCount, "Expected single stale log, got: ${logger.loggedKeys}")
        }
}
