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

package com.chriscartland.garage.config

import com.chriscartland.garage.data.NetworkConfigDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.data.repository.CachedServerConfigRepository
import com.chriscartland.garage.domain.model.ServerConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FakeNetworkConfigDataSource : NetworkConfigDataSource {
    var serverConfigResult: NetworkResult<ServerConfig> = NetworkResult.ConnectionFailed
    var fetchCount = 0

    override suspend fun fetchServerConfig(serverConfigKey: String): NetworkResult<ServerConfig> {
        fetchCount++
        return serverConfigResult
    }
}

class ServerConfigRepositoryTest {
    private lateinit var networkConfig: FakeNetworkConfigDataSource
    private lateinit var repo: CachedServerConfigRepository

    private val validConfig = ServerConfig(
        buildTimestamp = "2024-01-15T00:00:00Z",
        remoteButtonBuildTimestamp = "2024-01-15T00:00:00Z",
        remoteButtonPushKey = "test-push-key",
    )

    @Before
    fun setup() {
        networkConfig = FakeNetworkConfigDataSource()
        repo = CachedServerConfigRepository(networkConfig, "test-config-key")
    }

    @Test
    fun fetchServerConfigReturnsConfigOnSuccess() =
        runTest {
            networkConfig.serverConfigResult = NetworkResult.Success(validConfig)

            val result = repo.fetchServerConfig()

            assertNotNull(result)
            assertEquals("2024-01-15T00:00:00Z", result!!.buildTimestamp)
            assertEquals("test-push-key", result.remoteButtonPushKey)
        }

    @Test
    fun fetchServerConfigReturnsNullOnFailure() =
        runTest {
            networkConfig.serverConfigResult = NetworkResult.ConnectionFailed

            assertNull(repo.fetchServerConfig())
        }

    @Test
    fun getServerConfigCachedReturnsCachedValueOnSecondCall() =
        runTest {
            networkConfig.serverConfigResult = NetworkResult.Success(validConfig)

            val first = repo.getServerConfigCached()
            val second = repo.getServerConfigCached()

            assertEquals(first, second)
            assertEquals(1, networkConfig.fetchCount)
        }

    @Test
    fun getServerConfigCachedFetchesWhenNotCached() =
        runTest {
            networkConfig.serverConfigResult = NetworkResult.Success(validConfig)

            val result = repo.getServerConfigCached()

            assertNotNull(result)
            assertEquals(1, networkConfig.fetchCount)
        }

    @Test
    fun getServerConfigCachedReturnsNullWhenFetchFails() =
        runTest {
            networkConfig.serverConfigResult = NetworkResult.ConnectionFailed

            assertNull(repo.getServerConfigCached())
        }
}
