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

package com.chriscartland.garage.wear.tile

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.presentation.CheckInStatus
import com.chriscartland.garage.presentation.CheckInStatusMapper
import com.chriscartland.garage.presentation.DataFreshness
import com.chriscartland.garage.presentation.DoorHeadline
import com.chriscartland.garage.presentation.StatusHeadline
import com.chriscartland.garage.testcommon.FakeClock
import com.chriscartland.garage.testcommon.FakeDoorRepository
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import com.chriscartland.garage.usecase.ObserveDoorEventsUseCase
import com.chriscartland.garage.wear.data.DoorSnapshotHydration
import com.chriscartland.garage.wear.glance.WearGlanceStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tile's decisions, which is all of them that are not drawing.
 *
 * These run on the JVM because `WearTilePresenter` holds no platform types —
 * that separation is the point of it existing at all (see its KDoc). What is
 * NOT covered here is the ProtoLayout rendering, which needs a `Context`; the
 * emulator screenshot stages are the gate for that.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearTilePresenterTest {
    private val doorRepository = FakeDoorRepository()
    private val clock = FakeClock(nowSeconds = NOW)

    /** Hydration already finished — the ordinary case. */
    private val hydrated = DoorSnapshotHydration { }

    private fun glance(hydration: DoorSnapshotHydration = hydrated) =
        WearGlanceStatus(
            observeDoorEvents = ObserveDoorEventsUseCase(doorRepository),
            fetchCurrentDoorEvent = FetchCurrentDoorEventUseCase(doorRepository),
            hydration = hydration,
            clock = clock,
        )

    private fun presenter(hydration: DoorSnapshotHydration = hydrated) = WearTilePresenter(glance(hydration))

    @Test
    fun aRecentlyConfirmedDoorIsShownConfidently() =
        runTest {
            doorRepository.setCurrentDoorEvent(
                DoorEvent(doorPosition = DoorPosition.OPEN, lastCheckInTimeSeconds = NOW - 30),
            )

            val status = presenter().status()

            assertEquals(StatusHeadline.Door(DoorHeadline.OPEN), status.headline)
            assertEquals(DataFreshness.FRESH, status.freshness)
        }

    @Test
    fun aDoorFromBeforeTheGarageWentQuietIsMutedButStillNamed() =
        runTest {
            // The whole reason a tile is allowed to show a cached value: it says
            // what it knows AND that it cannot vouch for it. Replacing "Open"
            // with "No signal" here would discard the one fact worth glancing
            // for, at the moment it matters most.
            doorRepository.setCurrentDoorEvent(
                DoorEvent(
                    doorPosition = DoorPosition.OPEN,
                    lastCheckInTimeSeconds = NOW - CheckInStatusMapper.STALE_THRESHOLD_SECONDS - 1,
                ),
            )

            val status = presenter().status()

            assertEquals(StatusHeadline.Door(DoorHeadline.OPEN), status.headline)
            assertTrue("a reading we cannot vouch for must be muted", status.freshness.isMuted)
            assertTrue("and it must say so", status.freshness.isSpoken)
        }

    @Test
    fun aTileWithNothingCachedSaysNoSignalRatherThanConnecting() =
        runTest {
            // The settle window belongs to a screen that is arriving. A tile is
            // asked once, and whatever it returns is what gets read and swiped
            // away from.
            doorRepository.clearCurrentDoorEvent()

            val status = presenter().status()

            assertEquals(StatusHeadline.NoSignal, status.headline)
        }

    @Test
    fun theAgeOfTheReadingIsAvailableToRender() =
        runTest {
            doorRepository.setCurrentDoorEvent(
                DoorEvent(doorPosition = DoorPosition.CLOSED, lastCheckInTimeSeconds = NOW - 3_600),
            )

            val age = presenter().status().age

            assertTrue("a tile must be able to say how old its reading is", age is CheckInStatus.Reported)
        }

    @Test
    fun aFirstRenderNeverAsksForAnother() =
        runTest {
            // Nothing was on screen to correct, so spending a re-render would be
            // a wasted wake — and, since every render fires a refresh, the start
            // of a loop.
            doorRepository.setCurrentDoorEvent(
                DoorEvent(doorPosition = DoorPosition.CLOSED, lastCheckInTimeSeconds = NOW - 30),
            )
            val presenter = presenter()
            presenter.status()

            assertFalse(presenter.refreshAndReportChange())
        }

    @Test
    fun aDoorThatMovedAsksForAReRender() =
        runTest {
            // The case the re-render exists for: the tile drew a cached CLOSED,
            // the refresh found OPEN, and what is on the wrist is now wrong.
            doorRepository.setCurrentDoorEvent(
                DoorEvent(doorPosition = DoorPosition.CLOSED, lastCheckInTimeSeconds = NOW - 30),
            )
            val presenter = presenter()
            presenter.status()

            doorRepository.setCurrentDoorEvent(
                DoorEvent(doorPosition = DoorPosition.OPEN, lastCheckInTimeSeconds = NOW - 5),
            )

            assertTrue(presenter.refreshAndReportChange())
        }

    @Test
    fun theReRenderRequestTerminatesInsteadOfLooping() =
        runTest {
            // The loop guard, exercised as a sequence rather than asserted about.
            // Every render fires a refresh, so a presenter that answered "changed"
            // unconditionally would render -> refresh -> render forever. After the
            // tile has re-rendered the new value, the next refresh must settle.
            doorRepository.setCurrentDoorEvent(
                DoorEvent(doorPosition = DoorPosition.CLOSED, lastCheckInTimeSeconds = NOW - 30),
            )
            val presenter = presenter()
            presenter.status()
            doorRepository.setCurrentDoorEvent(
                DoorEvent(doorPosition = DoorPosition.OPEN, lastCheckInTimeSeconds = NOW - 5),
            )
            assertTrue("the first refresh should notice the door moved", presenter.refreshAndReportChange())

            // The system honours the request and re-renders.
            presenter.status()

            assertFalse(
                "a second refresh with nothing new must not ask for another render",
                presenter.refreshAndReportChange(),
            )
        }

    @Test
    fun aFailedRefreshMutesTheNextRenderWithoutDemandingOne() =
        runTest {
            // A watch out of range must not redraw its tile on every failure —
            // but the next render it is asked for has to be honest about it.
            doorRepository.setCurrentDoorEvent(
                DoorEvent(doorPosition = DoorPosition.CLOSED, lastCheckInTimeSeconds = NOW - 30),
            )
            val presenter = presenter()
            assertEquals(DataFreshness.FRESH, presenter.status().freshness)

            doorRepository.setFailCurrentDoorEventFetch(true)
            val askedForRender = presenter.refreshAndReportChange()

            assertFalse("a failure alone must not spend a re-render", askedForRender)
            assertEquals(
                "but the value on screen is now remembered, not confirmed",
                DataFreshness.STALE,
                presenter.status().freshness,
            )
        }

    @Test
    fun aRecoveredRefreshStopsMutingAgain() =
        runTest {
            // Positive control for the test above: without it, a `lastRefreshFailed`
            // that latched true would satisfy it and the tile would stay grey
            // forever once the watch had been out of range once.
            doorRepository.setCurrentDoorEvent(
                DoorEvent(doorPosition = DoorPosition.CLOSED, lastCheckInTimeSeconds = NOW - 30),
            )
            val presenter = presenter()
            doorRepository.setFailCurrentDoorEventFetch(true)
            presenter.refreshAndReportChange()
            assertEquals(DataFreshness.STALE, presenter.status().freshness)

            doorRepository.setFailCurrentDoorEventFetch(false)
            presenter.refreshAndReportChange()

            assertEquals(DataFreshness.FRESH, presenter.status().freshness)
        }

    @Test
    fun aRenderThatArrivesBeforeTheDiskReadStillShowsTheStoredDoor() =
        runTest {
            // The cold-process race, which is the normal case for a tile: the
            // system may start the process purely to answer this request, so
            // the disk read can still be in flight. Answering "No signal"
            // about a door we have on disk would be a wrong reading, shown
            // once, on a surface that gets one chance to be right.
            val gate = CompletableDeferred<Unit>()
            val presenter = presenter(hydration = { gate.await() })
            doorRepository.clearCurrentDoorEvent()

            val render = async { presenter.status() }
            runCurrent()
            assertFalse("status() must not answer before hydration finishes", render.isCompleted)

            // Hydration lands, populating the cache the way the real one does.
            doorRepository.setCurrentDoorEvent(
                DoorEvent(doorPosition = DoorPosition.OPEN, lastCheckInTimeSeconds = NOW - 30),
            )
            gate.complete(Unit)

            assertEquals(StatusHeadline.Door(DoorHeadline.OPEN), render.await().headline)
        }

    private companion object {
        const val NOW = 1_700_000_000L
    }
}
