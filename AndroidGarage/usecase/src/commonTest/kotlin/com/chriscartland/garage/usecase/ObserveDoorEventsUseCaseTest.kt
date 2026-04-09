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

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.testcommon.FakeDoorRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveDoorEventsUseCaseTest {
    @Test
    fun currentReflectsRepositoryUpdates() =
        runTest {
            val repo = FakeDoorRepository()
            val useCase = ObserveDoorEventsUseCase(repo)

            val event = DoorEvent(doorPosition = DoorPosition.OPEN, lastChangeTimeSeconds = 1000L)
            repo.setCurrentDoorEvent(event)

            assertEquals(event, useCase.current().first())
        }

    @Test
    fun recentReflectsRepositoryUpdates() =
        runTest {
            val repo = FakeDoorRepository()
            val useCase = ObserveDoorEventsUseCase(repo)

            val events = listOf(
                DoorEvent(doorPosition = DoorPosition.CLOSED, lastChangeTimeSeconds = 100L),
                DoorEvent(doorPosition = DoorPosition.OPEN, lastChangeTimeSeconds = 200L),
            )
            repo.setRecentDoorEvents(events)

            assertEquals(events, useCase.recent().first())
        }
}
