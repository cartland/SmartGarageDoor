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
import com.chriscartland.garage.presentation.GlanceStatus
import com.chriscartland.garage.presentation.GlanceWarning
import com.chriscartland.garage.presentation.Liveness
import com.chriscartland.garage.presentation.StatusHeadline
import com.chriscartland.garage.testcommon.FakeDoorRepository
import com.chriscartland.garage.testcommon.InMemoryLocalDoorDataSource
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
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
 *
 * The second scar is a session that outlives its first frame: Glance does not
 * restart one that is still alive, so a reader that answers once freezes the
 * widget on the first thing it saw. The `observe` tests are that.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetGlanceStatusTest {
    private var now = 1_000_000L

    private val repaints = WidgetRepaintRequests()

    private fun readerFor(
        local: InMemoryLocalDoorDataSource,
        repo: FakeDoorRepository,
    ) = WidgetGlanceStatus(
        localDoorDataSource = local,
        fetchCurrentDoorEvent = FetchCurrentDoorEventUseCase(repo),
        clock = AppClock { now },
        fetchFailures = repo.currentDoorFetchFailures,
        repaintRequests = repaints.count,
    )

    /** Everything a live session would have been handed, in order. */
    private fun TestScope.framesOf(reader: WidgetGlanceStatus): List<GlanceStatus> {
        val frames = mutableListOf<GlanceStatus>()
        backgroundScope.launch { reader.observe().collect { frames += it } }
        runCurrent()
        return frames
    }

    private fun door(
        position: DoorPosition,
        changedAgo: Long = 600,
        checkedInAgo: Long = 30,
    ) = DoorEvent(
        doorPosition = position,
        lastChangeTimeSeconds = now - changedAgo,
        lastCheckInTimeSeconds = now - checkedInAgo,
    )

    @Test
    fun aSecondChangeInsideOneSessionReachesTheWidget() =
        runTest {
            // THE scar. A door goes Opening and then Open a few seconds later:
            // two pushes, one widget session (it lives about 45 seconds and
            // Glance does not restart it). The reader used to be asked once,
            // so the home screen said "Opening" until the launcher's next
            // half-hourly update.
            val local = InMemoryLocalDoorDataSource()
            local.insertDoorEvent(door(DoorPosition.CLOSED))
            val frames = framesOf(readerFor(local, FakeDoorRepository()))

            local.insertDoorEvent(door(DoorPosition.OPENING, changedAgo = 0))
            runCurrent()
            local.insertDoorEvent(door(DoorPosition.OPEN, changedAgo = 0))
            runCurrent()

            assertEquals(
                listOf(
                    StatusHeadline.Door(DoorHeadline.CLOSED),
                    StatusHeadline.Door(DoorHeadline.OPENING),
                    StatusHeadline.Door(DoorHeadline.OPEN),
                ),
                frames.map { it.headline },
            )
        }

    @Test
    fun theFirstObservedFrameIsTheOneCurrentAlreadyGave() =
        runTest {
            // The session composes current() and then collects observe(). If
            // the two could differ, every render would start with a correction.
            val local = InMemoryLocalDoorDataSource()
            local.insertDoorEvent(door(DoorPosition.OPEN))
            val reader = readerFor(local, FakeDoorRepository())

            val first = reader.current()
            val frames = framesOf(reader)

            assertEquals(listOf(first), frames)
        }

    @Test
    fun aRefreshThatFailsReachesALiveSession() =
        runTest {
            // The session no longer re-reads after its refresh; it has to HEAR
            // the outcome. Without the flag being observed, a widget that could
            // not reach the server would go on presenting the door as current.
            val local = InMemoryLocalDoorDataSource()
            local.insertDoorEvent(door(DoorPosition.OPEN))
            val repo = FakeDoorRepository().apply { setFailCurrentDoorEventFetch(true) }
            val reader = readerFor(local, repo)
            val frames = framesOf(reader)

            reader.refresh()
            runCurrent()

            assertEquals(listOf(Liveness.LIVE, Liveness.STALE), frames.map { it.liveness })
        }

    @Test
    fun aFailureSomeoneElseFoundIsWhatTheWidgetSays() =
        runTest {
            // The app's own fetch could not get through. The widget has not
            // asked anything, and must still say "Not confirmed": the process
            // knows, and the widget reads the process's memory, not its own.
            val local = InMemoryLocalDoorDataSource()
            local.insertDoorEvent(door(DoorPosition.OPEN))
            val repo = FakeDoorRepository().apply { setFailCurrentDoorEventFetch(true) }
            val reader = readerFor(local, repo)
            val frames = framesOf(reader)

            repo.fetchCurrentDoorEvent()
            runCurrent()

            assertEquals(listOf(Liveness.LIVE, Liveness.STALE), frames.map { it.liveness })
            assertEquals(Liveness.STALE, reader.current().liveness)
        }

    @Test
    fun whenAnyoneGetsThroughTheWidgetIsConfidentAgain() =
        runTest {
            // Positive control for the test above, and the other half of the
            // rule: the doubt lasts exactly until the next thing the server
            // tells us, by request or by push.
            val local = InMemoryLocalDoorDataSource()
            local.insertDoorEvent(door(DoorPosition.OPEN))
            val repo = FakeDoorRepository().apply { setFailCurrentDoorEventFetch(true) }
            val reader = readerFor(local, repo)
            repo.fetchCurrentDoorEvent()
            val frames = framesOf(reader)
            assertEquals(listOf(Liveness.STALE), frames.map { it.liveness })

            repo.insertDoorEvent(door(DoorPosition.OPEN))
            runCurrent()

            assertEquals(listOf(Liveness.STALE, Liveness.LIVE), frames.map { it.liveness })
        }

    @Test
    fun aRepaintRequestJudgesTheSameDoorAgainstThePresent() =
        runTest {
            // The garage goes quiet. Nothing on disk changes — the app notices
            // only because time passed — so the request is the only way a live
            // session can find out.
            val local = InMemoryLocalDoorDataSource()
            local.insertDoorEvent(door(DoorPosition.CLOSED, checkedInAgo = 30))
            val frames = framesOf(readerFor(local, FakeDoorRepository()))

            now += 4_000
            runCurrent()
            assertEquals(
                "time passing alone tells a session nothing — that is the gap the request closes",
                listOf(Liveness.LIVE),
                frames.map { it.liveness },
            )

            repaints.request()
            runCurrent()

            assertEquals(listOf(Liveness.LIVE, Liveness.STALE), frames.map { it.liveness })
        }

    @Test
    fun aRepaintRequestThatChangesNothingSendsNothing() =
        runTest {
            // Glance re-sends the whole widget for every value it is handed,
            // and the app asks on every arrival and departure. Most of those
            // requests find the verdict exactly as it was.
            val local = InMemoryLocalDoorDataSource()
            local.insertDoorEvent(door(DoorPosition.CLOSED))
            val frames = framesOf(readerFor(local, FakeDoorRepository()))

            repaints.request()
            repaints.request()
            runCurrent()

            assertEquals(1, frames.size)
        }

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
