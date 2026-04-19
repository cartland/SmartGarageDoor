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

import com.chriscartland.garage.data.NetworkConfigDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.ServerConfig
import com.chriscartland.garage.testcommon.FakeNetworkConfigDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Integration tests for [CachedServerConfigRepository] (ADR-022 shape).
 *
 * The repository now owns a `StateFlow<ServerConfig?>`; an always-on fetch
 * runs on `externalScope` at construction. Callers read `serverConfig.value`
 * for the current cached value or call `fetchServerConfig()` to refresh.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CachedServerConfigRepositoryTest {
    private val sampleConfig = ServerConfig(
        buildTimestamp = "2024-01-15T00:00:00Z",
        remoteButtonBuildTimestamp = "2024-01-15T00:00:00Z",
        remoteButtonPushKey = "key",
    )

    @Test
    fun initFetchPopulatesServerConfigStateFlow() =
        runTest {
            val ds = FakeNetworkConfigDataSource().apply {
                setServerConfigResult(NetworkResult.Success(sampleConfig))
            }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = CachedServerConfigRepository(ds, "test-key", externalScope)
            advanceUntilIdle()

            assertEquals(sampleConfig, repo.serverConfig.value)
            assertEquals(1, ds.fetchCount)

            externalScope.cancel()
        }

    @Test
    fun fetchServerConfigReturnsCachedValueOnSecondCall() =
        runTest {
            val ds = FakeNetworkConfigDataSource().apply {
                setServerConfigResult(NetworkResult.Success(sampleConfig))
            }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = CachedServerConfigRepository(ds, "test-key", externalScope)
            advanceUntilIdle()
            // init fetch counted.

            val secondCall = repo.fetchServerConfig()
            assertEquals(sampleConfig, secondCall)
            // Second call served from cache — no extra network fetch.
            assertEquals(1, ds.fetchCount)

            externalScope.cancel()
        }

    @Test
    fun initFetchLeavesValueNullOnNetworkFailure() =
        runTest {
            val ds = FakeNetworkConfigDataSource().apply {
                setServerConfigResult(NetworkResult.ConnectionFailed)
            }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = CachedServerConfigRepository(ds, "test-key", externalScope)
            advanceUntilIdle()

            assertNull(repo.serverConfig.value)

            externalScope.cancel()
        }

    @Test
    fun fetchServerConfigRetriesAfterInitFailure() =
        runTest {
            val ds = FakeNetworkConfigDataSource().apply {
                setServerConfigResult(NetworkResult.ConnectionFailed)
            }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = CachedServerConfigRepository(ds, "test-key", externalScope)
            advanceUntilIdle()
            assertNull(repo.serverConfig.value)

            ds.setServerConfigResult(NetworkResult.Success(sampleConfig))
            val result = repo.fetchServerConfig()
            assertEquals(sampleConfig, result)
            assertEquals(sampleConfig, repo.serverConfig.value)
            assertEquals(2, ds.fetchCount)

            externalScope.cancel()
        }

    @Test
    fun fetchAfterInitKeepsSameInstance() =
        runTest {
            val ds = FakeNetworkConfigDataSource().apply {
                setServerConfigResult(NetworkResult.Success(sampleConfig))
            }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = CachedServerConfigRepository(ds, "test-key", externalScope)
            advanceUntilIdle()

            // Update network response and force-refresh. Because the cache is
            // already populated, fetchServerConfig coalesces to the cached value
            // (first-write-wins on success).
            val newConfig = sampleConfig.copy(buildTimestamp = "2024-01-16T00:00:00Z")
            ds.setServerConfigResult(NetworkResult.Success(newConfig))
            val fetched = repo.fetchServerConfig()

            assertEquals(sampleConfig, fetched)
            assertEquals(sampleConfig, repo.serverConfig.value)
            assertEquals(1, ds.fetchCount)

            externalScope.cancel()
        }

    @Test
    fun fetchSwallowsExceptionsAndMutexReleasedForSubsequentCalls() =
        runTest {
            val throwingDataSource = object : NetworkConfigDataSource {
                var throwNext = true

                override suspend fun fetchServerConfig(serverConfigKey: String): NetworkResult<ServerConfig> {
                    if (throwNext) {
                        throwNext = false
                        throw RuntimeException("simulated transient error")
                    }
                    return NetworkResult.Success(sampleConfig)
                }
            }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = CachedServerConfigRepository(throwingDataSource, "key", externalScope)
            // First fetch (driven by init) throws internally; the try/catch
            // around the data-source call swallows it and the cache stays null.
            advanceUntilIdle()
            assertNull(repo.serverConfig.value)

            // Subsequent fetch must return — if the mutex was leaked by an
            // uncaught exception or if the fetch-in-flight flag was stuck,
            // this would hang.
            val result = withTimeout(1_000) { repo.fetchServerConfig() }
            assertEquals(sampleConfig, result)

            externalScope.cancel()
        }

    @Test
    fun initFailureDoesNotBlockLaterFetch() =
        runTest {
            val ds = FakeNetworkConfigDataSource().apply {
                setServerConfigResult(NetworkResult.ConnectionFailed)
            }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = CachedServerConfigRepository(ds, "key", externalScope)
            advanceUntilIdle()
            assertNull(repo.serverConfig.value)

            // Fix the network and retry — should succeed.
            ds.setServerConfigResult(NetworkResult.Success(sampleConfig))
            val result = repo.fetchServerConfig()
            assertEquals(sampleConfig, result)

            // But a null server config does NOT overwrite a cached value.
            ds.setServerConfigResult(NetworkResult.ConnectionFailed)
            val failResult = repo.fetchServerConfig()
            assertEquals(sampleConfig, failResult) // cached value won
            assertEquals(sampleConfig, repo.serverConfig.value)

            externalScope.cancel()
        }
}
