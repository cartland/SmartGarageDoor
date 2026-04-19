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

    /**
     * Documents the contract of [CachedServerConfigRepository.fetchServerConfig]:
     * it is a force-refresh. A server-side change in config is visible to the
     * next caller without needing a process restart.
     */
    @Test
    fun fetchServerConfigAlwaysRefreshesCache() =
        runTest {
            val v1 = sampleConfig
            val v2 = sampleConfig.copy(buildTimestamp = "2024-01-16T00:00:00Z")
            val ds = FakeNetworkConfigDataSource().apply {
                setServerConfigResult(NetworkResult.Success(v1))
            }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = CachedServerConfigRepository(ds, "test-key", externalScope)
            advanceUntilIdle()
            assertEquals(v1, repo.serverConfig.value)

            // Server changes. fetchServerConfig must see the new value.
            ds.setServerConfigResult(NetworkResult.Success(v2))
            val refreshed = repo.fetchServerConfig()

            assertEquals(v2, refreshed)
            assertEquals(v2, repo.serverConfig.value)
            assertEquals(2, ds.fetchCount) // init + refresh

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

    /**
     * Null responses (HTTP error / connection failure) leave the cache alone
     * rather than overwriting a previously-successful value. Avoids a transient
     * network blip blowing away the last known-good config.
     */
    @Test
    fun nullFetchResultPreservesCachedValue() =
        runTest {
            val ds = FakeNetworkConfigDataSource().apply {
                setServerConfigResult(NetworkResult.Success(sampleConfig))
            }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = CachedServerConfigRepository(ds, "test-key", externalScope)
            advanceUntilIdle()
            assertEquals(sampleConfig, repo.serverConfig.value)

            ds.setServerConfigResult(NetworkResult.ConnectionFailed)
            val failResult = repo.fetchServerConfig()

            assertNull(failResult) // fetch itself reports failure
            assertEquals(sampleConfig, repo.serverConfig.value) // cache untouched

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

            // A null server config does NOT overwrite a cached value. The
            // fetch itself reports failure (null return); the cache stays
            // on the last known-good value.
            ds.setServerConfigResult(NetworkResult.ConnectionFailed)
            val failResult = repo.fetchServerConfig()
            assertNull(failResult)
            assertEquals(sampleConfig, repo.serverConfig.value)

            externalScope.cancel()
        }
}
