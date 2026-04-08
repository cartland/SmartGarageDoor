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

package com.chriscartland.garage.remotebutton

import com.chriscartland.garage.data.repository.NetworkRemoteButtonRepository
import com.chriscartland.garage.data.repository.NetworkSnoozeRepository
import com.chriscartland.garage.domain.model.PushStatus
import com.chriscartland.garage.domain.model.SnoozeRequestStatus
import com.chriscartland.garage.testcommon.FakeNetworkButtonDataSource
import com.chriscartland.garage.testcommon.FakeServerConfigRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RemoteButtonRepositoryTest {
    private lateinit var networkButtonDataSource: FakeNetworkButtonDataSource
    private lateinit var serverConfigRepository: FakeServerConfigRepository
    private lateinit var repo: NetworkRemoteButtonRepository

    @Before
    fun setup() {
        networkButtonDataSource = FakeNetworkButtonDataSource()
        serverConfigRepository = FakeServerConfigRepository()
        repo = NetworkRemoteButtonRepository(
            networkButtonDataSource,
            serverConfigRepository,
            remoteButtonPushEnabled = true,
        )
    }

    @Test
    fun initialPushStatusIsIdle() {
        assertEquals(PushStatus.IDLE, repo.pushButtonStatus.value)
    }

    @Test
    fun pushResetsToIdleWhenServerConfigIsNull() =
        runTest {
            serverConfigRepository.serverConfig = null
            repo.pushButton("token", "ack-token")
            assertEquals(PushStatus.IDLE, repo.pushButtonStatus.value)
        }
}

class SnoozeRepositoryTest {
    private lateinit var networkButtonDataSource: FakeNetworkButtonDataSource
    private lateinit var serverConfigRepository: FakeServerConfigRepository
    private lateinit var repo: NetworkSnoozeRepository

    @Before
    fun setup() {
        networkButtonDataSource = FakeNetworkButtonDataSource()
        serverConfigRepository = FakeServerConfigRepository()
        repo = NetworkSnoozeRepository(
            networkButtonDataSource,
            serverConfigRepository,
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
    fun snoozeResetsToIdleWhenServerConfigIsNull() =
        runTest {
            serverConfigRepository.serverConfig = null
            repo.snoozeNotifications(
                snoozeDurationHours = "1h",
                idToken = "token",
                snoozeEventTimestampSeconds = 1000L,
            )
            assertEquals(SnoozeRequestStatus.IDLE, repo.snoozeRequestStatus.value)
        }
}
