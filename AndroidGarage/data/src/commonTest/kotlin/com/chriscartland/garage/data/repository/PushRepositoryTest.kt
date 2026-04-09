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

import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.PushStatus
import com.chriscartland.garage.domain.model.ServerConfig
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.testcommon.FakeNetworkButtonDataSource
import com.chriscartland.garage.testcommon.FakeNetworkConfigDataSource
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Tests use real [CachedServerConfigRepository] with [FakeNetworkConfigDataSource],
 * exercising the actual caching and null-handling logic in the repository.
 */
class RemoteButtonRepositoryTest {
    private lateinit var networkButtonDataSource: FakeNetworkButtonDataSource
    private lateinit var networkConfigDataSource: FakeNetworkConfigDataSource
    private lateinit var repo: NetworkRemoteButtonRepository

    @BeforeTest
    fun setup() {
        networkButtonDataSource = FakeNetworkButtonDataSource()
        networkConfigDataSource = FakeNetworkConfigDataSource()
        repo = NetworkRemoteButtonRepository(
            networkButtonDataSource,
            CachedServerConfigRepository(networkConfigDataSource, "test-key"),
            remoteButtonPushEnabled = true,
        )
    }

    @Test
    fun initialPushStatusIsIdle() {
        assertEquals(PushStatus.IDLE, repo.pushButtonStatus.value)
    }

    @Test
    fun pushResetsToIdleWhenServerConfigFetchFails() =
        runTest {
            networkConfigDataSource.serverConfigResult = NetworkResult.ConnectionFailed
            repo.pushButton("token", "ack-token")
            assertEquals(PushStatus.IDLE, repo.pushButtonStatus.value)
        }

    @Test
    fun pushSendsWhenServerConfigAvailable() =
        runTest {
            networkConfigDataSource.serverConfigResult = NetworkResult.Success(
                ServerConfig(buildTimestamp = "test", remoteButtonBuildTimestamp = "test", remoteButtonPushKey = "key"),
            )
            repo.pushButton("token", "ack-token")
            assertEquals(1, networkButtonDataSource.pushCount)
        }
}

class SnoozeRepositoryTest {
    private lateinit var networkButtonDataSource: FakeNetworkButtonDataSource
    private lateinit var networkConfigDataSource: FakeNetworkConfigDataSource
    private lateinit var repo: NetworkSnoozeRepository

    @BeforeTest
    fun setup() {
        networkButtonDataSource = FakeNetworkButtonDataSource()
        networkConfigDataSource = FakeNetworkConfigDataSource()
        repo = NetworkSnoozeRepository(
            networkButtonDataSource,
            CachedServerConfigRepository(networkConfigDataSource, "test-key"),
            snoozeNotificationsOption = true,
            currentTimeSeconds = { 1000L },
        )
    }

    @Test
    fun initialSnoozeStateIsLoading() {
        assertEquals(SnoozeState.Loading, repo.snoozeState.value)
    }

    @Test
    fun snoozeDoesNotCrashWhenServerConfigFetchFails() =
        runTest {
            networkConfigDataSource.serverConfigResult = NetworkResult.ConnectionFailed
            repo.snoozeNotifications(
                snoozeDurationHours = "1h",
                idToken = "token",
                snoozeEventTimestampSeconds = 1000L,
            )
            // State remains Loading — no crash, no error propagation
            assertEquals(SnoozeState.Loading, repo.snoozeState.value)
        }

    @Test
    fun fetchSnoozeStatusTransitionsFromLoadingToNotSnoozing() =
        runTest {
            assertEquals(SnoozeState.Loading, repo.snoozeState.value)
            networkConfigDataSource.serverConfigResult = NetworkResult.Success(
                ServerConfig(buildTimestamp = "test", remoteButtonBuildTimestamp = "test", remoteButtonPushKey = "key"),
            )
            networkButtonDataSource.fetchSnoozeResult = NetworkResult.Success(0L)
            repo.fetchSnoozeStatus()
            assertEquals(SnoozeState.NotSnoozing, repo.snoozeState.value)
        }

    @Test
    fun fetchSnoozeStatusTransitionsFromLoadingEvenOnFailure() =
        runTest {
            assertEquals(SnoozeState.Loading, repo.snoozeState.value)
            networkConfigDataSource.serverConfigResult = NetworkResult.Success(
                ServerConfig(buildTimestamp = "test", remoteButtonBuildTimestamp = "test", remoteButtonPushKey = "key"),
            )
            networkButtonDataSource.fetchSnoozeResult = NetworkResult.ConnectionFailed
            repo.fetchSnoozeStatus()
            // Must not stay Loading — user would see "Loading..." forever
            assertNotEquals(SnoozeState.Loading, repo.snoozeState.value)
        }
}
