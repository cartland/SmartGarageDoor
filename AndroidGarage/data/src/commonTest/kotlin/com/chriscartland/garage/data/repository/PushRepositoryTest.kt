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
import com.chriscartland.garage.domain.model.SnoozeRequestStatus
import com.chriscartland.garage.testcommon.FakeNetworkButtonDataSource
import com.chriscartland.garage.testcommon.FakeNetworkConfigDataSource
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
        )
    }

    @Test
    fun initialSnoozeRequestStatusIsIdle() {
        assertEquals(SnoozeRequestStatus.IDLE, repo.snoozeRequestStatus.value)
    }

    @Test
    fun initialSnoozeEndTimeIsZero() {
        assertEquals(0L, repo.snoozeEndTimeSeconds.value)
    }

    @Test
    fun snoozeResetsToIdleWhenServerConfigFetchFails() =
        runTest {
            networkConfigDataSource.serverConfigResult = NetworkResult.ConnectionFailed
            repo.snoozeNotifications(
                snoozeDurationHours = "1h",
                idToken = "token",
                snoozeEventTimestampSeconds = 1000L,
            )
            assertEquals(SnoozeRequestStatus.IDLE, repo.snoozeRequestStatus.value)
        }
}
