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

package com.chriscartland.garage.door

import com.chriscartland.garage.config.ServerConfigRepository
import com.chriscartland.garage.config.model.ServerConfig
import com.chriscartland.garage.data.LocalDoorDataSource
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.internet.CurrentEventDataResponse
import com.chriscartland.garage.internet.GarageNetworkService
import com.chriscartland.garage.internet.RecentEventDataResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import retrofit2.Response

/**
 * Fake LocalDoorDataSource that records insertions for assertion.
 */
class FakeLocalDoorDataSource : LocalDoorDataSource {
    val insertedEvents = mutableListOf<DoorEvent>()
    val replacedEvents = mutableListOf<List<DoorEvent>>()

    private val _currentDoorEvent = MutableStateFlow(DoorEvent())
    override val currentDoorEvent: Flow<DoorEvent> = _currentDoorEvent
    override val recentDoorEvents: Flow<List<DoorEvent>> = MutableStateFlow(emptyList())

    override fun insertDoorEvent(doorEvent: DoorEvent) {
        insertedEvents.add(doorEvent)
        _currentDoorEvent.value = doorEvent
    }

    override fun replaceDoorEvents(doorEvents: List<DoorEvent>) {
        replacedEvents.add(doorEvents)
    }
}

class DoorRepositoryTest {
    private lateinit var localDataSource: FakeLocalDoorDataSource
    private lateinit var network: GarageNetworkService
    private lateinit var serverConfigRepository: ServerConfigRepository
    private lateinit var repo: DoorRepositoryImpl

    private val validServerConfig =
        ServerConfig(
            buildTimestamp = "2024-01-15T00:00:00Z",
            remoteButtonBuildTimestamp = "2024-01-15T00:00:00Z",
            remoteButtonPushKey = "key",
        )

    private val validEvent =
        CurrentEventDataResponse.Event(
            type = "CLOSED",
            message = "The door is closed.",
            timestampSeconds = 1000L,
            checkInTimestampSeconds = 1100L,
        )

    private val validCurrentResponse =
        CurrentEventDataResponse(
            currentEventData =
                CurrentEventDataResponse.EventData(
                    currentEvent = validEvent,
                    previousEvent = null,
                    firestoreDatabaseTimestamp = null,
                    firestoreDatabaseTimestampSeconds = null,
                ),
            queryParams = null,
            session = null,
            buildTimestamp = null,
        )

    @Before
    fun setup() {
        localDataSource = FakeLocalDoorDataSource()
        network = mock(GarageNetworkService::class.java)
        serverConfigRepository = mock(ServerConfigRepository::class.java)
        repo = DoorRepositoryImpl(localDataSource, network, serverConfigRepository)
    }

    // --- fetchCurrentDoorEvent ---

    @Test
    fun fetchCurrentDoorEventInsertsOnValidResponse() =
        runTest {
            `when`(serverConfigRepository.getServerConfigCached()).thenReturn(validServerConfig)
            `when`(network.getCurrentEventData(anyString(), isNull()))
                .thenReturn(Response.success(validCurrentResponse))

            repo.fetchCurrentDoorEvent()

            assertEquals(1, localDataSource.insertedEvents.size)
            val inserted = localDataSource.insertedEvents[0]
            assertEquals(DoorPosition.CLOSED, inserted.doorPosition)
            assertEquals("The door is closed.", inserted.message)
            assertEquals(1000L, inserted.lastChangeTimeSeconds)
            assertEquals(1100L, inserted.lastCheckInTimeSeconds)
        }

    @Test
    fun fetchCurrentDoorEventSkipsWhenServerConfigIsNull() =
        runTest {
            `when`(serverConfigRepository.getServerConfigCached()).thenReturn(null)

            repo.fetchCurrentDoorEvent()

            assertTrue("Should not insert when config is null", localDataSource.insertedEvents.isEmpty())
        }

    @Test
    fun fetchCurrentDoorEventSkipsOnNon200Response() =
        runTest {
            `when`(serverConfigRepository.getServerConfigCached()).thenReturn(validServerConfig)
            `when`(network.getCurrentEventData(anyString(), isNull()))
                .thenReturn(Response.error(500, "error".toResponseBody()))

            repo.fetchCurrentDoorEvent()

            assertTrue("Should not insert on 500", localDataSource.insertedEvents.isEmpty())
        }

    @Test
    fun fetchCurrentDoorEventSkipsWhenBodyIsNull() =
        runTest {
            `when`(serverConfigRepository.getServerConfigCached()).thenReturn(validServerConfig)
            `when`(network.getCurrentEventData(anyString(), isNull()))
                .thenReturn(Response.success(null))

            repo.fetchCurrentDoorEvent()

            assertTrue("Should not insert on null body", localDataSource.insertedEvents.isEmpty())
        }

    @Test
    fun fetchCurrentDoorEventSkipsWhenCurrentEventDataIsNull() =
        runTest {
            `when`(serverConfigRepository.getServerConfigCached()).thenReturn(validServerConfig)
            val response = validCurrentResponse.copy(currentEventData = null)
            `when`(network.getCurrentEventData(anyString(), isNull()))
                .thenReturn(Response.success(response))

            repo.fetchCurrentDoorEvent()

            assertTrue("Should not insert on null eventData", localDataSource.insertedEvents.isEmpty())
        }

    @Test
    fun fetchCurrentDoorEventHandlesNetworkException() =
        runTest {
            `when`(serverConfigRepository.getServerConfigCached()).thenReturn(validServerConfig)
            `when`(network.getCurrentEventData(anyString(), isNull()))
                .thenThrow(RuntimeException("timeout"))

            repo.fetchCurrentDoorEvent()

            assertTrue("Should not insert on exception", localDataSource.insertedEvents.isEmpty())
        }

    @Test
    fun fetchCurrentDoorEventMapsDoorPositionCorrectly() =
        runTest {
            `when`(serverConfigRepository.getServerConfigCached()).thenReturn(validServerConfig)
            val event = validEvent.copy(type = "OPENING_TOO_LONG")
            val response =
                validCurrentResponse.copy(
                    currentEventData =
                        CurrentEventDataResponse.EventData(
                            currentEvent = event,
                            previousEvent = null,
                            firestoreDatabaseTimestamp = null,
                            firestoreDatabaseTimestampSeconds = null,
                        ),
                )
            `when`(network.getCurrentEventData(anyString(), isNull()))
                .thenReturn(Response.success(response))

            repo.fetchCurrentDoorEvent()

            assertEquals(DoorPosition.OPENING_TOO_LONG, localDataSource.insertedEvents[0].doorPosition)
        }

    // --- fetchRecentDoorEvents ---

    @Test
    fun fetchRecentDoorEventsReplacesOnValidResponse() =
        runTest {
            `when`(serverConfigRepository.getServerConfigCached()).thenReturn(validServerConfig)
            val eventData =
                RecentEventDataResponse.EventData(
                    currentEvent =
                        RecentEventDataResponse.Event(
                            type = "OPEN",
                            message = "The door is open.",
                            timestampSeconds = 2000L,
                            checkInTimestampSeconds = 2100L,
                        ),
                    previousEvent = null,
                    firestoreDatabaseTimestamp = null,
                    firestoreDatabaseTimestampSeconds = null,
                )
            val response =
                RecentEventDataResponse(
                    eventHistory = listOf(eventData),
                    eventHistoryCount = 1,
                    queryParams = null,
                    session = null,
                    buildTimestamp = null,
                )
            `when`(network.getRecentEventData(anyString(), isNull(), anyInt()))
                .thenReturn(Response.success(response))

            repo.fetchRecentDoorEvents()

            assertEquals(1, localDataSource.replacedEvents.size)
            val replaced = localDataSource.replacedEvents[0]
            assertEquals(1, replaced.size)
            assertEquals(DoorPosition.OPEN, replaced[0].doorPosition)
        }

    @Test
    fun fetchRecentDoorEventsSkipsWhenServerConfigIsNull() =
        runTest {
            `when`(serverConfigRepository.getServerConfigCached()).thenReturn(null)

            repo.fetchRecentDoorEvents()

            assertTrue("Should not replace when config is null", localDataSource.replacedEvents.isEmpty())
        }

    @Test
    fun fetchRecentDoorEventsSkipsOnEmptyHistory() =
        runTest {
            `when`(serverConfigRepository.getServerConfigCached()).thenReturn(validServerConfig)
            val response =
                RecentEventDataResponse(
                    eventHistory = emptyList(),
                    eventHistoryCount = 0,
                    queryParams = null,
                    session = null,
                    buildTimestamp = null,
                )
            `when`(network.getRecentEventData(anyString(), isNull(), anyInt()))
                .thenReturn(Response.success(response))

            repo.fetchRecentDoorEvents()

            assertTrue("Should not replace on empty history", localDataSource.replacedEvents.isEmpty())
        }
}
