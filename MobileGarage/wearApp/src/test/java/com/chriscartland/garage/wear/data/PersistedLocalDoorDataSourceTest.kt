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

package com.chriscartland.garage.wear.data

import com.chriscartland.garage.data.statuscache.DoorEventSnapshot
import com.chriscartland.garage.data.statuscache.DoorEventSnapshotDto
import com.chriscartland.garage.data.statuscache.StatusSnapshot
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.testcommon.FakeClock
import com.chriscartland.garage.testcommon.FakeStatusSnapshotStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersistedLocalDoorDataSourceTest {
    private val store = FakeStatusSnapshotStore()
    private val clock = FakeClock(nowSeconds = NOW)

    private fun seedSnapshot(
        position: DoorPosition = DoorPosition.OPEN,
        checkInSeconds: Long? = NOW - 30,
        writtenAt: Long = NOW - 30,
    ) {
        store.seed(
            key = DoorEventSnapshot.KEY,
            schemaVersion = DoorEventSnapshot.SCHEMA_VERSION,
            snapshot = StatusSnapshot(
                payload = DoorEventSnapshotDto(
                    doorPosition = position.name,
                    lastCheckInTimeSeconds = checkInSeconds,
                    lastChangeTimeSeconds = writtenAt,
                ),
                fetchedAtEpochSeconds = writtenAt,
                confirmedAtEpochSeconds = writtenAt,
            ),
        )
    }

    @Test
    fun theLastKnownDoorIsAvailableFromDiskBeforeAnythingIsFetched() =
        runTest {
            // The whole point. Before this class the watch started every process
            // knowing nothing, which is invisible on a screen the user just
            // opened and is the entire problem for a surface the system renders
            // while the app is not running.
            seedSnapshot(position = DoorPosition.OPEN)

            val source = PersistedLocalDoorDataSource(store, clock, backgroundScope)
            runCurrent()

            assertEquals(DoorPosition.OPEN, source.currentDoorEvent.first()?.doorPosition)
        }

    @Test
    fun anEmptyCacheStillStartsAtNull() =
        runTest {
            // First ever launch, or a cleared cache. Must not invent a door.
            val source = PersistedLocalDoorDataSource(store, clock, backgroundScope)
            runCurrent()

            assertNull(source.currentDoorEvent.first())
        }

    @Test
    fun anUnreadableSnapshotIsTreatedAsAnEmptyCache() =
        runTest {
            // `StatusSnapshotStore.read` returns null for every failure it can
            // have (corrupt envelope, schema mismatch, IO), so the only correct
            // behaviour here is the same as never having had one.
            seedSnapshot()
            store.failNextRead()

            val source = PersistedLocalDoorDataSource(store, clock, backgroundScope)
            runCurrent()

            assertNull(source.currentDoorEvent.first())
        }

    @Test
    fun aLiveEventThatLandsMidHydrationIsNotOverwrittenByTheDiskValue() =
        runTest {
            // The race this class must not lose. Hydration is a check-then-act
            // and the writer is a network callback, so on Dispatchers.IO they
            // genuinely interleave. Losing it would replace a just-fetched door
            // with a stored one that is older by construction — a wrong reading
            // on screen, arriving after the right one.
            seedSnapshot(position = DoorPosition.OPEN)
            val gate = CompletableDeferred<Unit>()
            store.setReadGate(gate)

            val source = PersistedLocalDoorDataSource(store, clock, backgroundScope)
            runCurrent()

            // A fetch lands while the disk read is parked.
            source.insertDoorEvent(DoorEvent(doorPosition = DoorPosition.CLOSED, lastCheckInTimeSeconds = NOW))
            assertEquals(DoorPosition.CLOSED, source.currentDoorEvent.first()?.doorPosition)

            // Now let hydration finish. It must find the live value and yield.
            gate.complete(Unit)
            runCurrent()

            assertEquals(
                "hydration overwrote a newer live event",
                DoorPosition.CLOSED,
                source.currentDoorEvent.first()?.doorPosition,
            )
        }

    @Test
    fun anInsertedEventIsPersisted() =
        runTest {
            val source = PersistedLocalDoorDataSource(store, clock, backgroundScope)
            runCurrent()

            source.insertDoorEvent(DoorEvent(doorPosition = DoorPosition.CLOSED, lastCheckInTimeSeconds = NOW))

            assertTrue(store.contains(DoorEventSnapshot.KEY))
        }

    @Test
    fun anEventWeCannotDateIsNotPersisted() =
        runTest {
            // The load-bearing rule of the file. `lastCheckInTimeSeconds` is
            // nullable on both inbound paths, and it is the ONLY thing a later
            // process can use to judge the stored position. Persisting without it
            // would produce a door that reads as current forever, because every
            // staleness rule would have nothing to compare against — precisely
            // the failure the cache exists to avoid.
            val source = PersistedLocalDoorDataSource(store, clock, backgroundScope)
            runCurrent()

            source.insertDoorEvent(DoorEvent(doorPosition = DoorPosition.OPEN, lastCheckInTimeSeconds = null))

            assertTrue(
                "an undatable event reached the snapshot",
                !store.contains(DoorEventSnapshot.KEY),
            )
            // It is still the live value — the in-memory path is unaffected, and
            // the screen showing it is about to be refreshed anyway.
            assertEquals(DoorPosition.OPEN, source.currentDoorEvent.first()?.doorPosition)
        }

    @Test
    fun anUndatableEventDoesNotEraseAnOlderDatableSnapshot() =
        runTest {
            // The refusal above must be a skipped WRITE, not a clear. A watch
            // that received one undatable event should still open onto the last
            // door it could actually vouch for.
            seedSnapshot(position = DoorPosition.OPEN, checkInSeconds = NOW - 30)
            val source = PersistedLocalDoorDataSource(store, clock, backgroundScope)
            runCurrent()

            source.insertDoorEvent(DoorEvent(doorPosition = DoorPosition.CLOSING, lastCheckInTimeSeconds = null))
            runCurrent()

            val persisted = requireNotNull(
                store.read(
                    key = DoorEventSnapshot.KEY,
                    schemaVersion = DoorEventSnapshot.SCHEMA_VERSION,
                    payloadSerializer = DoorEventSnapshotDto.serializer(),
                ),
            )
            assertEquals(DoorPosition.OPEN.name, persisted.payload.doorPosition)
        }

    @Test
    fun replacingTheEventListPersistsTheNewestEvent() =
        runTest {
            // `replaceDoorEvents` is the other way a door event arrives (the
            // recent-events fetch). It sets the current event, so it must
            // persist on the same terms as an insert — otherwise which code path
            // happened to run decides whether the watch remembers.
            val source = PersistedLocalDoorDataSource(store, clock, backgroundScope)
            runCurrent()

            source.replaceDoorEvents(
                listOf(
                    DoorEvent(doorPosition = DoorPosition.OPEN, lastCheckInTimeSeconds = NOW, lastChangeTimeSeconds = NOW),
                    DoorEvent(
                        doorPosition = DoorPosition.CLOSED,
                        lastCheckInTimeSeconds = NOW - 600,
                        lastChangeTimeSeconds = NOW - 600,
                    ),
                ),
            )

            val persisted = requireNotNull(
                store.read(
                    key = DoorEventSnapshot.KEY,
                    schemaVersion = DoorEventSnapshot.SCHEMA_VERSION,
                    payloadSerializer = DoorEventSnapshotDto.serializer(),
                ),
            )
            assertEquals(DoorPosition.OPEN.name, persisted.payload.doorPosition)
        }

    @Test
    fun anEmptyReplaceDoesNotClearTheKnownDoor() =
        runTest {
            // A recent-events response can be empty (the phone's history paging
            // returns a windowed list). That is an absence of history, not news
            // that the door is unknown.
            seedSnapshot(position = DoorPosition.OPEN)
            val source = PersistedLocalDoorDataSource(store, clock, backgroundScope)
            runCurrent()

            source.replaceDoorEvents(emptyList())

            assertEquals(DoorPosition.OPEN, source.currentDoorEvent.first()?.doorPosition)
        }

    @Test
    fun theSnapshotIsStampedWithTheCurrentTime() =
        runTest {
            // The envelope timestamps are not used as the reading's age (the
            // event's own check-in time is), but they must still be honest: a
            // wrong `fetchedAt` would defeat the shared clock-skew guard.
            val source = PersistedLocalDoorDataSource(store, clock, backgroundScope)
            runCurrent()
            clock.advanceSeconds(100)

            source.insertDoorEvent(DoorEvent(doorPosition = DoorPosition.OPEN, lastCheckInTimeSeconds = NOW))

            val persisted = requireNotNull(
                store.read(
                    key = DoorEventSnapshot.KEY,
                    schemaVersion = DoorEventSnapshot.SCHEMA_VERSION,
                    payloadSerializer = DoorEventSnapshotDto.serializer(),
                ),
            )
            assertEquals(NOW + 100, persisted.fetchedAtEpochSeconds)
        }

    @Test
    fun theRecentEventListIsNeverPersisted() =
        runTest {
            // Deliberate: no reader on the watch outlives the process, and it is
            // an order of magnitude more bytes. `appendDoorEvents` is the
            // history-paging path and must not write at all.
            val source = PersistedLocalDoorDataSource(store, clock, backgroundScope)
            runCurrent()

            source.appendDoorEvents(
                listOf(DoorEvent(doorPosition = DoorPosition.OPEN, lastCheckInTimeSeconds = NOW, lastChangeTimeSeconds = NOW)),
            )

            assertEquals(0, store.writeCount)
        }

    @Test
    fun theRecentListStillDedupesAndSortsNewestFirst() =
        runTest {
            // Unchanged from the in-memory source this replaces; pinned so the
            // rewrite cannot have quietly dropped it.
            val source = PersistedLocalDoorDataSource(store, clock, backgroundScope)
            runCurrent()

            val older = DoorEvent(doorPosition = DoorPosition.CLOSED, lastChangeTimeSeconds = 100)
            val newer = DoorEvent(doorPosition = DoorPosition.OPEN, lastChangeTimeSeconds = 200)
            source.appendDoorEvents(listOf(older, newer, older))

            val events = source.recentDoorEvents.first()
            assertEquals(listOf(newer, older), events)
        }

    private companion object {
        const val NOW = 1_700_000_000L
    }
}
