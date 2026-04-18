/*
 * Copyright 2024 Chris Cartland. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chriscartland.garage.data.repository

import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.ServerConfig
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.testcommon.FakeNetworkButtonDataSource
import com.chriscartland.garage.testcommon.FakeNetworkConfigDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Protects against the android/164 "stuck Loading" bug.
 *
 * The singleton's first fetch must run on an app-lifetime scope. If it runs
 * on a VM scope and the VM is destroyed mid-fetch, Ktor rethrows
 * CancellationException, the when(result) branch never executes, and the
 * singleton's StateFlow is permanently stuck at Loading for every future
 * subscriber. See [androidApp/.../FirebaseAuthRepository] for the reference
 * pattern (ADR-018).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkSnoozeRepositoryTest {
    private val validConfig = NetworkResult.Success(
        ServerConfig(
            buildTimestamp = "test",
            remoteButtonBuildTimestamp = "test",
            remoteButtonPushKey = "key",
        ),
    )

    @Test
    fun initOnExternalScopeFetchesAndTransitionsOffLoading() =
        runTest {
            val buttonDs = FakeNetworkButtonDataSource()
            val configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
            buttonDs.setFetchSnoozeResult(NetworkResult.Success(0L))
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = NetworkSnoozeRepository(
                networkButtonDataSource = buttonDs,
                serverConfigRepository = CachedServerConfigRepository(configDs, "key"),
                snoozeNotificationsOption = true,
                currentTimeSeconds = { 0L },
                externalScope = externalScope,
            )
            advanceUntilIdle()

            assertEquals(SnoozeState.NotSnoozing, repo.observeSnoozeState().first())
            assertEquals(1, buttonDs.fetchSnoozeCount)
        }

    @Test
    fun vmScopeCancellationCannotStrandSingletonAtLoading() =
        runTest {
            // Simulate the bug: a slow network call and a VM scope that cancels
            // mid-flight. With the fix, the repo's own init fetch (on external
            // scope) runs to completion regardless. Without the fix, the
            // repo-scoped fetch was absent and VM-scoped fetches could leave
            // state at Loading forever.
            val buttonDs = FakeNetworkButtonDataSource()
            val configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
            buttonDs.setFetchSnoozeResult(NetworkResult.Success(0L))
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val vmScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = NetworkSnoozeRepository(
                networkButtonDataSource = buttonDs,
                serverConfigRepository = CachedServerConfigRepository(configDs, "key"),
                snoozeNotificationsOption = true,
                currentTimeSeconds = { 0L },
                externalScope = externalScope,
            )
            // VM-scoped fetch starts then is cancelled before it can complete.
            vmScope.launch { repo.fetchSnoozeStatus() }
            advanceTimeBy(10)
            vmScope.coroutineContext.cancelChildren()
            advanceUntilIdle()

            // External-scope fetch from init still completed — state is NOT stuck.
            assertEquals(SnoozeState.NotSnoozing, repo.observeSnoozeState().first())

            externalScope.cancel()
            vmScope.cancel()
        }

    @Test
    fun initHandlesConnectionFailureByClearingLoading() =
        runTest {
            val buttonDs = FakeNetworkButtonDataSource()
            val configDs = FakeNetworkConfigDataSource().apply {
                setServerConfigResult(NetworkResult.ConnectionFailed)
            }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = NetworkSnoozeRepository(
                networkButtonDataSource = buttonDs,
                serverConfigRepository = CachedServerConfigRepository(configDs, "key"),
                snoozeNotificationsOption = true,
                currentTimeSeconds = { 0L },
                externalScope = externalScope,
            )
            advanceUntilIdle()

            // Server config unavailable → fall back to NotSnoozing, never stay Loading.
            assertEquals(SnoozeState.NotSnoozing, repo.observeSnoozeState().first())

            externalScope.cancel()
        }

    @Test
    fun snoozingStateReflectsFutureEndTime() =
        runTest {
            val now = 1000L
            val futureEnd = 5000L
            val buttonDs = FakeNetworkButtonDataSource().apply {
                setFetchSnoozeResult(NetworkResult.Success(futureEnd))
            }
            val configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = NetworkSnoozeRepository(
                networkButtonDataSource = buttonDs,
                serverConfigRepository = CachedServerConfigRepository(configDs, "key"),
                snoozeNotificationsOption = true,
                currentTimeSeconds = { now },
                externalScope = externalScope,
            )
            advanceUntilIdle()

            assertEquals(SnoozeState.Snoozing(futureEnd), repo.observeSnoozeState().first())

            externalScope.cancel()
        }

    @Test
    fun successfulSubmitTriggersFetchUpdatingStateFromRealServerResponse() =
        runTest {
            // After submit succeeds, the server flips the snooze to an active
            // window. The next fetch must return that new end time and update
            // the singleton state — no optimistic local write in the repo.
            val now = 1000L
            val submittedSnoozeEnd = 5000L
            val buttonDs = FakeNetworkButtonDataSource().apply {
                setFetchSnoozeResult(NetworkResult.Success(0L)) // initial: no snooze
            }
            val configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = NetworkSnoozeRepository(
                networkButtonDataSource = buttonDs,
                serverConfigRepository = CachedServerConfigRepository(configDs, "key"),
                snoozeNotificationsOption = true,
                currentTimeSeconds = { now },
                externalScope = externalScope,
            )
            advanceUntilIdle()
            assertEquals(SnoozeState.NotSnoozing, repo.observeSnoozeState().first())

            // Simulate server: after submit the GET returns the new snooze end.
            buttonDs.setFetchSnoozeResult(NetworkResult.Success(submittedSnoozeEnd))

            val submitted = repo.snoozeNotifications(
                snoozeDurationHours = "1h",
                idToken = "t",
                snoozeEventTimestampSeconds = 0L,
            )
            advanceUntilIdle()

            assertEquals(true, submitted)
            // State reflects the server's GET response, not any client-side
            // optimistic computation.
            assertEquals(SnoozeState.Snoozing(submittedSnoozeEnd), repo.observeSnoozeState().first())
            // submit POST + post-submit GET = 1 submit + 2 fetches (init + post-submit).
            assertEquals(2, buttonDs.fetchSnoozeCount)
            assertEquals(1, buttonDs.snoozeCount)

            externalScope.cancel()
        }

    @Test
    fun callerCancellationDuringSubmitDoesNotStrandState() =
        runTest {
            // The VM scope calls snoozeNotifications then is cancelled. The
            // submit runs on externalScope and completes; the post-submit
            // fetch updates state even though the VM's await threw.
            val now = 1000L
            val snoozeEnd = 5000L
            val buttonDs = FakeNetworkButtonDataSource().apply {
                setFetchSnoozeResult(NetworkResult.Success(snoozeEnd))
            }
            val configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val vmScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = NetworkSnoozeRepository(
                networkButtonDataSource = buttonDs,
                serverConfigRepository = CachedServerConfigRepository(configDs, "key"),
                snoozeNotificationsOption = true,
                currentTimeSeconds = { now },
                externalScope = externalScope,
            )
            advanceUntilIdle()

            vmScope.launch {
                repo.snoozeNotifications("1h", "t", 0L)
            }
            advanceTimeBy(1)
            vmScope.coroutineContext.cancelChildren()
            advanceUntilIdle()

            // State reaches Snoozing despite VM cancellation.
            assertEquals(SnoozeState.Snoozing(snoozeEnd), repo.observeSnoozeState().first())

            externalScope.cancel()
            vmScope.cancel()
        }

    @Test
    fun fetchSnoozeStatusWritesToFlowEvenWhenCallerCancels() =
        runTest {
            // The polling LaunchedEffect calls fetchSnoozeStatus on viewModelScope.
            // If the VM is destroyed mid-fetch, the state must still update on
            // the singleton so the next VM observes the fresh value.
            val now = 1000L
            val snoozeEnd = 5000L
            val buttonDs = FakeNetworkButtonDataSource().apply {
                setFetchSnoozeResult(NetworkResult.Success(0L)) // initial: no snooze
            }
            val configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val vmScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = NetworkSnoozeRepository(
                networkButtonDataSource = buttonDs,
                serverConfigRepository = CachedServerConfigRepository(configDs, "key"),
                snoozeNotificationsOption = true,
                currentTimeSeconds = { now },
                externalScope = externalScope,
            )
            advanceUntilIdle()
            assertEquals(SnoozeState.NotSnoozing, repo.observeSnoozeState().first())

            // Server now reports a snooze. Next fetch should pick it up.
            buttonDs.setFetchSnoozeResult(NetworkResult.Success(snoozeEnd))

            vmScope.launch { repo.fetchSnoozeStatus() }
            advanceTimeBy(1)
            vmScope.coroutineContext.cancelChildren()
            advanceUntilIdle()

            assertEquals(SnoozeState.Snoozing(snoozeEnd), repo.observeSnoozeState().first())

            externalScope.cancel()
            vmScope.cancel()
        }

    @Test
    fun failedSubmitDoesNotTriggerExtraFetch() =
        runTest {
            val buttonDs = FakeNetworkButtonDataSource().apply {
                setFetchSnoozeResult(NetworkResult.Success(0L))
                setSnoozeResult(NetworkResult.HttpError(500))
            }
            val configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = NetworkSnoozeRepository(
                networkButtonDataSource = buttonDs,
                serverConfigRepository = CachedServerConfigRepository(configDs, "key"),
                snoozeNotificationsOption = true,
                currentTimeSeconds = { 0L },
                externalScope = externalScope,
            )
            advanceUntilIdle()
            val fetchesAfterInit = buttonDs.fetchSnoozeCount

            val submitted = repo.snoozeNotifications("1h", "t", 0L)
            advanceUntilIdle()

            assertEquals(false, submitted)
            // No post-submit fetch on failure — the state is untouched.
            assertEquals(fetchesAfterInit, buttonDs.fetchSnoozeCount)

            externalScope.cancel()
        }

    @Test
    fun featureDisabledTransitionsOffLoading() =
        runTest {
            val buttonDs = FakeNetworkButtonDataSource()
            val configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = NetworkSnoozeRepository(
                networkButtonDataSource = buttonDs,
                serverConfigRepository = CachedServerConfigRepository(configDs, "key"),
                snoozeNotificationsOption = false,
                currentTimeSeconds = { 0L },
                externalScope = externalScope,
            )
            // Feature-disabled path has a delay(500); advanceUntilIdle covers it.
            advanceUntilIdle()

            assertEquals(SnoozeState.NotSnoozing, repo.observeSnoozeState().first())
            assertEquals(0, buttonDs.fetchSnoozeCount)

            externalScope.cancel()
        }
}
