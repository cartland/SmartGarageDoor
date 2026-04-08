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
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.FetchError
import com.chriscartland.garage.domain.model.ServerConfig
import com.chriscartland.garage.testcommon.FakeNetworkConfigDataSource
import com.chriscartland.garage.testcommon.FakeNetworkDoorDataSource
import com.chriscartland.garage.testcommon.InMemoryLocalDoorDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Integration tests wiring real [NetworkDoorRepository] with fake data sources.
 * Validates the fetch → cache → expose flow without network or database.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkDoorRepositoryIntegrationTest {
    private val localDataSource = InMemoryLocalDoorDataSource()
    private val networkDataSource = FakeNetworkDoorDataSource()
    private val configDataSource = FakeNetworkConfigDataSource()

    private fun createRepository(): NetworkDoorRepository {
        val serverConfigRepo = CachedServerConfigRepository(
            networkConfigDataSource = configDataSource,
            serverConfigKey = "test-key",
        )
        return NetworkDoorRepository(
            localDoorDataSource = localDataSource,
            networkDoorDataSource = networkDataSource,
            serverConfigRepository = serverConfigRepo,
            recentEventCount = 10,
        )
    }

    // --- fetchCurrentDoorEvent ---

    @Test
    fun fetchCurrentDoorEventStoresInLocal() =
        runTest {
            configDataSource.serverConfigResult = NetworkResult.Success(
                ServerConfig(
                    buildTimestamp = "2024-01-15T00:00:00Z",
                    remoteButtonBuildTimestamp = "2024-01-15T00:00:00Z",
                    remoteButtonPushKey = "key",
                ),
            )
            val event = DoorEvent(
                doorPosition = DoorPosition.OPEN,
                message = "Door is open",
                lastCheckInTimeSeconds = 1000L,
                lastChangeTimeSeconds = 900L,
            )
            networkDataSource.currentDoorEventResult = NetworkResult.Success(event)

            val repo = createRepository()
            val result = repo.fetchCurrentDoorEvent()

            assertIs<AppResult.Success<*>>(result)
            assertEquals(event, result.data)
            assertEquals(event, localDataSource.currentDoorEvent.first())
            assertEquals(1, localDataSource.insertCount)
            assertEquals(1, networkDataSource.fetchCurrentCount)
        }

    @Test
    fun fetchCurrentDoorEventExposedViaFlow() =
        runTest {
            configDataSource.serverConfigResult = NetworkResult.Success(
                ServerConfig(
                    buildTimestamp = "2024-01-15T00:00:00Z",
                    remoteButtonBuildTimestamp = "2024-01-15T00:00:00Z",
                    remoteButtonPushKey = "key",
                ),
            )
            val event = DoorEvent(
                doorPosition = DoorPosition.CLOSED,
                message = "Door is closed",
            )
            networkDataSource.currentDoorEventResult = NetworkResult.Success(event)

            val repo = createRepository()
            repo.fetchCurrentDoorEvent()

            assertEquals(event, repo.currentDoorEvent.first())
            assertEquals(DoorPosition.CLOSED, repo.currentDoorPosition.first())
        }

    @Test
    fun fetchCurrentDoorEventWithNullServerConfigReturnsNotReady() =
        runTest {
            configDataSource.serverConfigResult = NetworkResult.ConnectionFailed

            val repo = createRepository()
            val result = repo.fetchCurrentDoorEvent()

            assertIs<AppResult.Error<*>>(result)
            assertEquals(FetchError.NotReady, result.error)
            assertEquals(0, networkDataSource.fetchCurrentCount)
            assertEquals(0, localDataSource.insertCount)
        }

    @Test
    fun fetchCurrentDoorEventWithNullNetworkResponseReturnsNetworkFailed() =
        runTest {
            configDataSource.serverConfigResult = NetworkResult.Success(
                ServerConfig(
                    buildTimestamp = "2024-01-15T00:00:00Z",
                    remoteButtonBuildTimestamp = "2024-01-15T00:00:00Z",
                    remoteButtonPushKey = "key",
                ),
            )
            networkDataSource.currentDoorEventResult = NetworkResult.ConnectionFailed

            val repo = createRepository()
            val result = repo.fetchCurrentDoorEvent()

            assertIs<AppResult.Error<*>>(result)
            assertEquals(FetchError.NetworkFailed, result.error)
            assertEquals(1, networkDataSource.fetchCurrentCount)
            assertEquals(0, localDataSource.insertCount)
            assertNull(repo.currentDoorEvent.first())
        }

    // --- fetchRecentDoorEvents ---

    @Test
    fun fetchRecentDoorEventsStoresInLocal() =
        runTest {
            configDataSource.serverConfigResult = NetworkResult.Success(
                ServerConfig(
                    buildTimestamp = "2024-01-15T00:00:00Z",
                    remoteButtonBuildTimestamp = "2024-01-15T00:00:00Z",
                    remoteButtonPushKey = "key",
                ),
            )
            val events = listOf(
                DoorEvent(doorPosition = DoorPosition.OPEN, lastChangeTimeSeconds = 300L),
                DoorEvent(doorPosition = DoorPosition.CLOSED, lastChangeTimeSeconds = 200L),
                DoorEvent(doorPosition = DoorPosition.OPENING, lastChangeTimeSeconds = 100L),
            )
            networkDataSource.recentDoorEventsResult = NetworkResult.Success(events)

            val repo = createRepository()
            val result = repo.fetchRecentDoorEvents()

            assertIs<AppResult.Success<*>>(result)
            assertEquals(events, repo.recentDoorEvents.first())
            assertEquals(1, localDataSource.replaceCount)
        }

    @Test
    fun fetchRecentDoorEventsWithNullServerConfigReturnsNotReady() =
        runTest {
            configDataSource.serverConfigResult = NetworkResult.ConnectionFailed

            val repo = createRepository()
            val result = repo.fetchRecentDoorEvents()

            assertIs<AppResult.Error<*>>(result)
            assertEquals(FetchError.NotReady, result.error)
            assertEquals(0, networkDataSource.fetchRecentCount)
            assertEquals(0, localDataSource.replaceCount)
        }

    @Test
    fun fetchRecentDoorEventsWithNullNetworkResponseReturnsNetworkFailed() =
        runTest {
            configDataSource.serverConfigResult = NetworkResult.Success(
                ServerConfig(
                    buildTimestamp = "2024-01-15T00:00:00Z",
                    remoteButtonBuildTimestamp = "2024-01-15T00:00:00Z",
                    remoteButtonPushKey = "key",
                ),
            )
            networkDataSource.recentDoorEventsResult = NetworkResult.ConnectionFailed

            val repo = createRepository()
            val result = repo.fetchRecentDoorEvents()

            assertIs<AppResult.Error<*>>(result)
            assertEquals(FetchError.NetworkFailed, result.error)
            assertEquals(1, networkDataSource.fetchRecentCount)
            assertEquals(0, localDataSource.replaceCount)
        }

    // --- insertDoorEvent ---

    @Test
    fun insertDoorEventUpdatesLocalAndPosition() =
        runTest {
            val event = DoorEvent(
                doorPosition = DoorPosition.OPENING,
                message = "Door opening",
            )

            val repo = createRepository()
            repo.insertDoorEvent(event)

            assertEquals(event, repo.currentDoorEvent.first())
            assertEquals(DoorPosition.OPENING, repo.currentDoorPosition.first())
        }

    // --- currentDoorPosition ---

    @Test
    fun currentDoorPositionDefaultsToUnknown() =
        runTest {
            val repo = createRepository()
            assertEquals(DoorPosition.UNKNOWN, repo.currentDoorPosition.first())
        }

    @Test
    fun currentDoorPositionTracksInsertedEvents() =
        runTest {
            val repo = createRepository()

            repo.insertDoorEvent(DoorEvent(doorPosition = DoorPosition.CLOSED))
            assertEquals(DoorPosition.CLOSED, repo.currentDoorPosition.first())

            repo.insertDoorEvent(DoorEvent(doorPosition = DoorPosition.OPENING))
            assertEquals(DoorPosition.OPENING, repo.currentDoorPosition.first())
        }

    // --- fetchBuildTimestampCached ---

    @Test
    fun fetchBuildTimestampCachedReturnsFromServerConfig() =
        runTest {
            configDataSource.serverConfigResult = NetworkResult.Success(
                ServerConfig(
                    buildTimestamp = "2024-01-15T00:00:00Z",
                    remoteButtonBuildTimestamp = "2024-01-15T00:00:00Z",
                    remoteButtonPushKey = "key",
                ),
            )

            val repo = createRepository()
            assertEquals("2024-01-15T00:00:00Z", repo.fetchBuildTimestampCached())
        }

    @Test
    fun fetchBuildTimestampCachedReturnsNullWhenNoConfig() =
        runTest {
            configDataSource.serverConfigResult = NetworkResult.ConnectionFailed

            val repo = createRepository()
            assertNull(repo.fetchBuildTimestampCached())
        }
}
