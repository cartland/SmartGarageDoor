/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SystemSurfaceRefresherTest {
    private fun event(
        position: DoorPosition,
        at: Long,
    ) = DoorEvent(doorPosition = position, lastChangeTimeSeconds = at, lastCheckInTimeSeconds = at)

    @Test
    fun aChangeAfterStartAsksTheSurfacesToRedraw() =
        runTest {
            val door = MutableStateFlow<DoorEvent?>(null)
            var refreshes = 0
            SystemSurfaceRefresher(door, { refreshes++ }, backgroundScope).start()
            runCurrent()

            door.value = event(DoorPosition.OPEN, 100)
            runCurrent()

            assertEquals(1, refreshes)
        }

    @Test
    fun theValuePresentAtStartIsNotARedraw() =
        runTest {
            // The seed or the disk hydration: whatever surface just asked to
            // render already reads that cache, so redrawing it again is waste.
            val door = MutableStateFlow<DoorEvent?>(event(DoorPosition.CLOSED, 50))
            var refreshes = 0
            SystemSurfaceRefresher(door, { refreshes++ }, backgroundScope).start()
            runCurrent()

            assertEquals(0, refreshes)
        }

    @Test
    fun theSameDoorLandingAgainIsNotAChange() =
        runTest {
            // This is what makes a re-fetching surface unable to loop: the
            // redraw fetches, the fetch lands an equal event, nothing emits.
            val door = MutableStateFlow<DoorEvent?>(null)
            var refreshes = 0
            SystemSurfaceRefresher(door, { refreshes++ }, backgroundScope).start()
            runCurrent()

            door.value = event(DoorPosition.OPEN, 100)
            runCurrent()
            door.value = event(DoorPosition.OPEN, 100)
            runCurrent()

            assertEquals(1, refreshes, "an equal event must not redraw again")
        }

    @Test
    fun aDifferentDoorIsAChange() =
        runTest {
            // Positive control for the previous test: without it, a refresher
            // that never redrew after the first change would also pass.
            val door = MutableStateFlow<DoorEvent?>(null)
            var refreshes = 0
            SystemSurfaceRefresher(door, { refreshes++ }, backgroundScope).start()
            runCurrent()

            door.value = event(DoorPosition.OPEN, 100)
            runCurrent()
            door.value = event(DoorPosition.CLOSED, 200)
            runCurrent()

            assertEquals(2, refreshes)
        }

    @Test
    fun startingTwiceStartsOneCollector() =
        runTest {
            val door = MutableStateFlow<DoorEvent?>(null)
            var refreshes = 0
            val refresher = SystemSurfaceRefresher(door, { refreshes++ }, backgroundScope)
            refresher.start()
            refresher.start()
            runCurrent()

            door.value = event(DoorPosition.OPEN, 100)
            runCurrent()

            assertEquals(1, refreshes, "two collectors would redraw twice per change")
        }
}
