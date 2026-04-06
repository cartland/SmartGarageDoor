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
import com.chriscartland.garage.data.repository.ServerConfigRepositoryImpl
import com.chriscartland.garage.domain.model.ServerConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FakeNetworkConfigDataSource : NetworkConfigDataSource {
    var serverConfig: ServerConfig? = null
    var fetchCount = 0

    override suspend fun fetchServerConfig(serverConfigKey: String): ServerConfig? {
        fetchCount++
        return serverConfig
    }
}

class ServerConfigRepositoryTest {
    private lateinit var networkConfig: FakeNetworkConfigDataSource
    private lateinit var repo: ServerConfigRepositoryImpl

    private val validConfig = ServerConfig(
        buildTimestamp = "2024-01-15T00:00:00Z",
        remoteButtonBuildTimestamp = "2024-01-15T00:00:00Z",
        remoteButtonPushKey = "test-push-key",
    )

    @Before
    fun setup() {
        networkConfig = FakeNetworkConfigDataSource()
        repo = ServerConfigRepositoryImpl(networkConfig, "test-config-key")
    }

    @Test
    fun fetchServerConfigReturnsConfigOnSuccess() =
        runTest {
            networkConfig.serverConfig = validConfig

            val result = repo.fetchServerConfig()

            assertNotNull(result)
            assertEquals("2024-01-15T00:00:00Z", result!!.buildTimestamp)
            assertEquals("test-push-key", result.remoteButtonPushKey)
        }

    @Test
    fun fetchServerConfigReturnsNullOnFailure() =
        runTest {
            networkConfig.serverConfig = null

            assertNull(repo.fetchServerConfig())
        }

    @Test
    fun getServerConfigCachedReturnsCachedValueOnSecondCall() =
        runTest {
            networkConfig.serverConfig = validConfig

            val first = repo.getServerConfigCached()
            val second = repo.getServerConfigCached()

            assertEquals(first, second)
            assertEquals(1, networkConfig.fetchCount)
        }

    @Test
    fun getServerConfigCachedFetchesWhenNotCached() =
        runTest {
            networkConfig.serverConfig = validConfig

            val result = repo.getServerConfigCached()

            assertNotNull(result)
            assertEquals(1, networkConfig.fetchCount)
        }

    @Test
    fun getServerConfigCachedReturnsNullWhenFetchFails() =
        runTest {
            networkConfig.serverConfig = null

            assertNull(repo.getServerConfigCached())
        }
}
