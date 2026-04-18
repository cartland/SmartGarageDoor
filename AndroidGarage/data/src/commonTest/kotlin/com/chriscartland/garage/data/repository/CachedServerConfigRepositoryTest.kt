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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * Integration tests for [CachedServerConfigRepository] with fake network data source.
 * Tests caching behavior, null handling, and cache invalidation via fetchServerConfig.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CachedServerConfigRepositoryTest {
    private val configDataSource = FakeNetworkConfigDataSource()

    private fun createRepository() =
        CachedServerConfigRepository(
            networkConfigDataSource = configDataSource,
            serverConfigKey = "test-key",
        )

    @Test
    fun getServerConfigCachedFetchesOnFirstCall() =
        runTest {
            val config = ServerConfig(
                buildTimestamp = "2024-01-15T00:00:00Z",
                remoteButtonBuildTimestamp = "2024-01-15T00:00:00Z",
                remoteButtonPushKey = "key",
            )
            configDataSource.setServerConfigResult(NetworkResult.Success(config))

            val repo = createRepository()
            val result = repo.getServerConfigCached()

            assertEquals(config, result)
            assertEquals(1, configDataSource.fetchCount)
        }

    @Test
    fun getServerConfigCachedReturnsCacheOnSecondCall() =
        runTest {
            val config = ServerConfig(
                buildTimestamp = "2024-01-15T00:00:00Z",
                remoteButtonBuildTimestamp = "2024-01-15T00:00:00Z",
                remoteButtonPushKey = "key",
            )
            configDataSource.setServerConfigResult(NetworkResult.Success(config))

            val repo = createRepository()
            repo.getServerConfigCached()
            repo.getServerConfigCached()

            assertEquals(1, configDataSource.fetchCount)
        }

    @Test
    fun getServerConfigCachedReturnsNullWhenNetworkFails() =
        runTest {
            configDataSource.setServerConfigResult(NetworkResult.ConnectionFailed)

            val repo = createRepository()
            assertNull(repo.getServerConfigCached())
            assertEquals(1, configDataSource.fetchCount)
        }

    @Test
    fun getServerConfigCachedRetriesAfterNullResponse() =
        runTest {
            configDataSource.setServerConfigResult(NetworkResult.ConnectionFailed)

            val repo = createRepository()
            assertNull(repo.getServerConfigCached())

            // Second call should retry since cache is still null
            val config = ServerConfig(
                buildTimestamp = "2024-01-15T00:00:00Z",
                remoteButtonBuildTimestamp = "2024-01-15T00:00:00Z",
                remoteButtonPushKey = "key",
            )
            configDataSource.setServerConfigResult(NetworkResult.Success(config))
            assertEquals(config, repo.getServerConfigCached())
            assertEquals(2, configDataSource.fetchCount)
        }

    @Test
    fun fetchServerConfigUpdatesCache() =
        runTest {
            val config1 = ServerConfig(
                buildTimestamp = "2024-01-15T00:00:00Z",
                remoteButtonBuildTimestamp = "2024-01-15T00:00:00Z",
                remoteButtonPushKey = "key1",
            )
            configDataSource.setServerConfigResult(NetworkResult.Success(config1))

            val repo = createRepository()
            repo.getServerConfigCached()

            // Update network response and force refresh
            val config2 = ServerConfig(
                buildTimestamp = "2024-01-16T00:00:00Z",
                remoteButtonBuildTimestamp = "2024-01-16T00:00:00Z",
                remoteButtonPushKey = "key2",
            )
            configDataSource.setServerConfigResult(NetworkResult.Success(config2))
            repo.fetchServerConfig()

            // Cached value should now be updated
            assertEquals(config2, repo.getServerConfigCached())
            // 3 fetches: initial getServerConfigCached, fetchServerConfig, getServerConfigCached (cached)
            assertEquals(2, configDataSource.fetchCount)
        }

    @Test
    fun mutexReleasedAfterFetchThrowsSoSubsequentCallsDoNotDeadlock() =
        runTest {
            val validConfig = ServerConfig(
                buildTimestamp = "2024-01-15T00:00:00Z",
                remoteButtonBuildTimestamp = "2024-01-15T00:00:00Z",
                remoteButtonPushKey = "key",
            )
            val throwingDataSource = object : NetworkConfigDataSource {
                var throwNext = true

                override suspend fun fetchServerConfig(serverConfigKey: String): NetworkResult<ServerConfig> {
                    if (throwNext) {
                        throwNext = false
                        throw RuntimeException("simulated transient error")
                    }
                    return NetworkResult.Success(validConfig)
                }
            }
            val repo = CachedServerConfigRepository(throwingDataSource, "key")

            // First call throws. Before the fix, the bare mutex.lock() + unlock()
            // pair leaked the mutex on exception, permanently blocking every
            // future caller.
            try {
                repo.getServerConfigCached()
                fail("Expected exception was not thrown")
            } catch (_: RuntimeException) {
                // expected
            }

            // Second call must return — if the mutex is leaked, this hangs.
            val result = withTimeout(1_000) { repo.getServerConfigCached() }
            assertEquals(validConfig, result)
        }

    @Test
    fun fetchServerConfigWithNullDoesNotOverwriteCache() =
        runTest {
            val config = ServerConfig(
                buildTimestamp = "2024-01-15T00:00:00Z",
                remoteButtonBuildTimestamp = "2024-01-15T00:00:00Z",
                remoteButtonPushKey = "key",
            )
            configDataSource.setServerConfigResult(NetworkResult.Success(config))

            val repo = createRepository()
            repo.getServerConfigCached()

            // Network now returns null
            configDataSource.setServerConfigResult(NetworkResult.ConnectionFailed)
            repo.fetchServerConfig()

            // Cache should retain the old value
            assertEquals(config, repo.getServerConfigCached())
        }
}
