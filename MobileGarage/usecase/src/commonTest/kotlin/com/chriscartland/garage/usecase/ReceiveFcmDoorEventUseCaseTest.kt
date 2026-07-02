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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReceiveFcmDoorEventUseCaseTest {
    @Test
    fun invoke_insertsEventIntoRepository() =
        runTest {
            val doorRepo = FakeDoorRepository()
            val loggerRepo = FakeAppLoggerRepository()
            val useCase = DefaultReceiveFcmDoorEventUseCase(
                doorRepository = doorRepo,
                appLoggerRepository = loggerRepo,
                externalScope = backgroundScope,
            )
            val event = DoorEvent(
                doorPosition = DoorPosition.OPEN,
                message = "Door opened",
                lastChangeTimeSeconds = 1000L,
                lastCheckInTimeSeconds = 1100L,
            )

            useCase(event)
            runCurrent()

            assertEquals(event, doorRepo.currentDoorEvent.value)
        }

    @Test
    fun invoke_logsFcmDoorReceived() =
        runTest {
            val doorRepo = FakeDoorRepository()
            val loggerRepo = FakeAppLoggerRepository()
            val useCase = DefaultReceiveFcmDoorEventUseCase(
                doorRepository = doorRepo,
                appLoggerRepository = loggerRepo,
                externalScope = backgroundScope,
            )

            useCase(DoorEvent(doorPosition = DoorPosition.CLOSED))
            runCurrent()

            assertTrue(
                loggerRepo.loggedKeys.contains(AppLoggerKeys.FCM_DOOR_RECEIVED),
                "Expected FCM_DOOR_RECEIVED to be logged after invoke()",
            )
        }

    @Test
    fun invoke_runsOnExternalScope_notCallerScope() =
        runTest {
            // The caller (FCMService) cancels its scope in onDestroy. The use case
            // must dispatch the insert onto externalScope so the work survives
            // (ADR-019). This test enforces that contract: invoke() returns
            // synchronously without performing the insert on the caller's stack.
            val doorRepo = FakeDoorRepository()
            val loggerRepo = FakeAppLoggerRepository()
            val useCase = DefaultReceiveFcmDoorEventUseCase(
                doorRepository = doorRepo,
                appLoggerRepository = loggerRepo,
                externalScope = backgroundScope,
            )
            val event = DoorEvent(doorPosition = DoorPosition.OPEN, lastChangeTimeSeconds = 42L)

            useCase(event)
            // Without runCurrent(), the launched coroutine has not executed yet.
            // The repo should still hold its default (FakeDoorRepository's seed).
            assertEquals(DoorEvent(), doorRepo.currentDoorEvent.value)

            runCurrent()
            // After draining the dispatcher, the work is done.
            assertEquals(event, doorRepo.currentDoorEvent.value)
        }
}
