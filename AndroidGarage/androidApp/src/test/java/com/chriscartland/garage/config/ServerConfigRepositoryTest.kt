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

import com.chriscartland.garage.internet.GarageNetworkService
import com.chriscartland.garage.internet.ServerConfigResponse
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import retrofit2.Response

class ServerConfigRepositoryTest {
    private lateinit var network: GarageNetworkService
    private lateinit var repo: ServerConfigRepositoryImpl

    private val validBody =
        ServerConfigResponse.Body(
            buildTimestamp = "2024-01-15T00:00:00Z",
            deleteOldDataEnabledDryRun = false,
            remoteButtonEnabled = true,
            deleteOldDataEnabled = false,
            path = "addRemoteButtonCommand",
            remoteButtonPushKey = "test-push-key",
            _remoteButtonBuildTimestamp = "2024-01-15T00%3A00%3A00Z",
            host = "example.com",
            remoteButtonAuthorizedEmails = emptyList(),
        )

    private val validResponse =
        ServerConfigResponse(
            firestoreDatabaseTimestamp = null,
            firestoreDatabaseTimestampSeconds = null,
            body = validBody,
        )

    @Before
    fun setup() {
        network = mock(GarageNetworkService::class.java)
        repo = ServerConfigRepositoryImpl(network)
    }

    @Test
    fun fetchServerConfigReturnsConfigOnValidResponse() =
        runTest {
            `when`(network.getServerConfig(anyString())).thenReturn(Response.success(validResponse))

            val result = repo.fetchServerConfig()

            assertNotNull(result)
            assertEquals("2024-01-15T00:00:00Z", result!!.buildTimestamp)
            assertEquals("test-push-key", result.remoteButtonPushKey)
        }

    @Test
    fun fetchServerConfigReturnsNullOnNon200Response() =
        runTest {
            `when`(network.getServerConfig(anyString()))
                .thenReturn(Response.error(500, "error".toResponseBody()))

            assertNull(repo.fetchServerConfig())
        }

    @Test
    fun fetchServerConfigReturnsNullWhenBodyIsNull() =
        runTest {
            `when`(network.getServerConfig(anyString()))
                .thenReturn(Response.success(null))

            assertNull(repo.fetchServerConfig())
        }

    @Test
    fun fetchServerConfigReturnsNullWhenBodyBodyIsNull() =
        runTest {
            val response = ServerConfigResponse(null, null, body = null)
            `when`(network.getServerConfig(anyString())).thenReturn(Response.success(response))

            assertNull(repo.fetchServerConfig())
        }

    @Test
    fun fetchServerConfigReturnsNullWhenBuildTimestampIsEmpty() =
        runTest {
            val body = validBody.copy(buildTimestamp = "")
            val response = validResponse.copy(body = body)
            `when`(network.getServerConfig(anyString())).thenReturn(Response.success(response))

            assertNull(repo.fetchServerConfig())
        }

    @Test
    fun fetchServerConfigReturnsNullWhenRemoteButtonPushKeyIsEmpty() =
        runTest {
            val body = validBody.copy(remoteButtonPushKey = "")
            val response = validResponse.copy(body = body)
            `when`(network.getServerConfig(anyString())).thenReturn(Response.success(response))

            assertNull(repo.fetchServerConfig())
        }

    @Test
    fun fetchServerConfigReturnsNullOnException() =
        runTest {
            `when`(network.getServerConfig(anyString())).thenThrow(RuntimeException("network error"))

            assertNull(repo.fetchServerConfig())
        }

    @Test
    fun getServerConfigCachedReturnsCachedValueOnSecondCall() =
        runTest {
            `when`(network.getServerConfig(anyString())).thenReturn(Response.success(validResponse))

            val first = repo.getServerConfigCached()
            val second = repo.getServerConfigCached()

            assertEquals(first, second)
            // Network should only be called once — second call uses cache
            verify(network, times(1)).getServerConfig(anyString())
        }
}
