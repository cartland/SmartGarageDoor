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

import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeDiagnosticsCountersRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InitialDoorFetchManagerTest {
    private fun createManager(
        scope: kotlinx.coroutines.test.TestScope,
        doorRepo: FakeDoorRepository,
    ): InitialDoorFetchManager {
        val logger = FakeAppLoggerRepository()
        val counters = FakeDiagnosticsCountersRepository()
        val dispatcher = UnconfinedTestDispatcher(scope.testScheduler)
        return InitialDoorFetchManager(
            fetchCurrentDoorEvent = FetchCurrentDoorEventUseCase(doorRepo),
            fetchRecentDoorEvents = FetchRecentDoorEventsUseCase(doorRepo),
            logAppEvent = LogAppEventUseCase(logger, counters),
            scope = scope.backgroundScope,
            dispatcher = dispatcher,
        )
    }

    @Test
    fun startFetchesCurrentAndRecentOnce() =
        runTest {
            val doorRepo = FakeDoorRepository()
            val manager = createManager(this, doorRepo)

            manager.start()

            assertEquals(1, doorRepo.fetchCurrentDoorEventCount)
            assertEquals(1, doorRepo.fetchRecentDoorEventsCount)
        }

    @Test
    fun startIsIdempotent() =
        runTest {
            val doorRepo = FakeDoorRepository()
            val manager = createManager(this, doorRepo)

            manager.start()
            manager.start()
            manager.start()

            // Three start() calls but only ONE pair of fetches —
            // matches the per-process semantics that motivate the
            // singleton scope (rotation, app resume after Activity
            // destroy, etc.).
            assertEquals(1, doorRepo.fetchCurrentDoorEventCount)
            assertEquals(1, doorRepo.fetchRecentDoorEventsCount)
        }
}
