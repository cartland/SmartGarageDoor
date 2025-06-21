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

import com.chriscartland.garage.door.DoorPosition
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class GarageNetworkServiceTest {
    @Test
    fun verifyTestData_currentEventDataResponse() = runTest {
        val response = test_CurrentEventDataResponse()
        assertNotNull("Test data should exist", response.currentEventData)
        response.currentEventData?.run {
            assertNotNull("Current event should exist", currentEvent)
            currentEvent?.asDoorEvent()?.run {
                assertEquals("Door should be closed", DoorPosition.CLOSED, doorPosition)
                assertEquals("Message should match", "The door is closed.", message)
                assertEquals("Last check-in time should match", 1730329727L, lastCheckInTimeSeconds)
                assertEquals("Last change time should match", 1730239045L, lastChangeTimeSeconds)
            }
        }
    }

    @Test
    fun verifyTestData_recentEventDataResponse() = runTest {
        val response = test_RecentEventDataResponse()
        assertNotNull("Test data should exist", response.eventHistory)
        assertEquals("Event count should match", 30, response.eventHistoryCount)
        assertEquals("Number of events should match", 30, response.eventHistory?.size)
        response.eventHistory?.run {
            assertNotNull("Last element should exist", lastOrNull())
            lastOrNull()?.run {
                assertNotNull("Last event should exist", currentEvent)
                currentEvent?.asDoorEvent()?.run {
                    assertEquals("Door should be closed", DoorPosition.OPENING, doorPosition)
                    assertEquals("Message should match", "The door is opening.", message)
                    assertEquals(
                        "Last check-in time should match",
                        1729997503L,
                        lastCheckInTimeSeconds,
                    )
                    assertEquals(
                        "Last change time should match",
                        1729997503L,
                        lastChangeTimeSeconds,
                    )
                }
            }
        }
    }

    @Test
    fun verifyTestData_serverConfigResponse() = runTest {
        val response = test_ServerConfigResponse()
        assertNotNull("Test data should exist", response.body)
        response.body?.run {
            assertEquals("Config buildTimestamp should match", "Sat Mar 20 14:25:00 2024", buildTimestamp)
            assertEquals("Config button timestamp should match", "Sat Apr 17 23:57:32 2024", remoteButtonBuildTimestamp)
            assertEquals("Config button push key should match", "key", remoteButtonPushKey)
            assertEquals("Config button authorized emails should match", listOf("demo@example.com"), remoteButtonAuthorizedEmails)
        }
    }
}

fun readJsonResource(filename: String): String {
    val jsonFilePath = "src/test/resources/$filename"
    val jsonFile = File(jsonFilePath)
    return jsonFile.readText()
}

fun moshi(): Moshi? = Moshi
    .Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

fun test_CurrentEventDataResponse(): CurrentEventDataResponse {
    val filename = "currentEventData_1730329839.json"
    val jsonString = readJsonResource(filename)
    assertNotNull("Test file should load correctly", jsonString)
    val adapter: JsonAdapter<CurrentEventDataResponse>? = moshi()?.adapter(CurrentEventDataResponse::class.java)
    val result = adapter?.fromJson(jsonString)
    assertNotNull("Test file should parse correctly", result)
    return result!!
}

fun test_RecentEventDataResponse(): RecentEventDataResponse {
    val filename = "eventHistory_1730329839.json"
    val jsonString = readJsonResource(filename)
    assertNotNull("Test file should load correctly", jsonString)
    val adapter: JsonAdapter<RecentEventDataResponse>? = moshi()?.adapter(RecentEventDataResponse::class.java)
    val result = adapter?.fromJson(jsonString)
    assertNotNull("Test file should parse correctly", result)
    return result!!
}

fun test_ServerConfigResponse(): ServerConfigResponse {
    val filename = "serverConfig_1730329839.json"
    val jsonString = readJsonResource(filename)
    assertNotNull("Test file should load correctly", jsonString)
    val adapter: JsonAdapter<ServerConfigResponse>? = moshi()?.adapter(ServerConfigResponse::class.java)
    val result = adapter?.fromJson(jsonString)
    assertNotNull("Test file should parse correctly", result)
    return result!!
}
