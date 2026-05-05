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

package com.chriscartland.garage.data.repository

import com.chriscartland.garage.data.NetworkButtonHealthDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.ButtonHealth
import com.chriscartland.garage.domain.model.ButtonHealthError
import com.chriscartland.garage.domain.model.ButtonHealthState
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.model.ServerConfig
import com.chriscartland.garage.testcommon.FakeNetworkConfigDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkButtonHealthRepositoryTest {
    private val validConfig = NetworkResult.Success(
        ServerConfig(
            buildTimestamp = "door",
            remoteButtonBuildTimestamp = "button",
            remoteButtonPushKey = "key",
        ),
    )

    private fun makeRepo(
        ds: FakeNetworkButtonHealthDataSource,
        scope: CoroutineScope,
    ): NetworkButtonHealthRepository {
        val configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
        return NetworkButtonHealthRepository(
            networkButtonHealthDataSource = ds,
            serverConfigRepository = CachedServerConfigRepository(configDs, "key", scope),
            externalScope = scope,
        )
    }

    @Test
    fun fetchButtonHealth_success_writesStateAndReturnsValue() =
        runTest {
            val ds = FakeNetworkButtonHealthDataSource().apply {
                setResult(NetworkResult.Success(ButtonHealth(ButtonHealthState.ONLINE, 1000L)))
            }
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val repo = makeRepo(ds, scope)

            val result = repo.fetchButtonHealth(idToken = "token")
            advanceUntilIdle()

            val success = assertIs<AppResult.Success<ButtonHealth>>(result)
            assertEquals(ButtonHealthState.ONLINE, success.data.state)
            val complete = assertIs<LoadingResult.Complete<ButtonHealth>>(repo.buttonHealth.value)
            assertEquals(ButtonHealth(ButtonHealthState.ONLINE, 1000L), complete.data)
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }

    @Test
    fun fetchButtonHealth_http401_returnsForbidden() =
        runTest {
            val ds = FakeNetworkButtonHealthDataSource().apply {
                setResult(NetworkResult.HttpError(401))
            }
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val repo = makeRepo(ds, scope)

            val result = repo.fetchButtonHealth(idToken = "token")
            advanceUntilIdle()

            val error = assertIs<AppResult.Error<ButtonHealthError>>(result)
            assertEquals(ButtonHealthError.Forbidden(), error.error)
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }

    @Test
    fun fetchButtonHealth_http403_returnsForbidden() =
        runTest {
            val ds = FakeNetworkButtonHealthDataSource().apply {
                setResult(NetworkResult.HttpError(403))
            }
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val repo = makeRepo(ds, scope)

            val result = repo.fetchButtonHealth(idToken = "token")
            advanceUntilIdle()

            val error = assertIs<AppResult.Error<ButtonHealthError>>(result)
            assertEquals(ButtonHealthError.Forbidden(), error.error)
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }

    @Test
    fun fetchButtonHealth_http500_returnsNetwork() =
        runTest {
            val ds = FakeNetworkButtonHealthDataSource().apply {
                setResult(NetworkResult.HttpError(500))
            }
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val repo = makeRepo(ds, scope)

            val result = repo.fetchButtonHealth(idToken = "token")
            advanceUntilIdle()

            val error = assertIs<AppResult.Error<ButtonHealthError>>(result)
            assertEquals(ButtonHealthError.Network(), error.error)
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }

    @Test
    fun fetchButtonHealth_connectionFailed_returnsNetwork() =
        runTest {
            val ds = FakeNetworkButtonHealthDataSource().apply {
                setResult(NetworkResult.ConnectionFailed)
            }
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val repo = makeRepo(ds, scope)

            val result = repo.fetchButtonHealth(idToken = "token")
            advanceUntilIdle()

            val error = assertIs<AppResult.Error<ButtonHealthError>>(result)
            assertEquals(ButtonHealthError.Network(), error.error)
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }

    @Test
    fun applyFcmUpdate_writesState() =
        runTest {
            val ds = FakeNetworkButtonHealthDataSource()
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val repo = makeRepo(ds, scope)

            repo.applyFcmUpdate(ButtonHealth(ButtonHealthState.OFFLINE, 5000L))
            advanceUntilIdle()

            val complete = assertIs<LoadingResult.Complete<ButtonHealth>>(repo.buttonHealth.value)
            assertEquals(ButtonHealth(ButtonHealthState.OFFLINE, 5000L), complete.data)
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }

    // shouldOverwrite truth-table tests — pin the FCM-vs-fetch ordering rule.

    @Test
    fun shouldOverwrite_acceptsAnythingWhenCurrentIsLoading() {
        val repo = NetworkButtonHealthRepository(
            networkButtonHealthDataSource = FakeNetworkButtonHealthDataSource(),
            serverConfigRepository = CachedServerConfigRepository(
                FakeNetworkConfigDataSource(),
                "key",
                CoroutineScope(SupervisorJob()),
            ),
            externalScope = CoroutineScope(SupervisorJob()),
        )
        assertTrue(
            repo.shouldOverwrite(
                LoadingResult.Loading(null),
                ButtonHealth(ButtonHealthState.ONLINE, 100L),
            ),
        )
    }

    @Test
    fun shouldOverwrite_anyKnownStateBeatsCurrentUnknown() {
        val repo = makeRepoForRuleTest()
        assertTrue(
            repo.shouldOverwrite(
                LoadingResult.Complete(ButtonHealth(ButtonHealthState.UNKNOWN, null)),
                ButtonHealth(ButtonHealthState.ONLINE, 100L),
            ),
        )
        assertTrue(
            repo.shouldOverwrite(
                LoadingResult.Complete(ButtonHealth(ButtonHealthState.UNKNOWN, null)),
                ButtonHealth(ButtonHealthState.OFFLINE, 100L),
            ),
        )
    }

    @Test
    fun shouldOverwrite_unknownNeverBeatsKnownState() {
        val repo = makeRepoForRuleTest()
        assertEquals(
            false,
            repo.shouldOverwrite(
                LoadingResult.Complete(ButtonHealth(ButtonHealthState.ONLINE, 100L)),
                ButtonHealth(ButtonHealthState.UNKNOWN, null),
            ),
        )
    }

    @Test
    fun shouldOverwrite_strictlyNewerTimestampWins() {
        val repo = makeRepoForRuleTest()
        // Strictly newer wins.
        assertTrue(
            repo.shouldOverwrite(
                LoadingResult.Complete(ButtonHealth(ButtonHealthState.ONLINE, 100L)),
                ButtonHealth(ButtonHealthState.OFFLINE, 200L),
            ),
        )
        // Equal timestamp does NOT win (`>`, not `>=`).
        assertEquals(
            false,
            repo.shouldOverwrite(
                LoadingResult.Complete(ButtonHealth(ButtonHealthState.ONLINE, 100L)),
                ButtonHealth(ButtonHealthState.OFFLINE, 100L),
            ),
        )
        // Older timestamp does not win.
        assertEquals(
            false,
            repo.shouldOverwrite(
                LoadingResult.Complete(ButtonHealth(ButtonHealthState.ONLINE, 100L)),
                ButtonHealth(ButtonHealthState.OFFLINE, 50L),
            ),
        )
    }

    private fun makeRepoForRuleTest(): NetworkButtonHealthRepository =
        NetworkButtonHealthRepository(
            networkButtonHealthDataSource = FakeNetworkButtonHealthDataSource(),
            serverConfigRepository = CachedServerConfigRepository(
                FakeNetworkConfigDataSource(),
                "key",
                CoroutineScope(SupervisorJob()),
            ),
            externalScope = CoroutineScope(SupervisorJob()),
        )
}

/**
 * Fake [NetworkButtonHealthDataSource] for repository unit tests.
 * Inline (not in test-common) because only this test consumes it for now —
 * promote when a second test arrives.
 */
private class FakeNetworkButtonHealthDataSource : NetworkButtonHealthDataSource {
    private var result: NetworkResult<ButtonHealth> =
        NetworkResult.Success(ButtonHealth(ButtonHealthState.UNKNOWN, null))

    fun setResult(value: NetworkResult<ButtonHealth>) {
        result = value
    }

    override suspend fun fetchButtonHealth(
        buildTimestamp: String,
        remoteButtonPushKey: String,
        idToken: String,
    ): NetworkResult<ButtonHealth> = result
}
