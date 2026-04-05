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

package com.chriscartland.garage.internet

import com.chriscartland.garage.domain.model.DoorPosition
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/**
 * Contract tests verifying that real server JSON responses parse correctly.
 *
 * These use test fixture JSON files captured from the production server.
 * If the server response format changes, these tests catch the mismatch
 * before a broken app is shipped.
 */
class GarageNetworkServiceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun verifyTestData_currentEventDataResponse() =
        runTest {
            val response = parseJsonResource<TestCurrentEventDataResponse>("currentEventData_1730329839.json")
            assertNotNull("Test data should exist", response.currentEventData)
            response.currentEventData?.run {
                assertNotNull("Current event should exist", currentEvent)
                currentEvent?.run {
                    assertEquals("Door should be closed", "CLOSED", type)
                    assertEquals("Message should match", "The door is closed.", message)
                    assertEquals("Last check-in time should match", 1730329727L, checkInTimestampSeconds)
                    assertEquals("Last change time should match", 1730239045L, timestampSeconds)
                }
            }
        }

    @Test
    fun verifyTestData_currentEventMapsToCorrectDoorPosition() =
        runTest {
            val response = parseJsonResource<TestCurrentEventDataResponse>("currentEventData_1730329839.json")
            val type = response.currentEventData?.currentEvent?.type
            assertEquals(DoorPosition.CLOSED, type.toDoorPosition())
        }

    @Test
    fun verifyTestData_recentEventDataResponse() =
        runTest {
            val response = parseJsonResource<TestRecentEventDataResponse>("eventHistory_1730329839.json")
            assertNotNull("Test data should exist", response.eventHistory)
            assertEquals("Event count should match", 30, response.eventHistoryCount)
            assertEquals("Number of events should match", 30, response.eventHistory?.size)
            response.eventHistory?.run {
                assertNotNull("Last element should exist", lastOrNull())
                lastOrNull()?.run {
                    assertNotNull("Last event should exist", currentEvent)
                    currentEvent?.run {
                        assertEquals("Door should be opening", "OPENING", type)
                        assertEquals("Message should match", "The door is opening.", message)
                        assertEquals(
                            "Last check-in time should match",
                            1729997503L,
                            checkInTimestampSeconds,
                        )
                        assertEquals(
                            "Last change time should match",
                            1729997503L,
                            timestampSeconds,
                        )
                    }
                }
            }
        }

    @Test
    fun verifyTestData_serverConfigResponse() =
        runTest {
            val response = parseJsonResource<TestServerConfigResponse>("serverConfig_1730329839.json")
            assertNotNull("Test data should exist", response.body)
            response.body?.run {
                assertEquals("Config buildTimestamp should match", "Sat Mar 20 14:25:00 2024", buildTimestamp)
                assertEquals(
                    "Config button timestamp should match (raw, URL-encoded)",
                    "Sat%20Apr%2017%2023:57:32%202024",
                    remoteButtonBuildTimestamp,
                )
                assertEquals("Config button push key should match", "key", remoteButtonPushKey)
                assertEquals("Config button authorized emails should match", listOf("demo@example.com"), remoteButtonAuthorizedEmails)
            }
        }

    @Test
    fun allDoorPositionStringsParsable() {
        val knownTypes = listOf(
            "CLOSED",
            "OPENING",
            "OPENING_TOO_LONG",
            "CLOSING",
            "CLOSING_TOO_LONG",
            "OPEN",
            "OPEN_MISALIGNED",
            "ERROR_SENSOR_CONFLICT",
        )
        for (type in knownTypes) {
            val position = type.toDoorPosition()
            assertNotNull("$type should map to a DoorPosition", position)
            assertEquals("$type should not map to UNKNOWN", false, position == DoorPosition.UNKNOWN)
        }
    }

    private inline fun <reified T> parseJsonResource(filename: String): T {
        val file = File("src/test/resources/$filename")
        return json.decodeFromString(file.readText())
    }
}

// region Test-only @Serializable types (contract verification)

@Serializable
private data class TestCurrentEventDataResponse(
    val currentEventData: TestEventData? = null,
)

@Serializable
private data class TestRecentEventDataResponse(
    val eventHistory: List<TestEventData>? = null,
    val eventHistoryCount: Int? = null,
)

@Serializable
private data class TestEventData(
    val currentEvent: TestEvent? = null,
)

@Serializable
private data class TestEvent(
    val type: String? = null,
    val message: String? = null,
    @SerialName("timestampSeconds") val timestampSeconds: Long? = null,
    @SerialName("checkInTimestampSeconds") val checkInTimestampSeconds: Long? = null,
)

@Serializable
private data class TestServerConfigResponse(
    val body: TestServerConfigBody? = null,
)

@Serializable
private data class TestServerConfigBody(
    val buildTimestamp: String? = null,
    @SerialName("remoteButtonBuildTimestamp") val remoteButtonBuildTimestamp: String? = null,
    val remoteButtonPushKey: String? = null,
    val remoteButtonAuthorizedEmails: List<String>? = null,
)

// Uses the same mapping as KtorNetworkDoorDataSource — kept in sync
private fun String?.toDoorPosition(): DoorPosition =
    when (this) {
        "CLOSED" -> DoorPosition.CLOSED
        "OPENING" -> DoorPosition.OPENING
        "OPENING_TOO_LONG" -> DoorPosition.OPENING_TOO_LONG
        "CLOSING" -> DoorPosition.CLOSING
        "CLOSING_TOO_LONG" -> DoorPosition.CLOSING_TOO_LONG
        "OPEN" -> DoorPosition.OPEN
        "OPEN_MISALIGNED" -> DoorPosition.OPEN_MISALIGNED
        "ERROR_SENSOR_CONFLICT" -> DoorPosition.ERROR_SENSOR_CONFLICT
        else -> DoorPosition.UNKNOWN
    }

// endregion
