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

package com.chriscartland.garage.widget

import com.chriscartland.garage.domain.coroutines.AppClock
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.presentation.DoorHeadline
import com.chriscartland.garage.presentation.GlanceWarning
import com.chriscartland.garage.presentation.Liveness
import com.chriscartland.garage.presentation.StatusHeadline
import com.chriscartland.garage.testcommon.FakeDoorRepository
import com.chriscartland.garage.testcommon.InMemoryLocalDoorDataSource
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the widget reads, and how honest it is about it.
 *
 * The scar these tests protect is a cold process: the system renders a widget
 * while the app is not running, so the door has to come off disk, and the
 * verdict has to admit when the network could not confirm it.
 */
class WidgetGlanceStatusTest {
    private val now = 1_000_000L

    private fun readerFor(
        local: InMemoryLocalDoorDataSource,
        repo: FakeDoorRepository,
    ) = WidgetGlanceStatus(
        localDoorDataSource = local,
        fetchCurrentDoorEvent = FetchCurrentDoorEventUseCase(repo),
        clock = AppClock { now },
    )

    @Test
    fun aDoorAlreadyOnDiskIsReadWithoutTheNetwork() =
        runTest {
            // The cold-process case. Nothing has fetched; the stored door must
            // still be what the widget renders, or a widget on a phone with no
            // signal shows "No signal" about a door it knows.
            val local = InMemoryLocalDoorDataSource()
            local.insertDoorEvent(
                DoorEvent(
                    doorPosition = DoorPosition.CLOSED,
                    lastChangeTimeSeconds = now - 600,
                    lastCheckInTimeSeconds = now - 30,
                ),
            )
            val repo = FakeDoorRepository()

            val status = readerFor(local, repo).current()

            assertEquals(StatusHeadline.Door(DoorHeadline.CLOSED), status.headline)
            assertEquals(Liveness.LIVE, status.liveness)
            assertEquals(0, repo.fetchCurrentDoorEventCount)
        }

    @Test
    fun aDoorWeCouldNotConfirmIsReportedStaleAndLosesItsSince() =
        runTest {
            // A refresh that fails must change what the NEXT read says. Without
            // this the widget would present a remembered door as current for as
            // long as the launcher left it alone.
            val local = InMemoryLocalDoorDataSource()
            local.insertDoorEvent(
                DoorEvent(
                    doorPosition = DoorPosition.OPEN,
                    lastChangeTimeSeconds = now - 600,
                    lastCheckInTimeSeconds = now - 30,
                ),
            )
            val repo = FakeDoorRepository().apply { setFailCurrentDoorEventFetch(true) }
            val reader = readerFor(local, repo)

            val before = reader.current()
            assertEquals(Liveness.LIVE, before.liveness)
            assertNotNull("a confirmed door should carry its since instant", before.stateSinceEpochSeconds)

            reader.refresh()
            val after = reader.current()

            assertEquals(Liveness.STALE, after.liveness)
            assertNull(
                "a door we cannot vouch for must not claim a continuous span — it may " +
                    "have moved twice since we last heard",
                after.stateSinceEpochSeconds,
            )
        }

    @Test
    fun aSuccessfulRefreshLeavesTheReadingLive() =
        runTest {
            // The other half of the previous test: without this, a reader that
            // reported STALE unconditionally would pass it.
            val local = InMemoryLocalDoorDataSource()
            local.insertDoorEvent(
                DoorEvent(
                    doorPosition = DoorPosition.CLOSED,
                    lastChangeTimeSeconds = now - 600,
                    lastCheckInTimeSeconds = now - 30,
                ),
            )
            val repo = FakeDoorRepository().apply {
                setCurrentDoorEvent(
                    DoorEvent(
                        doorPosition = DoorPosition.CLOSED,
                        lastChangeTimeSeconds = now - 600,
                        lastCheckInTimeSeconds = now - 5,
                    ),
                )
            }
            val reader = readerFor(local, repo)

            reader.refresh()
            val status = reader.current()

            assertEquals(Liveness.LIVE, status.liveness)
            assertEquals(1, repo.fetchCurrentDoorEventCount)
        }

    @Test
    fun anEmptyCacheSaysSoRatherThanInventingADoor() =
        runTest {
            val local = InMemoryLocalDoorDataSource()
            val repo = FakeDoorRepository()

            val status = readerFor(local, repo).current()

            assertEquals(
                "nothing known must escalate to a spoken headline on a glance surface, " +
                    "which never settles",
                StatusHeadline.NoSignal,
                status.headline,
            )
            assertNull(status.stateSinceEpochSeconds)
        }

    @Test
    fun aGarageThatHasGoneQuietIsStaleEvenWithoutAFailedFetch() =
        runTest {
            // Staleness has two independent causes and only one of them is a
            // failed request. A door whose own check-in has aged out is not
            // current no matter how well the network is working.
            val local = InMemoryLocalDoorDataSource()
            local.insertDoorEvent(
                DoorEvent(
                    doorPosition = DoorPosition.CLOSED,
                    lastChangeTimeSeconds = now - 10_000,
                    lastCheckInTimeSeconds = now - 4_000,
                ),
            )
            val repo = FakeDoorRepository()

            val status = readerFor(local, repo).current()

            assertEquals(Liveness.STALE, status.liveness)
        }

    @Test
    fun aStuckDoorReachesTheWidgetAsAWarning() =
        runTest {
            // The finding this whole change exists for: a door stuck opening
            // used to read as a plain "Opening" here.
            val local = InMemoryLocalDoorDataSource()
            local.insertDoorEvent(
                DoorEvent(
                    doorPosition = DoorPosition.OPENING_TOO_LONG,
                    lastChangeTimeSeconds = now - 1_200,
                    lastCheckInTimeSeconds = now - 30,
                ),
            )

            val status = readerFor(local, FakeDoorRepository()).current()

            assertEquals(GlanceWarning.STUCK, status.warning)
        }
}
