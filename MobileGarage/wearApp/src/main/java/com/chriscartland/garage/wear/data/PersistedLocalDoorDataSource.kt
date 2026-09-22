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

import co.touchlab.kermit.Logger
import com.chriscartland.garage.data.LocalDoorDataSource
import com.chriscartland.garage.data.statuscache.DoorEventSnapshot
import com.chriscartland.garage.data.statuscache.DoorEventSnapshotDto
import com.chriscartland.garage.data.statuscache.StatusSnapshot
import com.chriscartland.garage.data.statuscache.StatusSnapshotStore
import com.chriscartland.garage.domain.coroutines.AppClock
import com.chriscartland.garage.domain.model.DoorEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [LocalDoorDataSource] for the Wear app: process-lifetime memory for the
 * live value, plus a one-entry snapshot on disk so the watch still knows
 * what the door was doing after the process dies.
 *
 * Replaces `InMemoryLocalDoorDataSource`. Semantics mirror the phone's
 * Room-backed source for the parts the watch uses: newest event wins for
 * `currentDoorEvent`; the recent list is kept newest-first and deduped by
 * (lastChangeTimeSeconds, doorPosition).
 *
 * **Why the watch now persists, having deliberately not before.** The
 * in-memory-only choice was right while the only reader was a screen the
 * user had just opened — a poll lands within a second or two, so the empty
 * first moment is invisible. It stops being right the moment something
 * renders the door while the app is NOT running, which is what a tile or a
 * complication is: those are asked a question by the system, in a process
 * that may have just been started for the purpose, and "I don't know yet" is
 * the wrong answer to give a glance. It also removes the "Connecting…" that
 * every single cold start of the app showed.
 *
 * **Only the CURRENT event is persisted, never the recent list.** The watch
 * has no history screen, so the list has no reader that outlives the
 * process, and it is ~10x the bytes.
 *
 * **An event we cannot DATE is not persisted at all** — see [persist]. This
 * is the load-bearing rule of the whole file and the reason hydration is
 * safe.
 */
class PersistedLocalDoorDataSource(
    private val snapshotStore: StatusSnapshotStore,
    private val clock: AppClock,
    scope: CoroutineScope,
) : LocalDoorDataSource {
    private val _currentDoorEvent = MutableStateFlow<DoorEvent?>(null)
    private val _recentDoorEvents = MutableStateFlow<List<DoorEvent>>(emptyList())

    override val currentDoorEvent: Flow<DoorEvent?> = _currentDoorEvent
    override val recentDoorEvents: Flow<List<DoorEvent>> = _recentDoorEvents

    /**
     * Serializes hydration against incoming writes.
     *
     * Hydration is a check-then-act ("publish the disk value only if nothing
     * newer arrived first") and the writer is a network callback, so both run
     * concurrently on `Dispatchers.IO`. Without the lock a fetch landing
     * mid-hydration could be overwritten by the older disk value — the one
     * outcome hydration must never produce. Same reasoning, and the same
     * remedy, as STATUS_CACHE_PLAN.md D2's "serialize the writers".
     */
    private val mutex = Mutex()

    init {
        // Hydration runs from the constructor rather than an explicit
        // `start()` deliberately: this repo has been bitten more than once by
        // app-scoped machinery whose start() call was the thing that went
        // missing, and the symptom is always silence rather than a crash (see
        // AppSettleWindow's "must be STARTED" note in CLAUDE.md). There is
        // nothing to forget here.
        //
        // Ordering against `NetworkDoorRepository`, which mirrors this source
        // with an always-on collector, does not matter: `currentDoorEvent` is
        // a StateFlow, so a collector arriving after hydration still receives
        // the hydrated value as its first emission.
        scope.launch {
            hydrate()
        }
    }

    override suspend fun insertDoorEvent(doorEvent: DoorEvent) {
        mutex.withLock {
            _currentDoorEvent.value = doorEvent
            persist(doorEvent)
        }
    }

    override suspend fun replaceDoorEvents(doorEvents: List<DoorEvent>) {
        _recentDoorEvents.value = doorEvents
        val newest = doorEvents.firstOrNull() ?: return
        mutex.withLock {
            _currentDoorEvent.value = newest
            persist(newest)
        }
    }

    override suspend fun appendDoorEvents(doorEvents: List<DoorEvent>) {
        _recentDoorEvents.value = (_recentDoorEvents.value + doorEvents)
            .distinctBy { it.lastChangeTimeSeconds to it.doorPosition }
            .sortedByDescending { it.lastChangeTimeSeconds ?: Long.MIN_VALUE }
    }

    private suspend fun hydrate() {
        val snapshot = snapshotStore.read(
            key = DoorEventSnapshot.KEY,
            schemaVersion = DoorEventSnapshot.SCHEMA_VERSION,
            payloadSerializer = DoorEventSnapshotDto.serializer(),
        ) ?: return
        mutex.withLock {
            // A real event arrived while the disk read was in flight. It is
            // newer by construction, so the snapshot is spent.
            if (_currentDoorEvent.value != null) {
                Logger.d { "PersistedLocalDoorDataSource: live event beat hydration; snapshot ignored" }
                return
            }
            val event = snapshot.payload.toDomain()
            _currentDoorEvent.value = event
            Logger.i { "currentDoorEvent <- $event (source=snapshot)" }
        }
    }

    /**
     * Writes [doorEvent] to the snapshot, unless we cannot say how old it is.
     *
     * **An undatable event is not persisted.** `lastCheckInTimeSeconds` is
     * nullable on both inbound paths (`KtorNetworkDoorDataSource` and
     * `FcmPayloadParser` each decode it from an optional wire field), and it
     * is the ONLY thing a later process can use to judge whether the stored
     * position is still worth believing. Persisting an event without it would
     * produce exactly the failure this cache exists to avoid: a stored door
     * position that reads as current forever, because the staleness rule has
     * no timestamp to compare and every freshness verdict therefore comes out
     * FRESH. Dropping the write instead costs one "Connecting…" on the next
     * cold start, which is what the watch did for every launch before this
     * class existed.
     *
     * The envelope's own `fetchedAt`/`confirmedAt` are NOT used as a fallback
     * age. They record when this device last talked to the server, which is a
     * different question from when the garage last spoke — and it is the
     * garage going quiet that the door screen has to be able to tell you
     * about.
     */
    private suspend fun persist(doorEvent: DoorEvent) {
        if (doorEvent.lastCheckInTimeSeconds == null) {
            Logger.d { "PersistedLocalDoorDataSource: event has no check-in time; not persisted" }
            return
        }
        val now = clock.nowEpochSeconds()
        snapshotStore.write(
            key = DoorEventSnapshot.KEY,
            schemaVersion = DoorEventSnapshot.SCHEMA_VERSION,
            payloadSerializer = DoorEventSnapshotDto.serializer(),
            snapshot = StatusSnapshot(
                payload = DoorEventSnapshotDto.fromDomain(doorEvent),
                fetchedAtEpochSeconds = now,
                confirmedAtEpochSeconds = now,
            ),
        )
    }
}
