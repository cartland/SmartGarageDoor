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

import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeDiagnosticsCountersRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The strategies' timing behavior, driven on virtual time.
 *
 * The load-bearing assertions are the ones about NOT fetching — a
 * strategy that fetched constantly would satisfy every "did it fetch?"
 * check in this file, so each strategy also has a test that pins where it
 * stays quiet.
 */
class DoorUpdateStrategyTest {
    private val interval = PollDoorUpdateStrategy.DEFAULT_INTERVAL_MS

    private fun TestScope.logAppEvent() = LogAppEventUseCase(FakeAppLoggerRepository(), FakeDiagnosticsCountersRepository())

    private fun TestScope.pollStrategy(
        doorRepo: FakeDoorRepository,
        visibility: AppVisibilityState,
    ) = PollDoorUpdateStrategy(
        fetchCurrentDoorEvent = FetchCurrentDoorEventUseCase(doorRepo),
        appVisibility = visibility,
        logAppEvent = logAppEvent(),
    )

    private fun TestScope.foregroundRefreshStrategy(
        doorRepo: FakeDoorRepository,
        visibility: AppVisibilityState,
    ) = PushWithForegroundRefreshDoorUpdateStrategy(
        fetchCurrentDoorEvent = FetchCurrentDoorEventUseCase(doorRepo),
        appVisibility = visibility,
        logAppEvent = logAppEvent(),
    )

    // --- PUSH ---

    @Test
    fun pushStrategyWaitsInsteadOfCompleting() =
        runTest {
            val job = backgroundScope.launch { PushDoorUpdateStrategy().run() }

            advanceTimeBy(interval * 100)
            runCurrent()

            // Asserting "it makes no requests" here would be vacuous — this
            // strategy holds no repository to make one with. The real property
            // is that it stays running: every strategy is a coroutine the
            // manager cancels to swap, so one that RETURNED would end its
            // collectLatest block and leave the manager in a state no other
            // strategy can reach. (That the PUSH selection issues no requests
            // is pinned where it can actually fail: DoorUpdateManagerTest's
            // androidDefaultRunsPushAndNeverPolls, where a wrong selection
            // would poll.)
            assertTrue(job.isActive, "PushDoorUpdateStrategy.run() must suspend until cancelled")
        }

    // --- POLL ---

    @Test
    fun pollDoesNothingUntilTheAppIsVisible() =
        runTest {
            val doorRepo = FakeDoorRepository()
            val visibility = AppVisibilityState()
            backgroundScope.launch { pollStrategy(doorRepo, visibility).run() }

            advanceTimeBy(interval * 5)
            runCurrent()

            // AppVisibilityState starts false on purpose: a strategy that
            // assumed visibility would poll a backgrounded app forever.
            assertEquals(0, doorRepo.fetchCurrentDoorEventCount)
        }

    @Test
    fun pollFetchesImmediatelyWhenTheAppBecomesVisible() =
        runTest {
            val doorRepo = FakeDoorRepository()
            val visibility = AppVisibilityState()
            backgroundScope.launch { pollStrategy(doorRepo, visibility).run() }

            visibility.setVisible(true)
            runCurrent()

            // Without waiting out an interval: returning to the app is the
            // moment a stale door reading is most visible.
            assertEquals(1, doorRepo.fetchCurrentDoorEventCount)
        }

    @Test
    fun pollRepeatsOnTheInterval() =
        runTest {
            val doorRepo = FakeDoorRepository()
            val visibility = AppVisibilityState()
            backgroundScope.launch { pollStrategy(doorRepo, visibility).run() }

            visibility.setVisible(true)
            runCurrent()
            advanceTimeBy(interval * 3 + 1)
            runCurrent()

            assertEquals(4, doorRepo.fetchCurrentDoorEventCount)
        }

    @Test
    fun aReturnThatConflatesWithTheDepartureStillFetchesImmediately() =
        runTest {
            val doorRepo = FakeDoorRepository()
            val visibility = AppVisibilityState()
            backgroundScope.launch { pollStrategy(doorRepo, visibility).run() }

            visibility.setVisible(true)
            runCurrent()
            assertEquals(1, doorRepo.fetchCurrentDoorEventCount)
            advanceTimeBy(5_000)
            runCurrent()

            // The user leaves and comes back before the collector runs —
            // which is what a fast lock/unlock, an app-switcher bounce, or
            // the iOS process suspending between the two writes looks like
            // from the IO dispatcher's point of view. Both writes land
            // (the platform callbacks are synchronous); only COLLECTION is
            // deferred, and a conflating StateFlow<Boolean> then shows the
            // collector true -> true: no change, no wake, no fetch. The
            // user is staring at the screen and the next fetch is up to a
            // full interval away. Coming back must always fetch NOW.
            visibility.setVisible(false)
            visibility.setVisible(true)
            runCurrent()

            assertEquals(
                2,
                doorRepo.fetchCurrentDoorEventCount,
                "a conflated away-and-back must still trigger the immediate return fetch",
            )
        }

    @Test
    fun pollStopsWhenTheAppIsNoLongerVisible() =
        runTest {
            val doorRepo = FakeDoorRepository()
            val visibility = AppVisibilityState()
            backgroundScope.launch { pollStrategy(doorRepo, visibility).run() }

            visibility.setVisible(true)
            runCurrent()
            advanceTimeBy(interval + 1)
            runCurrent()
            val whileVisible = doorRepo.fetchCurrentDoorEventCount

            visibility.setVisible(false)
            runCurrent()
            advanceTimeBy(interval * 10)
            runCurrent()

            // The gate, not a slower interval: zero additional requests, no
            // matter how long the app stays away.
            assertEquals(whileVisible, doorRepo.fetchCurrentDoorEventCount)
            assertTrue(whileVisible >= 2, "expected the poll to have run before backgrounding")
        }

    @Test
    fun pollBacksOffWhileFetchesFail() =
        runTest {
            val doorRepo = FakeDoorRepository()
            doorRepo.setFailCurrentDoorEventFetch(true)
            val visibility = AppVisibilityState()
            backgroundScope.launch { pollStrategy(doorRepo, visibility).run() }

            visibility.setVisible(true)
            runCurrent()
            advanceTimeBy(interval * 10)
            runCurrent()

            // 10 intervals of failure must cost far fewer than 10 requests.
            // Backoff is 15s, 30s, 60s, … so ~4 attempts fit in 150s.
            val attempts = doorRepo.fetchCurrentDoorEventCount
            assertTrue(attempts in 2..6, "expected a backed-off retry count, got $attempts")
        }

    @Test
    fun pollReturnsToTheIntervalAfterRecovering() =
        runTest {
            val doorRepo = FakeDoorRepository()
            doorRepo.setFailCurrentDoorEventFetch(true)
            val visibility = AppVisibilityState()
            backgroundScope.launch { pollStrategy(doorRepo, visibility).run() }

            visibility.setVisible(true)
            runCurrent()
            advanceTimeBy(interval * 10)
            runCurrent()
            doorRepo.setFailCurrentDoorEventFetch(false)
            // Let the pending backoff expire so the next attempt succeeds.
            advanceTimeBy(PollDoorUpdateStrategy.DEFAULT_MAX_BACKOFF_MS)
            runCurrent()
            val afterRecovery = doorRepo.fetchCurrentDoorEventCount

            advanceTimeBy(interval * 3 + 1)
            runCurrent()

            // Backoff resets on success — otherwise one outage would leave
            // the app polling every two minutes until the user relaunched it.
            assertEquals(afterRecovery + 3, doorRepo.fetchCurrentDoorEventCount)
        }

    // --- PUSH_WITH_FOREGROUND_REFRESH ---

    @Test
    fun foregroundRefreshFetchesOncePerForegroundAndThenStops() =
        runTest {
            val doorRepo = FakeDoorRepository()
            val visibility = AppVisibilityState()
            backgroundScope.launch { foregroundRefreshStrategy(doorRepo, visibility).run() }

            visibility.setVisible(true)
            runCurrent()
            advanceTimeBy(interval * 100)
            runCurrent()

            // No timer: push is doing the work while the app sits open.
            assertEquals(1, doorRepo.fetchCurrentDoorEventCount)

            visibility.setVisible(false)
            runCurrent()
            visibility.setVisible(true)
            runCurrent()

            assertEquals(2, doorRepo.fetchCurrentDoorEventCount)
        }

    @Test
    fun foregroundRefreshRetriesUntilItSucceeds() =
        runTest {
            val doorRepo = FakeDoorRepository()
            doorRepo.setFailCurrentDoorEventFetch(true)
            val visibility = AppVisibilityState()
            backgroundScope.launch { foregroundRefreshStrategy(doorRepo, visibility).run() }

            visibility.setVisible(true)
            runCurrent()
            advanceTimeBy(PushWithForegroundRefreshDoorUpdateStrategy.DEFAULT_MAX_BACKOFF_MS * 4)
            runCurrent()
            val whileFailing = doorRepo.fetchCurrentDoorEventCount
            assertTrue(whileFailing >= 2, "expected retries while failing, got $whileFailing")

            doorRepo.setFailCurrentDoorEventFetch(false)
            advanceTimeBy(PushWithForegroundRefreshDoorUpdateStrategy.DEFAULT_MAX_BACKOFF_MS)
            runCurrent()
            val afterSuccess = doorRepo.fetchCurrentDoorEventCount

            advanceTimeBy(PushWithForegroundRefreshDoorUpdateStrategy.DEFAULT_MAX_BACKOFF_MS * 10)
            runCurrent()

            // Having succeeded, it goes quiet again — a failed refresh must
            // not silently promote this strategy into a poll.
            assertEquals(afterSuccess, doorRepo.fetchCurrentDoorEventCount)
        }
}
