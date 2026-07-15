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
import com.chriscartland.garage.data.statuscache.SnoozeSnapshot
import com.chriscartland.garage.data.statuscache.SnoozeSnapshotDto
import com.chriscartland.garage.data.statuscache.StatusSnapshot
import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.ServerConfig
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.repository.SnoozeDoorEventBridge
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeNetworkButtonDataSource
import com.chriscartland.garage.testcommon.FakeNetworkConfigDataSource
import com.chriscartland.garage.testcommon.FakeStatusSnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    private fun makeAuthRepo(): FakeAuthRepository =
        FakeAuthRepository().apply {
            setIdTokenResult(FirebaseIdToken(idToken = "t", exp = Long.MAX_VALUE))
        }

    @Test
    fun initOnExternalScopeFetchesAndTransitionsOffLoading() =
        runTest {
            val buttonDs = FakeNetworkButtonDataSource()
            val configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
            buttonDs.setFetchSnoozeResult(NetworkResult.Success(0L))
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = NetworkSnoozeRepository(
                networkButtonDataSource = buttonDs,
                serverConfigRepository = CachedServerConfigRepository(configDs, "key", externalScope),
                authRepository = makeAuthRepo(),
                statusSnapshotStore = FakeStatusSnapshotStore(),
                snoozeDoorEventBridge = SnoozeDoorEventBridge(),
                snoozeNotificationsOption = true,
                currentTimeSeconds = { 0L },
                externalScope = externalScope,
            )
            advanceUntilIdle()

            assertEquals(SnoozeState.NotSnoozing, repo.snoozeState.value)
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
                serverConfigRepository = CachedServerConfigRepository(configDs, "key", externalScope),
                authRepository = makeAuthRepo(),
                statusSnapshotStore = FakeStatusSnapshotStore(),
                snoozeDoorEventBridge = SnoozeDoorEventBridge(),
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
            assertEquals(SnoozeState.NotSnoozing, repo.snoozeState.value)

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
                serverConfigRepository = CachedServerConfigRepository(configDs, "key", externalScope),
                authRepository = makeAuthRepo(),
                statusSnapshotStore = FakeStatusSnapshotStore(),
                snoozeDoorEventBridge = SnoozeDoorEventBridge(),
                snoozeNotificationsOption = true,
                currentTimeSeconds = { 0L },
                externalScope = externalScope,
            )
            advanceUntilIdle()

            // Server config unavailable → fall back to NotSnoozing, never stay Loading.
            assertEquals(SnoozeState.NotSnoozing, repo.snoozeState.value)

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
                serverConfigRepository = CachedServerConfigRepository(configDs, "key", externalScope),
                authRepository = makeAuthRepo(),
                statusSnapshotStore = FakeStatusSnapshotStore(),
                snoozeDoorEventBridge = SnoozeDoorEventBridge(),
                snoozeNotificationsOption = true,
                currentTimeSeconds = { now },
                externalScope = externalScope,
            )
            advanceUntilIdle()

            assertEquals(SnoozeState.Snoozing(futureEnd), repo.snoozeState.value)

            externalScope.cancel()
        }

    @Test
    fun successfulSubmitUpdatesStateFromPostResponse() =
        runTest {
            // The server returns the snoozeEndTimeSeconds in the POST response
            // body. The repo writes state directly from it — no follow-up GET
            // needed (authoritative data is already in hand).
            val now = 1000L
            val submittedSnoozeEnd = 5000L
            val buttonDs = FakeNetworkButtonDataSource().apply {
                setFetchSnoozeResult(NetworkResult.Success(0L)) // initial: no snooze
                setSnoozeResult(NetworkResult.Success(submittedSnoozeEnd)) // POST returns end time
            }
            val configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = NetworkSnoozeRepository(
                networkButtonDataSource = buttonDs,
                serverConfigRepository = CachedServerConfigRepository(configDs, "key", externalScope),
                authRepository = makeAuthRepo(),
                statusSnapshotStore = FakeStatusSnapshotStore(),
                snoozeDoorEventBridge = SnoozeDoorEventBridge(),
                snoozeNotificationsOption = true,
                currentTimeSeconds = { now },
                externalScope = externalScope,
            )
            advanceUntilIdle()
            assertEquals(SnoozeState.NotSnoozing, repo.snoozeState.value)

            val submitted = repo.snoozeNotifications(
                snoozeDurationHours = "1h",
                snoozeEventTimestampSeconds = 0L,
            )
            advanceUntilIdle()

            assertTrue(submitted is AppResult.Success, "Should succeed")
            assertEquals(
                SnoozeState.Snoozing(submittedSnoozeEnd),
                (submitted as AppResult.Success).data,
            )
            // Return value matches the flow value — repo writes both.
            assertEquals(SnoozeState.Snoozing(submittedSnoozeEnd), repo.snoozeState.value)
            // Only the init fetch runs — no follow-up GET after the POST.
            assertEquals(1, buttonDs.fetchSnoozeCount)
            assertEquals(1, buttonDs.snoozeCount)

            externalScope.cancel()
        }

    @Test
    fun callerCancellationDuringSubmitDoesNotStrandState() =
        runTest {
            // The VM scope calls snoozeNotifications then is cancelled. The
            // submit runs on externalScope and writes state from the POST
            // response even though the VM's await threw.
            val now = 1000L
            val snoozeEnd = 5000L
            val buttonDs = FakeNetworkButtonDataSource().apply {
                setFetchSnoozeResult(NetworkResult.Success(0L))
                setSnoozeResult(NetworkResult.Success(snoozeEnd))
            }
            val configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val vmScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = NetworkSnoozeRepository(
                networkButtonDataSource = buttonDs,
                serverConfigRepository = CachedServerConfigRepository(configDs, "key", externalScope),
                authRepository = makeAuthRepo(),
                statusSnapshotStore = FakeStatusSnapshotStore(),
                snoozeDoorEventBridge = SnoozeDoorEventBridge(),
                snoozeNotificationsOption = true,
                currentTimeSeconds = { now },
                externalScope = externalScope,
            )
            advanceUntilIdle()

            vmScope.launch {
                repo.snoozeNotifications("1h", 0L)
            }
            advanceTimeBy(1)
            vmScope.coroutineContext.cancelChildren()
            advanceUntilIdle()

            // State reaches Snoozing despite VM cancellation.
            assertEquals(SnoozeState.Snoozing(snoozeEnd), repo.snoozeState.value)

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
                serverConfigRepository = CachedServerConfigRepository(configDs, "key", externalScope),
                authRepository = makeAuthRepo(),
                statusSnapshotStore = FakeStatusSnapshotStore(),
                snoozeDoorEventBridge = SnoozeDoorEventBridge(),
                snoozeNotificationsOption = true,
                currentTimeSeconds = { now },
                externalScope = externalScope,
            )
            advanceUntilIdle()
            assertEquals(SnoozeState.NotSnoozing, repo.snoozeState.value)

            // Server now reports a snooze. Next fetch should pick it up.
            buttonDs.setFetchSnoozeResult(NetworkResult.Success(snoozeEnd))

            vmScope.launch { repo.fetchSnoozeStatus() }
            advanceTimeBy(1)
            vmScope.coroutineContext.cancelChildren()
            advanceUntilIdle()

            assertEquals(SnoozeState.Snoozing(snoozeEnd), repo.snoozeState.value)

            externalScope.cancel()
            vmScope.cancel()
        }

    @Test
    fun failedSubmitDoesNotTouchState() =
        runTest {
            val buttonDs = FakeNetworkButtonDataSource().apply {
                setFetchSnoozeResult(NetworkResult.Success(0L))
                setSnoozeResult(NetworkResult.HttpError(500))
            }
            val configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = NetworkSnoozeRepository(
                networkButtonDataSource = buttonDs,
                serverConfigRepository = CachedServerConfigRepository(configDs, "key", externalScope),
                authRepository = makeAuthRepo(),
                statusSnapshotStore = FakeStatusSnapshotStore(),
                snoozeDoorEventBridge = SnoozeDoorEventBridge(),
                snoozeNotificationsOption = true,
                currentTimeSeconds = { 0L },
                externalScope = externalScope,
            )
            advanceUntilIdle()

            val submitted = repo.snoozeNotifications("1h", 0L)
            advanceUntilIdle()

            assertTrue(submitted is AppResult.Error, "Should surface the network failure")
            assertEquals(SnoozeState.NotSnoozing, repo.snoozeState.value)

            externalScope.cancel()
        }

    // Pins the HTTP-404 → ActionError.SnoozeEventChanged mapping. The
    // server returns 404 when `snoozeEventTimestamp` no longer matches
    // the current event — the dominant failure mode during
    // `OPENING`/`CLOSING` transitions (see docs/SNOOZE_BEHAVIOR.md).
    // The typed variant lets the UI surface a specific snackbar
    // ("Door state changed before snooze could apply") instead of the
    // generic NetworkError copy.
    @Test
    fun submit404MapsToSnoozeEventChanged() =
        runTest {
            val buttonDs = FakeNetworkButtonDataSource().apply {
                setFetchSnoozeResult(NetworkResult.Success(0L))
                setSnoozeResult(NetworkResult.HttpError(404))
            }
            val configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = NetworkSnoozeRepository(
                networkButtonDataSource = buttonDs,
                serverConfigRepository = CachedServerConfigRepository(configDs, "key", externalScope),
                authRepository = makeAuthRepo(),
                statusSnapshotStore = FakeStatusSnapshotStore(),
                snoozeDoorEventBridge = SnoozeDoorEventBridge(),
                snoozeNotificationsOption = true,
                currentTimeSeconds = { 0L },
                externalScope = externalScope,
            )
            advanceUntilIdle()

            val submitted = repo.snoozeNotifications("1h", 0L)
            advanceUntilIdle()

            assertEquals(
                AppResult.Error(ActionError.SnoozeEventChanged),
                submitted,
                "HTTP 404 on snooze submit should map to SnoozeEventChanged",
            )
            assertEquals(SnoozeState.NotSnoozing, repo.snoozeState.value)

            externalScope.cancel()
        }

    // Sanity counterpart to submit404MapsToSnoozeEventChanged: any other
    // HTTP error code should keep the generic NetworkFailed mapping.
    // Without this, a future refactor that broadened the 404 branch to
    // catch other codes would slip past CI.
    @Test
    fun submit500MapsToNetworkFailedNotSnoozeEventChanged() =
        runTest {
            val buttonDs = FakeNetworkButtonDataSource().apply {
                setFetchSnoozeResult(NetworkResult.Success(0L))
                setSnoozeResult(NetworkResult.HttpError(500))
            }
            val configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = NetworkSnoozeRepository(
                networkButtonDataSource = buttonDs,
                serverConfigRepository = CachedServerConfigRepository(configDs, "key", externalScope),
                authRepository = makeAuthRepo(),
                statusSnapshotStore = FakeStatusSnapshotStore(),
                snoozeDoorEventBridge = SnoozeDoorEventBridge(),
                snoozeNotificationsOption = true,
                currentTimeSeconds = { 0L },
                externalScope = externalScope,
            )
            advanceUntilIdle()

            val submitted = repo.snoozeNotifications("1h", 0L)
            advanceUntilIdle()

            assertEquals(
                AppResult.Error(ActionError.NetworkFailed),
                submitted,
                "Non-404 HTTP errors should keep the generic NetworkFailed mapping",
            )

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
                serverConfigRepository = CachedServerConfigRepository(configDs, "key", externalScope),
                authRepository = makeAuthRepo(),
                statusSnapshotStore = FakeStatusSnapshotStore(),
                snoozeDoorEventBridge = SnoozeDoorEventBridge(),
                snoozeNotificationsOption = false,
                currentTimeSeconds = { 0L },
                externalScope = externalScope,
            )
            // Feature-disabled path has a delay(500); advanceUntilIdle covers it.
            advanceUntilIdle()

            assertEquals(SnoozeState.NotSnoozing, repo.snoozeState.value)
            assertEquals(0, buttonDs.fetchSnoozeCount)

            externalScope.cancel()
        }

    @Test
    fun snoozeReturnsNotAuthenticatedWhenIdTokenNull() =
        runTest {
            // ADR-027: the repo handles the case where AuthRepository
            // returns no token (sign-in race or sign-out mid-call).
            val buttonDs = FakeNetworkButtonDataSource()
            val configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val authRepo = FakeAuthRepository() // no setIdTokenResult — getIdToken returns null

            val repo = NetworkSnoozeRepository(
                networkButtonDataSource = buttonDs,
                serverConfigRepository = CachedServerConfigRepository(configDs, "key", externalScope),
                authRepository = authRepo,
                statusSnapshotStore = FakeStatusSnapshotStore(),
                snoozeDoorEventBridge = SnoozeDoorEventBridge(),
                snoozeNotificationsOption = true,
                currentTimeSeconds = { 0L },
                externalScope = externalScope,
            )
            advanceUntilIdle()
            buttonDs.setSnoozeResult(NetworkResult.Success(123L))
            val priorSnoozeCount = buttonDs.snoozeCount

            val result = repo.snoozeNotifications(
                snoozeDurationHours = "1h",
                snoozeEventTimestampSeconds = 100L,
            )

            assertEquals(AppResult.Error(ActionError.NotAuthenticated), result)
            assertEquals(priorSnoozeCount, buttonDs.snoozeCount, "Should NOT call data source when token is null")

            externalScope.cancel()
        }

    // Persisted-snapshot tests (STATUS_CACHE_PLAN.md D3) — hydration,
    // fetch-TTL, the door-event hook, and write-through.

    private fun seededStore(
        endTimeSeconds: Long,
        fetchedAtSeconds: Long,
    ): FakeStatusSnapshotStore =
        FakeStatusSnapshotStore().apply {
            seed(
                SnoozeSnapshot.KEY,
                SnoozeSnapshot.SCHEMA_VERSION,
                StatusSnapshot(
                    payload = SnoozeSnapshotDto(endTimeSeconds = endTimeSeconds),
                    fetchedAtEpochSeconds = fetchedAtSeconds,
                    confirmedAtEpochSeconds = fetchedAtSeconds,
                ),
            )
        }

    private fun makeRepo(
        buttonDs: FakeNetworkButtonDataSource,
        externalScope: CoroutineScope,
        store: FakeStatusSnapshotStore = FakeStatusSnapshotStore(),
        bridge: SnoozeDoorEventBridge = SnoozeDoorEventBridge(),
        nowSeconds: () -> Long = { 1_000L },
    ): NetworkSnoozeRepository {
        val configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
        return NetworkSnoozeRepository(
            networkButtonDataSource = buttonDs,
            serverConfigRepository = CachedServerConfigRepository(configDs, "key", externalScope),
            authRepository = makeAuthRepo(),
            statusSnapshotStore = store,
            snoozeDoorEventBridge = bridge,
            snoozeNotificationsOption = true,
            currentTimeSeconds = nowSeconds,
            externalScope = externalScope,
        )
    }

    @Test
    fun hydrationSeedsActiveSnoozeAndFreshSnapshotSkipsInitFetch() =
        runTest {
            val buttonDs = FakeNetworkButtonDataSource()
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            // Persisted 60s ago, snooze active until 5000 (now = 1000).
            val store = seededStore(endTimeSeconds = 5_000L, fetchedAtSeconds = 940L)

            val repo = makeRepo(buttonDs, externalScope, store)
            advanceUntilIdle()

            assertEquals(SnoozeState.Snoozing(5_000L), repo.snoozeState.value)
            assertEquals(0, buttonDs.fetchSnoozeCount, "Fresh snapshot must skip the init fetch")
            externalScope.cancel()
        }

    @Test
    fun hydrationRecomputesExpiredSnoozeAsNotSnoozing() =
        runTest {
            val buttonDs = FakeNetworkButtonDataSource()
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            // The raw end time is persisted, never the state: a snooze that
            // ended while the app was dead hydrates as NotSnoozing.
            val store = seededStore(endTimeSeconds = 500L, fetchedAtSeconds = 940L)

            val repo = makeRepo(buttonDs, externalScope, store)
            advanceUntilIdle()

            assertEquals(SnoozeState.NotSnoozing, repo.snoozeState.value)
            externalScope.cancel()
        }

    @Test
    fun staleSnapshotHydratesThenInitFetchRuns() =
        runTest {
            val buttonDs = FakeNetworkButtonDataSource().apply {
                setFetchSnoozeResult(NetworkResult.Success(7_000L))
            }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            // Persisted 10 minutes ago — past the 5-minute fetch-TTL.
            val store = seededStore(endTimeSeconds = 5_000L, fetchedAtSeconds = 400L)

            val repo = makeRepo(buttonDs, externalScope, store, nowSeconds = { 1_000L })
            advanceUntilIdle()

            assertEquals(1, buttonDs.fetchSnoozeCount, "Stale snapshot must revalidate")
            assertEquals(SnoozeState.Snoozing(7_000L), repo.snoozeState.value)
            externalScope.cancel()
        }

    @Test
    fun failedInitFetchKeepsHydratedStateInsteadOfClearingToNotSnoozing() =
        runTest {
            val buttonDs = FakeNetworkButtonDataSource().apply {
                setFetchSnoozeResult(NetworkResult.ConnectionFailed)
            }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            // Stale snapshot (so the init fetch runs) with an active snooze.
            val store = seededStore(endTimeSeconds = 5_000L, fetchedAtSeconds = 400L)

            val repo = makeRepo(buttonDs, externalScope, store)
            advanceUntilIdle()

            // clearLoadingState only converts the Loading SENTINEL; the
            // hydrated verdict must survive an offline cold start.
            assertEquals(SnoozeState.Snoozing(5_000L), repo.snoozeState.value)
            externalScope.cancel()
        }

    @Test
    fun revalidateSkipsWhenFreshAndFetchesWhenStale() =
        runTest {
            var now = 1_000L
            val buttonDs = FakeNetworkButtonDataSource().apply {
                setFetchSnoozeResult(NetworkResult.Success(0L))
            }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val repo = makeRepo(buttonDs, externalScope, nowSeconds = { now })
            advanceUntilIdle()
            val fetchesAfterInit = buttonDs.fetchSnoozeCount

            // Immediately after the init fetch: fresh — no network.
            repo.revalidateSnoozeIfStale()
            advanceUntilIdle()
            assertEquals(fetchesAfterInit, buttonDs.fetchSnoozeCount)

            // Past the TTL: revalidate fetches.
            now += 6L * 60L
            repo.revalidateSnoozeIfStale()
            advanceUntilIdle()
            assertEquals(fetchesAfterInit + 1, buttonDs.fetchSnoozeCount)
            externalScope.cancel()
        }

    @Test
    fun acceptedFetchPersistsEndTime() =
        runTest {
            val buttonDs = FakeNetworkButtonDataSource().apply {
                setFetchSnoozeResult(NetworkResult.Success(5_000L))
            }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val store = FakeStatusSnapshotStore()

            makeRepo(buttonDs, externalScope, store)
            advanceUntilIdle()

            val persisted = store.read(
                SnoozeSnapshot.KEY,
                SnoozeSnapshot.SCHEMA_VERSION,
                SnoozeSnapshotDto.serializer(),
            )
            assertEquals(5_000L, persisted?.payload?.endTimeSeconds)
            externalScope.cancel()
        }

    @Test
    fun doorEventWhileSnoozingTriggersOneRefetch() =
        runTest {
            val buttonDs = FakeNetworkButtonDataSource().apply {
                setFetchSnoozeResult(NetworkResult.Success(5_000L))
            }
            val bridge = SnoozeDoorEventBridge()
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val repo = makeRepo(buttonDs, externalScope, bridge = bridge)
            advanceUntilIdle()
            assertEquals(SnoozeState.Snoozing(5_000L), repo.snoozeState.value)
            val fetchesBefore = buttonDs.fetchSnoozeCount

            // Server voids the snooze on any door event; the hook refetches.
            buttonDs.setFetchSnoozeResult(NetworkResult.Success(0L))
            bridge.notifyDoorEvent()
            advanceUntilIdle()

            assertEquals(fetchesBefore + 1, buttonDs.fetchSnoozeCount)
            assertEquals(SnoozeState.NotSnoozing, repo.snoozeState.value)
            externalScope.cancel()
        }

    @Test
    fun doorEventWhileNotSnoozingDoesNotFetch() =
        runTest {
            val buttonDs = FakeNetworkButtonDataSource().apply {
                setFetchSnoozeResult(NetworkResult.Success(0L))
            }
            val bridge = SnoozeDoorEventBridge()
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val repo = makeRepo(buttonDs, externalScope, bridge = bridge)
            advanceUntilIdle()
            assertEquals(SnoozeState.NotSnoozing, repo.snoozeState.value)
            val fetchesBefore = buttonDs.fetchSnoozeCount

            bridge.notifyDoorEvent()
            advanceUntilIdle()

            assertEquals(fetchesBefore, buttonDs.fetchSnoozeCount)
            externalScope.cancel()
        }
}
