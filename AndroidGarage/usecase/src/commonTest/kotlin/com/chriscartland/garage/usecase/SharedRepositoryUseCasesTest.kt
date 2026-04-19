/*
 * Copyright 2024 Chris Cartland. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chriscartland.garage.usecase

import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.data.repository.CachedServerConfigRepository
import com.chriscartland.garage.data.repository.NetworkSnoozeRepository
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.ServerConfig
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.domain.repository.SnoozeRepository
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeNetworkButtonDataSource
import com.chriscartland.garage.testcommon.FakeNetworkConfigDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the intended architecture: multiple UseCase instances share a
 * single [SnoozeRepository] singleton. A submit via `SnoozeNotificationsUseCase`
 * must (a) return the new [SnoozeState] as a structured result and (b) be
 * observable via a separately-constructed `ObserveSnoozeStateUseCase`
 * backed by the SAME repository — proving the observe and mutate paths
 * converge on one source of truth.
 *
 * Uses the REAL [NetworkSnoozeRepository] with fake data sources so the
 * test exercises the production flow plumbing end-to-end (POST → flow
 * write → observer → return value). This is the architectural contract:
 * "UseCases are non-singleton, but they all share one repository singleton."
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedRepositoryUseCasesTest {
    private lateinit var externalScope: CoroutineScope

    private val validConfig = NetworkResult.Success(
        ServerConfig(
            buildTimestamp = "test",
            remoteButtonBuildTimestamp = "test",
            remoteButtonPushKey = "key",
        ),
    )

    @BeforeTest
    fun setup() {
        externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    }

    @AfterTest
    fun teardown() {
        externalScope.cancel()
    }

    private fun buildSharedRepository(
        now: Long,
        fetchResult: NetworkResult<Long>,
    ): Pair<FakeNetworkButtonDataSource, SnoozeRepository> {
        val buttonDs = FakeNetworkButtonDataSource().apply {
            setFetchSnoozeResult(fetchResult)
        }
        val configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
        val repo: SnoozeRepository = NetworkSnoozeRepository(
            networkButtonDataSource = buttonDs,
            serverConfigRepository = CachedServerConfigRepository(configDs, "key"),
            snoozeNotificationsOption = true,
            currentTimeSeconds = { now },
            externalScope = externalScope,
        )
        return buttonDs to repo
    }

    private fun authenticatedAuthRepo(): FakeAuthRepository =
        FakeAuthRepository().apply {
            setAuthState(
                AuthState.Authenticated(
                    user = User(
                        name = DisplayName("Test"),
                        email = Email("t@t.test"),
                        idToken = FirebaseIdToken(idToken = "tok", exp = Long.MAX_VALUE),
                    ),
                ),
            )
        }

    @Test
    fun submitViaOneUseCaseIsObservableViaAnother() =
        runTest {
            val now = 1_000L
            val serverEndTime = 5_000L
            val (buttonDs, sharedRepo) = buildSharedRepository(
                now = now,
                fetchResult = NetworkResult.Success(0L), // init fetch: no snooze yet
            )
            buttonDs.setSnoozeResult(NetworkResult.Success(serverEndTime))
            val authRepo = authenticatedAuthRepo()

            // Construct TWO independent UseCases — both new instances, both
            // wired to the SAME shared repository. This is the production
            // pattern: UseCases are created fresh per access (not @Singleton),
            // the Repository is the only @Singleton.
            val ensureFreshToken = EnsureFreshIdTokenUseCase(authRepo)
            val submitUseCase = SnoozeNotificationsUseCase(ensureFreshToken, authRepo, sharedRepo)
            val observeUseCase = ObserveSnoozeStateUseCase(sharedRepo)

            advanceUntilIdle()
            // Baseline: both paths agree — no active snooze.
            assertEquals(SnoozeState.NotSnoozing, observeUseCase().first())

            // Submit via the first UseCase. The return value is the new state.
            val submitResult = submitUseCase(
                snoozeDurationHours = "1h",
                lastChangeTimeSeconds = 0L,
            )
            advanceUntilIdle()

            // 1) Return value is a structured AppResult.Success carrying the
            //    authoritative SnoozeState from the server response.
            assertTrue(submitResult is AppResult.Success, "Submit should succeed")
            val returnedState = (submitResult as AppResult.Success).data
            assertEquals(SnoozeState.Snoozing(serverEndTime), returnedState)

            // 2) The OTHER UseCase, constructed separately, observes the same
            //    value via the shared repository singleton. This proves the
            //    repo owns one source of truth and the two UseCase instances
            //    don't have independent state.
            val observedState = observeUseCase().first()
            assertEquals(SnoozeState.Snoozing(serverEndTime), observedState)

            // 3) Return value and observed value are the SAME state object —
            //    the repo writes once and that write drives both paths.
            assertEquals(returnedState, observedState)

            // 4) Only one submit + one init fetch hit the network. No extra
            //    GET after submit — the POST response is authoritative.
            assertEquals(1, buttonDs.snoozeCount)
            assertEquals(1, buttonDs.fetchSnoozeCount)
        }

    @Test
    fun clearViaOneUseCaseIsObservableViaAnother() =
        runTest {
            // Same pattern for the clear ("Do not snooze") case. Server returns
            // an end time equal to now (immediately expired) → repo maps to
            // NotSnoozing → return value and observed value both show the clear.
            val now = 1_000L
            val (buttonDs, sharedRepo) = buildSharedRepository(
                now = now,
                fetchResult = NetworkResult.Success(5_000L), // init: active snooze
            )
            buttonDs.setSnoozeResult(NetworkResult.Success(now)) // clear — end time == now
            val authRepo = authenticatedAuthRepo()

            val ensureFreshToken = EnsureFreshIdTokenUseCase(authRepo)
            val submitUseCase = SnoozeNotificationsUseCase(ensureFreshToken, authRepo, sharedRepo)
            val observeUseCase = ObserveSnoozeStateUseCase(sharedRepo)

            advanceUntilIdle()
            assertEquals(SnoozeState.Snoozing(5_000L), observeUseCase().first())

            val submitResult = submitUseCase(
                snoozeDurationHours = "0h",
                lastChangeTimeSeconds = 0L,
            )
            advanceUntilIdle()

            assertTrue(submitResult is AppResult.Success, "Clear should succeed")
            assertEquals(SnoozeState.NotSnoozing, (submitResult as AppResult.Success).data)

            // The observe UseCase — a different instance, same repo — now sees
            // the cleared state too.
            assertEquals(SnoozeState.NotSnoozing, observeUseCase().first())
        }
}
