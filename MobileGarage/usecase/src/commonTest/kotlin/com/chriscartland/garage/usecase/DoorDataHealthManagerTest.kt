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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.testcommon.FakeClock
import com.chriscartland.garage.testcommon.FakeDoorRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val WINDOW_MILLIS = DoorDataHealthManager.RECOVERY_WINDOW_SECONDS * 1_000

/**
 * The rule: one failure is not news; three in a row, or one that a minute
 * has not recovered, is. Anything getting through clears it at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DoorDataHealthManagerTest {
    private lateinit var doorRepository: FakeDoorRepository
    private val clock = FakeClock(nowSeconds = 1_000_000L)

    @BeforeTest
    fun setup() {
        doorRepository = FakeDoorRepository().apply {
            setCurrentDoorEvent(DoorEvent(doorPosition = DoorPosition.CLOSED))
        }
    }

    private fun TestScope.started(): DefaultDoorDataHealthManager =
        DefaultDoorDataHealthManager(
            observeDoorEvents = ObserveDoorEventsUseCase(doorRepository),
            scope = backgroundScope,
            dispatcher = StandardTestDispatcher(testScheduler),
            clock = clock,
        ).also {
            it.start()
            runCurrent()
        }

    private suspend fun TestScope.failOnce() {
        doorRepository.setFailCurrentDoorEventFetch(true)
        doorRepository.fetchCurrentDoorEvent()
        runCurrent()
    }

    private suspend fun TestScope.succeedOnce() {
        doorRepository.setFailCurrentDoorEventFetch(false)
        doorRepository.fetchCurrentDoorEvent()
        runCurrent()
    }

    /** Move both clocks: the manager stamps the run with one and waits with the other. */
    private fun TestScope.pass(seconds: Long) {
        clock.advanceSeconds(seconds)
        advanceTimeBy(seconds * 1_000)
        runCurrent()
    }

    @Test
    fun oneFailureSaysNothing() =
        runTest {
            val manager = started()

            failOnce()

            assertFalse(manager.isDoorDataUnhealthy.value, "a single failed fetch must not be announced")
        }

    @Test
    fun threeFailuresInARowAreUnhealthyAtOnce() =
        runTest {
            val manager = started()

            failOnce()
            failOnce()
            assertFalse(manager.isDoorDataUnhealthy.value, "two is still one bad moment")
            failOnce()

            assertTrue(manager.isDoorDataUnhealthy.value)
        }

    @Test
    fun aFailureTheWindowDoesNotRecoverBecomesUnhealthy() =
        runTest {
            // The push-only phone: it asks once, nothing retries, and the
            // screen must not stay confident forever.
            val manager = started()
            failOnce()

            pass(DoorDataHealthManager.RECOVERY_WINDOW_SECONDS - 1)
            assertFalse(manager.isDoorDataUnhealthy.value, "one second early")

            pass(1)
            assertTrue(manager.isDoorDataUnhealthy.value)
        }

    @Test
    fun aRecoveryInsideTheWindowNeverShowsAnything() =
        runTest {
            // The ordinary iOS case: a poll fails, the next one succeeds.
            val manager = started()
            failOnce()
            pass(15)
            succeedOnce()

            pass(DoorDataHealthManager.RECOVERY_WINDOW_SECONDS * 2)

            assertFalse(manager.isDoorDataUnhealthy.value)
        }

    @Test
    fun theWindowCountsFromTheFirstFailureNotTheLatest() =
        runTest {
            // A second failure must not restart the clock — otherwise a poll
            // that fails every 30 seconds would push the verdict back forever.
            val manager = started()
            failOnce()
            pass(40)
            failOnce()

            pass(DoorDataHealthManager.RECOVERY_WINDOW_SECONDS - 40)

            assertTrue(manager.isDoorDataUnhealthy.value)
        }

    @Test
    fun anythingGettingThroughClearsItAtOnce() =
        runTest {
            val manager = started()
            failOnce()
            failOnce()
            failOnce()
            assertTrue(manager.isDoorDataUnhealthy.value)

            succeedOnce()

            assertFalse(manager.isDoorDataUnhealthy.value)
        }

    @Test
    fun aPushClearsItToo() =
        runTest {
            val manager = started()
            failOnce()
            failOnce()
            failOnce()

            doorRepository.insertDoorEvent(DoorEvent(doorPosition = DoorPosition.OPEN))
            runCurrent()

            assertFalse(manager.isDoorDataUnhealthy.value)
        }

    @Test
    fun aNewRunAfterARecoveryGetsAFreshWindow() =
        runTest {
            // The old run's start time must not leak into the next run, or a
            // single failure an hour later would be "unhealthy" immediately.
            val manager = started()
            failOnce()
            pass(50)
            succeedOnce()
            pass(3_600)

            failOnce()
            pass(DoorDataHealthManager.RECOVERY_WINDOW_SECONDS - 1)

            assertFalse(manager.isDoorDataUnhealthy.value)
        }

    @Test
    fun startingTwiceIsOneWatcher() =
        runTest {
            val manager = started()
            manager.start()
            runCurrent()

            failOnce()
            pass(WINDOW_MILLIS / 1_000)

            assertTrue(manager.isDoorDataUnhealthy.value)
        }

    @Test
    fun theRuleItself() {
        val window = DoorDataHealthManager.RECOVERY_WINDOW_SECONDS
        assertFalse(DoorDataHealthManager.isUnhealthy(consecutiveFailures = 0, secondsSinceFirstFailure = window * 10))
        assertFalse(DoorDataHealthManager.isUnhealthy(consecutiveFailures = 1, secondsSinceFirstFailure = window - 1))
        assertTrue(DoorDataHealthManager.isUnhealthy(consecutiveFailures = 1, secondsSinceFirstFailure = window))
        assertFalse(DoorDataHealthManager.isUnhealthy(consecutiveFailures = 2, secondsSinceFirstFailure = 0))
        assertTrue(DoorDataHealthManager.isUnhealthy(consecutiveFailures = 3, secondsSinceFirstFailure = 0))
    }
}
