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
import kotlinx.coroutines.CompletableDeferred
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
            SystemSurfaceRefresher(listOf(door), { refreshes++ }, backgroundScope).start()
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
            SystemSurfaceRefresher(listOf(door), { refreshes++ }, backgroundScope).start()
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
            SystemSurfaceRefresher(listOf(door), { refreshes++ }, backgroundScope).start()
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
            SystemSurfaceRefresher(listOf(door), { refreshes++ }, backgroundScope).start()
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
            val refresher = SystemSurfaceRefresher(listOf(door), { refreshes++ }, backgroundScope)
            refresher.start()
            refresher.start()
            runCurrent()

            door.value = event(DoorPosition.OPEN, 100)
            runCurrent()

            assertEquals(1, refreshes, "two collectors would redraw twice per change")
        }

    @Test
    fun arrivingAndLeavingEachAskForARedraw() =
        runTest {
            // Opening the app is expected to bring its widgets up to date, and
            // leaving it is the moment the launcher or the watch face is back
            // on screen — the one place a contradiction would be SEEN.
            val door = MutableStateFlow<DoorEvent?>(event(DoorPosition.CLOSED, 50))
            val visibility = AppVisibilityState()
            var refreshes = 0
            SystemSurfaceRefresher(listOf(door, visibility.visibility), { refreshes++ }, backgroundScope).start()
            runCurrent()
            assertEquals(0, refreshes, "neither flow's starting value is news")

            visibility.setVisible(true)
            runCurrent()
            assertEquals(1, refreshes, "arriving")

            visibility.setVisible(false)
            runCurrent()
            assertEquals(2, refreshes, "leaving")
        }

    @Test
    fun aVerdictWithNoEventBehindItIsStillNews() =
        runTest {
            // Staleness is produced by the clock: the garage goes quiet, no
            // event arrives, and the app greys its own screen. An event-only
            // trigger left the widget confident about that same garage.
            val door = MutableStateFlow<DoorEvent?>(event(DoorPosition.CLOSED, 50))
            val isStale = MutableStateFlow(false)
            var refreshes = 0
            SystemSurfaceRefresher(listOf(door, isStale), { refreshes++ }, backgroundScope).start()
            runCurrent()

            isStale.value = true
            runCurrent()

            assertEquals(1, refreshes)
        }

    @Test
    fun everyFlowSkipsItsOwnStartingValueAndNoOtherFlows() =
        runTest {
            // The skip is per flow. A single skip across the merged stream
            // would swallow the first real change from whichever flow spoke
            // second — here, the door moving after the app had already been
            // opened once.
            val door = MutableStateFlow<DoorEvent?>(null)
            val isStale = MutableStateFlow(false)
            var refreshes = 0
            SystemSurfaceRefresher(listOf(isStale, door), { refreshes++ }, backgroundScope).start()
            runCurrent()

            isStale.value = true
            runCurrent()
            door.value = event(DoorPosition.OPEN, 100)
            runCurrent()

            assertEquals(2, refreshes)
        }

    @Test
    fun changesThatLandDuringARefreshCollapseIntoOneMore() =
        runTest {
            // A push usually moves the door event AND the staleness verdict.
            // The surfaces need telling once more, not once per input.
            val door = MutableStateFlow<DoorEvent?>(null)
            val isStale = MutableStateFlow(false)
            val gate = CompletableDeferred<Unit>()
            var refreshes = 0
            SystemSurfaceRefresher(
                changes = listOf(door, isStale),
                refresh = {
                    refreshes++
                    // Only the first refresh is slow; the rest return at once.
                    if (refreshes == 1) gate.await()
                },
                scope = backgroundScope,
            ).start()
            runCurrent()

            door.value = event(DoorPosition.OPEN, 100)
            runCurrent()
            assertEquals(1, refreshes, "the first refresh is now in flight")

            door.value = event(DoorPosition.CLOSED, 200)
            isStale.value = true
            door.value = event(DoorPosition.OPEN, 300)
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            assertEquals(2, refreshes, "three changes during one refresh are one more refresh, not three")
        }

    @Test
    fun aChangeAfterAQuietRefreshIsNotCollapsedAway() =
        runTest {
            // Positive control for the collapse above: conflation must only
            // merge changes that overlap a running refresh. A refresher that
            // stopped after two would pass the previous test too.
            val door = MutableStateFlow<DoorEvent?>(null)
            var refreshes = 0
            SystemSurfaceRefresher(listOf(door), { refreshes++ }, backgroundScope).start()
            runCurrent()

            door.value = event(DoorPosition.OPEN, 100)
            runCurrent()
            door.value = event(DoorPosition.CLOSED, 200)
            runCurrent()
            door.value = event(DoorPosition.OPEN, 300)
            runCurrent()

            assertEquals(3, refreshes)
        }
}
