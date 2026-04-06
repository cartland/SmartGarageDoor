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

import com.chriscartland.garage.data.NetworkButtonDataSource
import com.chriscartland.garage.domain.model.PushStatus
import com.chriscartland.garage.domain.model.ServerConfig
import com.chriscartland.garage.domain.model.SnoozeRequestStatus
import com.chriscartland.garage.domain.repository.ServerConfigRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class PushRepositoryTest {
    private lateinit var networkButtonDataSource: NetworkButtonDataSource
    private lateinit var serverConfigRepository: ServerConfigRepository
    private lateinit var repo: PushRepositoryImpl

    private val validServerConfig =
        ServerConfig(
            buildTimestamp = "2024-01-15T00:00:00Z",
            remoteButtonBuildTimestamp = "2024-01-15T00:00:00Z",
            remoteButtonPushKey = "test-push-key",
        )

    @Before
    fun setup() {
        networkButtonDataSource = mock(NetworkButtonDataSource::class.java)
        serverConfigRepository = mock(ServerConfigRepository::class.java)
        repo = PushRepositoryImpl(networkButtonDataSource, serverConfigRepository)
    }

    @Test
    fun initialPushStatusIsIdle() {
        assertEquals(PushStatus.IDLE, repo.pushButtonStatus.value)
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
    fun pushResetsToIdleWhenServerConfigIsNull() =
        runTest {
            `when`(serverConfigRepository.getServerConfigCached()).thenReturn(null)

            repo.push("token", "ack-token")

            assertEquals(PushStatus.IDLE, repo.pushButtonStatus.value)
        }

    @Test
    fun snoozeResetsToIdleWhenServerConfigIsNull() =
        runTest {
            `when`(serverConfigRepository.getServerConfigCached()).thenReturn(null)

            repo.snoozeOpenDoorsNotifications(
                snoozeDurationHours = "1h",
                idToken = "token",
                snoozeEventTimestampSeconds = 1000L,
            )

            assertEquals(SnoozeRequestStatus.IDLE, repo.snoozeRequestStatus.value)
        }
}
