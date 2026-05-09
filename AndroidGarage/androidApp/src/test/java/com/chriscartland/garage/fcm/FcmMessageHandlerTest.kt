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

package com.chriscartland.garage.fcm

import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.ButtonHealth
import com.chriscartland.garage.domain.model.ButtonHealthError
import com.chriscartland.garage.domain.model.ButtonHealthState
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.repository.ButtonHealthRepository
import com.chriscartland.garage.usecase.ApplyButtonHealthFcmUseCase
import com.chriscartland.garage.usecase.ReceiveFcmDoorEventUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FcmMessageHandlerTest {
    private val receiveFcmDoorEvent = RecordingReceiveFcmDoorEventUseCase()
    private val buttonHealthRepo = RecordingButtonHealthRepository()
    private val applyButtonHealthFcm = ApplyButtonHealthFcmUseCase(buttonHealthRepo)
    private val handler = FcmMessageHandler(receiveFcmDoorEvent, applyButtonHealthFcm)

    private fun doorPayload(
        type: String? = "CLOSED",
        message: String? = "The door is closed.",
        timestampSeconds: String? = "1000",
        checkInTimestampSeconds: String? = "1100",
    ): Map<String, String> =
        buildMap {
            type?.let { put("type", it) }
            message?.let { put("message", it) }
            timestampSeconds?.let { put("timestampSeconds", it) }
            checkInTimestampSeconds?.let { put("checkInTimestampSeconds", it) }
        }

    private fun buttonHealthPayload(
        buttonState: String = "OFFLINE",
        stateChangedAtSeconds: String = "1730000000",
        buildTimestamp: String = "Sat Apr 10 23:57:32 2021",
    ): Map<String, String> =
        mapOf(
            "buttonState" to buttonState,
            "stateChangedAtSeconds" to stateChangedAtSeconds,
            "buildTimestamp" to buildTimestamp,
        )

    @Test
    fun emptyData_returnsFalse_noUseCaseCalls() =
        runTest {
            val result = handler.handleMessage(topic = "anything", data = emptyMap())

            assertFalse(result)
            assertNull(receiveFcmDoorEvent.lastEvent)
            assertNull(buttonHealthRepo.lastApplied)
        }

    // --- Door branch (default else, preserves the existing path) ---

    @Test
    fun doorTopic_invalidPayload_returnsFalse() =
        runTest {
            val result = handler.handleMessage(topic = "door-X", data = mapOf("message" to "hi"))

            assertFalse(result)
            assertNull(receiveFcmDoorEvent.lastEvent)
        }

    @Test
    fun doorTopic_validPayload_forwardsParsedEvent() =
        runTest {
            val result = handler.handleMessage(topic = "door_open-X", data = doorPayload())

            assertTrue(result)
            val event = receiveFcmDoorEvent.lastEvent
            assertEquals(DoorPosition.CLOSED, event?.doorPosition)
            assertEquals("The door is closed.", event?.message)
            assertEquals(1000L, event?.lastChangeTimeSeconds)
            assertEquals(1100L, event?.lastCheckInTimeSeconds)
        }

    @Test
    fun emptyTopic_routesToDoorBranch() =
        runTest {
            // Defensive: a missing topic falls through to the default door branch
            // (preserves existing behavior).
            val result = handler.handleMessage(topic = "", data = doorPayload())

            assertTrue(result)
            assertEquals(1, receiveFcmDoorEvent.invocationCount)
        }

    // --- Button-health branch (new) ---

    @Test
    fun buttonHealthTopic_validOfflinePayload_appliesUpdate() =
        runTest {
            val result = handler.handleMessage(
                topic = "buttonHealth-Sat.Apr.10.23.57.32.2021",
                data = buttonHealthPayload(),
            )

            assertTrue(result)
            val update = buttonHealthRepo.lastApplied
            assertEquals(ButtonHealthState.OFFLINE, update?.state)
            assertEquals(1730000000L, update?.stateChangedAtSeconds)
            assertNull(receiveFcmDoorEvent.lastEvent) // Door branch NOT invoked.
        }

    @Test
    fun buttonHealthTopic_validOnlinePayload_appliesUpdate() =
        runTest {
            val result = handler.handleMessage(
                topic = "buttonHealth-X",
                data = buttonHealthPayload(buttonState = "ONLINE"),
            )

            assertTrue(result)
            assertEquals(ButtonHealthState.ONLINE, buttonHealthRepo.lastApplied?.state)
        }

    @Test
    fun buttonHealthTopic_invalidPayload_returnsFalse_noApply() =
        runTest {
            // Missing required field — parser returns null; handler returns false.
            val result = handler.handleMessage(
                topic = "buttonHealth-X",
                data = mapOf("buttonState" to "OFFLINE"), // missing stateChangedAtSeconds
            )

            assertFalse(result)
            assertNull(buttonHealthRepo.lastApplied)
        }
}

private class RecordingReceiveFcmDoorEventUseCase : ReceiveFcmDoorEventUseCase {
    var invocationCount: Int = 0
        private set
    var lastEvent: DoorEvent? = null
        private set

    override fun invoke(event: DoorEvent) {
        invocationCount += 1
        lastEvent = event
    }
}

private class RecordingButtonHealthRepository : ButtonHealthRepository {
    var lastApplied: ButtonHealth? = null
        private set
    override val buttonHealth: StateFlow<LoadingResult<ButtonHealth>> =
        MutableStateFlow(LoadingResult.Loading(null))

    override suspend fun fetchButtonHealth() = AppResult.Error(ButtonHealthError.Network())

    override fun applyFcmUpdate(update: ButtonHealth) {
        lastApplied = update
    }
}
