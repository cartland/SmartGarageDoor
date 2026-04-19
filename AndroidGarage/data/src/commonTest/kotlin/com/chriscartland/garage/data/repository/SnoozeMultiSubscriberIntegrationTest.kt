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

package com.chriscartland.garage.data.repository

import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.ServerConfig
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.testcommon.FakeNetworkButtonDataSource
import com.chriscartland.garage.testcommon.FakeNetworkConfigDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Simulates the production three-ViewModel scenario that caused the android/167
 * snooze-propagation bug: one singleton [NetworkSnoozeRepository] with multiple
 * concurrent subscribers, each representing a different VM instance
 * (rememberViewModelStoreNavEntryDecorator created per-nav-entry VMs).
 *
 * The bug was: writes that should have reached every subscriber via the
 * singleton's `StateFlow` were observed only by some. PR #354 added a direct
 * write from the VM as a workaround; this test proves the repository-only path
 * is sufficient so the workaround can be removed in PR 2.
 *
 * Every subscriber here reads through [SnoozeRepository.snoozeState] — no one
 * consults `snoozeNotifications()`'s return value. If the repository's flow is
 * correct, every subscriber must converge to the submitted state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SnoozeMultiSubscriberIntegrationTest {
    private val validConfig = NetworkResult.Success(
        ServerConfig(
            buildTimestamp = "test",
            remoteButtonBuildTimestamp = "test",
            remoteButtonPushKey = "key",
        ),
    )

    private fun buildRepo(
        externalScope: CoroutineScope,
        buttonDs: FakeNetworkButtonDataSource,
        configDs: FakeNetworkConfigDataSource,
        currentTime: Long,
    ): NetworkSnoozeRepository =
        NetworkSnoozeRepository(
            networkButtonDataSource = buttonDs,
            serverConfigRepository = CachedServerConfigRepository(configDs, "key"),
            snoozeNotificationsOption = true,
            currentTimeSeconds = { currentTime },
            externalScope = externalScope,
        )

    /**
     * Three concurrent subscribers — one per simulated VM. A POST landing on
     * the repo flips every subscriber's latest value to Snoozing. None of the
     * subscribers use the POST return value; they rely entirely on the
     * repository's observable flow.
     */
    @Test
    fun postResultReachesAllThreeSubscribers() =
        runTest {
            val now = 1_000L
            val end = 5_000L
            val buttonDs = FakeNetworkButtonDataSource().apply {
                setFetchSnoozeResult(NetworkResult.Success(0L))
                setSnoozeResult(NetworkResult.Success(end))
            }
            val configDs = FakeNetworkConfigDataSource().apply {
                setServerConfigResult(validConfig)
            }
            val externalScope = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            )

            val repo = buildRepo(externalScope, buttonDs, configDs, now)
            advanceUntilIdle()
            // Initial fetch: NotSnoozing.
            assertEquals(SnoozeState.NotSnoozing, repo.snoozeState.value)

            val seenA = mutableListOf<SnoozeState>()
            val seenB = mutableListOf<SnoozeState>()
            val seenC = mutableListOf<SnoozeState>()

            val subscriberScopeA = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            )
            val subscriberScopeB = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            )
            val subscriberScopeC = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            )
            val jobs = mutableListOf<Job>()
            jobs += subscriberScopeA.launch {
                repo.snoozeState.collect { seenA.add(it) }
            }
            jobs += subscriberScopeB.launch {
                repo.snoozeState.collect { seenB.add(it) }
            }
            jobs += subscriberScopeC.launch {
                repo.snoozeState.collect { seenC.add(it) }
            }
            advanceUntilIdle()
            // All three subscribers got the initial replay.
            assertEquals(SnoozeState.NotSnoozing, seenA.last())
            assertEquals(SnoozeState.NotSnoozing, seenB.last())
            assertEquals(SnoozeState.NotSnoozing, seenC.last())

            // Single POST — no one reads its return value.
            val result = repo.snoozeNotifications(
                snoozeDurationHours = "1h",
                idToken = "t",
                snoozeEventTimestampSeconds = 0L,
            )
            advanceUntilIdle()
            assertTrue(result is AppResult.Success, "POST should succeed")

            // Every subscriber observed the new state via the flow alone.
            assertEquals(
                SnoozeState.Snoozing(end),
                seenA.last(),
                "Subscriber A must see Snoozing after POST (flow-only path)",
            )
            assertEquals(
                SnoozeState.Snoozing(end),
                seenB.last(),
                "Subscriber B must see Snoozing after POST (flow-only path)",
            )
            assertEquals(
                SnoozeState.Snoozing(end),
                seenC.last(),
                "Subscriber C must see Snoozing after POST (flow-only path)",
            )

            jobs.forEach { it.cancel() }
            subscriberScopeA.cancel()
            subscriberScopeB.cancel()
            subscriberScopeC.cancel()
            externalScope.cancel()
        }

    /**
     * A subscriber that joins AFTER the POST lands still sees the latest value
     * on first collect. Proves StateFlow replay is carrying the state, not
     * transient SharedFlow emissions.
     */
    @Test
    fun lateSubscriberSeesLatestValueAfterPost() =
        runTest {
            val now = 1_000L
            val end = 7_500L
            val buttonDs = FakeNetworkButtonDataSource().apply {
                setFetchSnoozeResult(NetworkResult.Success(0L))
                setSnoozeResult(NetworkResult.Success(end))
            }
            val configDs = FakeNetworkConfigDataSource().apply {
                setServerConfigResult(validConfig)
            }
            val externalScope = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            )

            val repo = buildRepo(externalScope, buttonDs, configDs, now)
            advanceUntilIdle()

            val result = repo.snoozeNotifications(
                snoozeDurationHours = "1h",
                idToken = "t",
                snoozeEventTimestampSeconds = 0L,
            )
            advanceUntilIdle()
            assertTrue(result is AppResult.Success)

            // Late subscriber — joins AFTER the POST completed.
            val lateSeen = mutableListOf<SnoozeState>()
            val lateScope = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            )
            val lateJob = lateScope.launch {
                repo.snoozeState.collect { lateSeen.add(it) }
            }
            advanceUntilIdle()

            assertEquals(
                SnoozeState.Snoozing(end),
                lateSeen.last(),
                "Late subscriber must receive current value on first collect",
            )

            lateJob.cancel()
            lateScope.cancel()
            externalScope.cancel()
        }

    /**
     * Two sequential POSTs — each one updates every subscriber. Simulates the
     * user snoozing, un-snoozing, then snoozing again in quick succession.
     */
    @Test
    fun sequentialPostsUpdateAllSubscribers() =
        runTest {
            val now = 1_000L
            val end1 = 4_000L
            val end2 = 0L // clear
            val end3 = 9_000L
            val buttonDs = FakeNetworkButtonDataSource().apply {
                setFetchSnoozeResult(NetworkResult.Success(0L))
            }
            val configDs = FakeNetworkConfigDataSource().apply {
                setServerConfigResult(validConfig)
            }
            val externalScope = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            )
            val repo = buildRepo(externalScope, buttonDs, configDs, now)
            advanceUntilIdle()

            val seenA = mutableListOf<SnoozeState>()
            val seenB = mutableListOf<SnoozeState>()
            val scopeA = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            )
            val scopeB = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            )
            val jobA = scopeA.launch {
                repo.snoozeState.collect { seenA.add(it) }
            }
            val jobB = scopeB.launch {
                repo.snoozeState.collect { seenB.add(it) }
            }
            advanceUntilIdle()

            buttonDs.setSnoozeResult(NetworkResult.Success(end1))
            repo.snoozeNotifications("1h", "t", 0L)
            advanceUntilIdle()
            assertEquals(SnoozeState.Snoozing(end1), seenA.last())
            assertEquals(SnoozeState.Snoozing(end1), seenB.last())

            buttonDs.setSnoozeResult(NetworkResult.Success(end2))
            repo.snoozeNotifications("0h", "t", 0L)
            advanceUntilIdle()
            assertEquals(SnoozeState.NotSnoozing, seenA.last())
            assertEquals(SnoozeState.NotSnoozing, seenB.last())

            buttonDs.setSnoozeResult(NetworkResult.Success(end3))
            repo.snoozeNotifications("2h", "t", 0L)
            advanceUntilIdle()
            assertEquals(SnoozeState.Snoozing(end3), seenA.last())
            assertEquals(SnoozeState.Snoozing(end3), seenB.last())

            jobA.cancel()
            jobB.cancel()
            scopeA.cancel()
            scopeB.cancel()
            externalScope.cancel()
        }
}
