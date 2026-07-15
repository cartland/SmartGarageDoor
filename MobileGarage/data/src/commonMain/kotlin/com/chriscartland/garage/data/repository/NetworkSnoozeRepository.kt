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

package com.chriscartland.garage.data.repository

import co.touchlab.kermit.Logger
import com.chriscartland.garage.data.NetworkButtonDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.data.statuscache.SnoozeSnapshot
import com.chriscartland.garage.data.statuscache.SnoozeSnapshotDto
import com.chriscartland.garage.data.statuscache.StatusSnapshot
import com.chriscartland.garage.data.statuscache.StatusSnapshotStore
import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.FetchError
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.domain.repository.ServerConfigRepository
import com.chriscartland.garage.domain.repository.SnoozeDoorEventBridge
import com.chriscartland.garage.domain.repository.SnoozeRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * All network work (fetch + submit) runs on [externalScope] so that VM-scope
 * cancellation can never strand the singleton state.
 *
 * Why: callers (ViewModels) launch these suspend calls on viewModelScope.
 * If the VM is cancelled after a network call returns but before the state
 * write, Ktor's rethrown CancellationException skips the write — the
 * singleton's StateFlow gets stuck, and every future subscriber sees stale
 * data. See ADR-018 and [FirebaseAuthRepository] for the same pattern.
 *
 * The caller still suspends via [kotlinx.coroutines.Deferred.await]/
 * [kotlinx.coroutines.Job.join]. If the caller is cancelled, their
 * `await`/`join` throws, but the launched coroutine continues on
 * [externalScope] and completes the state update independently.
 *
 * Persisted last-known snooze (STATUS_CACHE_PLAN.md D3):
 *  - `init` runs ONE sequenced coroutine: hydrate (seed the `Loading`
 *    sentinel from the persisted raw end time, recomputed against the
 *    clock so an expired snooze hydrates as NotSnoozing), complete
 *    [hydrated], then fetch ONLY if the snapshot is older than
 *    [FETCH_TTL_SECONDS] — this repo is constructed lazily on first
 *    Settings entry, and a fresh snapshot makes that entry free.
 *  - Every OTHER writer that can touch the `Loading` sentinel awaits
 *    [hydrated] first; without that, a failed fetch racing hydration
 *    converts `Loading` → NotSnoozing and the seed is discarded exactly
 *    in the offline case.
 *  - Successful GET/POST results persist the raw end time.
 *  - Submit wins: a fetch (screen-entry revalidate or the door-event
 *    hook) that completes while a snooze submit is in flight is
 *    discarded — the submit's authoritative response supersedes it.
 *  - The door-event voiding hook: the server voids a snooze on ANY door
 *    event, and the (deleted) Android 60s poll was the only channel
 *    that noticed. The FCM receive path calls [SnoozeDoorEventBridge];
 *    while state is Snoozing that triggers ONE debounced refetch.
 */
class NetworkSnoozeRepository(
    private val networkButtonDataSource: NetworkButtonDataSource,
    private val serverConfigRepository: ServerConfigRepository,
    private val authRepository: AuthRepository,
    private val statusSnapshotStore: StatusSnapshotStore,
    private val snoozeDoorEventBridge: SnoozeDoorEventBridge,
    private val snoozeNotificationsOption: Boolean,
    private val currentTimeSeconds: () -> Long,
    private val externalScope: CoroutineScope,
) : SnoozeRepository {
    private val _snoozeState = MutableStateFlow<SnoozeState>(SnoozeState.Loading)

    /** ADR-022: the repository owns the authoritative [StateFlow]. */
    override val snoozeState: StateFlow<SnoozeState> = _snoozeState

    /**
     * Completed once the persisted snapshot has been applied (or found
     * absent). Writers that can touch the `Loading` sentinel await this.
     */
    private val hydrated = CompletableDeferred<Unit>()

    /**
     * Guards [lastFetchedAtSeconds], [submitsInFlight], [acceptGeneration],
     * [doorRefetchInFlight], and every sentinel/state write.
     */
    private val writeMutex = Mutex()

    /** Epoch seconds of the last server round-trip (persisted or live). */
    private var lastFetchedAtSeconds: Long? = null

    /**
     * Number of snooze POSTs currently in flight (a counter, not a
     * boolean, so overlapping submits can't clear each other's guard).
     */
    private var submitsInFlight = 0

    /**
     * Bumped on every ACCEPTED server write. A GET captures it before
     * its network call and its result is dropped when it changed — a
     * GET launched BEFORE a submit but resolving AFTER the submit
     * cleared its in-flight counter would otherwise silently revert
     * the just-submitted snooze (in the StateFlow and on disk).
     */
    private var acceptGeneration = 0L

    /** Debounce for the door-event hook: at most one in-flight refetch. */
    private var doorRefetchInFlight = false

    init {
        snoozeDoorEventBridge.register(::onDoorEventReceived)
        externalScope.launch {
            hydrate()
            // Same TTL gate as the screen-entry revalidate — ONE freshness
            // rule, so init-fetch and revalidate can never diverge.
            fetchIfStale()
        }
    }

    override suspend fun fetchSnoozeStatus(): AppResult<SnoozeState, FetchError> = externalScope.async { doFetchSnoozeStatus() }.await()

    override suspend fun revalidateSnoozeIfStale() {
        externalScope
            .async {
                hydrated.await()
                fetchIfStale()
            }.await()
    }

    private suspend fun fetchIfStale() {
        val last = writeMutex.withLock { lastFetchedAtSeconds }
        val age = last?.let { currentTimeSeconds() - it }
        // Future-skewed (negative age) counts as stale — a backwards
        // clock correction must never suppress revalidation.
        if (age != null && age in 0..FETCH_TTL_SECONDS) {
            Logger.d { "snooze: last fetch ${age}s ago; skipping" }
            return
        }
        doFetchSnoozeStatus()
    }

    override suspend fun snoozeNotifications(
        snoozeDurationHours: String,
        snoozeEventTimestampSeconds: Long,
    ): AppResult<SnoozeState, ActionError> =
        externalScope
            .async {
                doSnoozeNotifications(snoozeDurationHours, snoozeEventTimestampSeconds)
            }.await()

    /**
     * Seeds the `Loading` sentinel from the persisted snapshot and
     * records its `fetchedAt` so [fetchIfStale] owns the (single)
     * freshness decision. Always completes [hydrated].
     */
    private suspend fun hydrate() {
        try {
            val snapshot = statusSnapshotStore.read(
                SnoozeSnapshot.KEY,
                SnoozeSnapshot.SCHEMA_VERSION,
                SnoozeSnapshotDto.serializer(),
            ) ?: return
            val seeded = snoozeStateFromEndTime(snapshot.payload.endTimeSeconds)
            writeMutex.withLock {
                lastFetchedAtSeconds = snapshot.fetchedAtEpochSeconds
                // CAS: only seed the untouched sentinel. A server value that
                // raced ahead (unlikely — writers await hydration) wins.
                if (_snoozeState.value is SnoozeState.Loading) {
                    _snoozeState.value = seeded
                    Logger.i { "snoozeState <- $seeded (source=disk seed)" }
                }
            }
        } finally {
            hydrated.complete(Unit)
        }
    }

    private suspend fun doFetchSnoozeStatus(): AppResult<SnoozeState, FetchError> {
        hydrated.await()
        val serverConfig = serverConfigRepository.serverConfig.value
            ?: serverConfigRepository.fetchServerConfig()
        if (serverConfig == null) {
            Logger.e { "Server config is null" }
            clearLoadingState()
            return AppResult.Error(FetchError.NotReady)
        }
        if (!snoozeNotificationsOption) {
            // Feature disabled: idempotent fall-through. No network call,
            // surface the current flow value as success so callers don't see
            // a phantom failure.
            Logger.w { "Snooze notifications disabled" }
            delay(500)
            clearLoadingState()
            return AppResult.Success(_snoozeState.value)
        }
        // Capture the accept generation BEFORE the network call: if any
        // submit is accepted while this GET is in flight, the generation
        // moves and this GET's (older) response is dropped on arrival.
        val startGeneration = writeMutex.withLock { acceptGeneration }
        return when (
            val result = networkButtonDataSource.fetchSnoozeEndTimeSeconds(
                buildTimestamp = serverConfig.buildTimestamp,
            )
        ) {
            is NetworkResult.Success -> {
                val applied = acceptServerEndTime(
                    endTimeSeconds = result.data,
                    source = "GET",
                    fetchStartGeneration = startGeneration,
                )
                AppResult.Success(applied)
            }
            is NetworkResult.HttpError -> {
                Logger.e { "Snooze fetch HTTP ${result.code}" }
                clearLoadingState()
                AppResult.Error(FetchError.NetworkFailed)
            }
            NetworkResult.ConnectionFailed -> {
                Logger.e { "Snooze fetch connection failed" }
                clearLoadingState()
                AppResult.Error(FetchError.NetworkFailed)
            }
        }
    }

    /**
     * The single accept path for a server-provided end time (GET and
     * POST): derive the state, publish it, record freshness from ONE
     * clock read, bump the generation, and write through to disk.
     *
     * Submit wins: a GET result is discarded when a submit is in
     * flight OR when any write was accepted after the GET started
     * ([fetchStartGeneration] no longer current) — without the
     * generation check, a GET launched before a submit but resolving
     * after it cleared its in-flight counter would silently revert the
     * just-submitted snooze in the StateFlow and on disk.
     */
    private suspend fun acceptServerEndTime(
        endTimeSeconds: Long,
        source: String,
        fetchStartGeneration: Long? = null,
    ): SnoozeState =
        writeMutex.withLock {
            if (fetchStartGeneration != null &&
                (submitsInFlight > 0 || acceptGeneration != fetchStartGeneration)
            ) {
                Logger.i { "snoozeState: dropping $source result (superseded by a submit)" }
                return@withLock _snoozeState.value
            }
            val now = currentTimeSeconds()
            val newState = snoozeStateFromEndTime(endTimeSeconds)
            _snoozeState.value = newState
            lastFetchedAtSeconds = now
            acceptGeneration++
            persistEndTime(endTimeSeconds, now)
            Logger.i { "snoozeState <- $newState (source=$source)" }
            newState
        }

    private suspend fun doSnoozeNotifications(
        snoozeDurationHours: String,
        snoozeEventTimestampSeconds: Long,
    ): AppResult<SnoozeState, ActionError> {
        hydrated.await()
        val serverConfig = serverConfigRepository.serverConfig.value
            ?: serverConfigRepository.fetchServerConfig()
        if (serverConfig == null) {
            Logger.e { "Server config is null" }
            return AppResult.Error(ActionError.NetworkFailed)
        }
        if (!snoozeNotificationsOption) {
            Logger.w { "Snooze notifications disabled" }
            delay(500)
            // Feature disabled: pretend it succeeded and surface the current
            // flow value so callers don't see a phantom network failure.
            return AppResult.Success(_snoozeState.value)
        }
        // ADR-027: token is fetched at the repository layer.
        val idToken = authRepository.getIdToken(forceRefresh = true)
        if (idToken == null) {
            Logger.e { "Snooze: getIdToken returned null" }
            return AppResult.Error(ActionError.NotAuthenticated)
        }
        writeMutex.withLock { submitsInFlight++ }
        try {
            return when (
                val result = networkButtonDataSource.snoozeNotifications(
                    buildTimestamp = serverConfig.buildTimestamp,
                    remoteButtonPushKey = serverConfig.remoteButtonPushKey,
                    idToken = idToken.asString(),
                    snoozeDurationHours = snoozeDurationHours,
                    snoozeEventTimestampSeconds = snoozeEventTimestampSeconds,
                )
            ) {
                is NetworkResult.Success -> {
                    // Compute the new state from the server's authoritative end
                    // time, write it to the observable flow, and return the SAME
                    // value. Subscribers observing [snoozeState] see the update
                    // via the flow alone — no caller needs the return value for
                    // correctness (ADR-022).
                    val newState = acceptServerEndTime(
                        endTimeSeconds = result.data,
                        source = "POST",
                    )
                    AppResult.Success(newState)
                }
                is NetworkResult.HttpError -> {
                    Logger.e { "Snooze HTTP ${result.code}" }
                    // HTTP 404 on snooze submit is the server's "event timestamp
                    // does not match current event" response from
                    // `submitSnoozeNotificationsRequest`. The handler also returns
                    // 404 for invalid duration, which is structurally impossible
                    // from this client (the Android enum mirrors the server's
                    // allowed list), so treating 404 as event-changed is safe in
                    // practice. See `docs/SNOOZE_BEHAVIOR.md`.
                    if (result.code == HTTP_NOT_FOUND) {
                        AppResult.Error(ActionError.SnoozeEventChanged)
                    } else {
                        AppResult.Error(ActionError.NetworkFailed)
                    }
                }
                NetworkResult.ConnectionFailed -> {
                    Logger.e { "Snooze connection failed" }
                    AppResult.Error(ActionError.NetworkFailed)
                }
            }
        } finally {
            writeMutex.withLock { submitsInFlight-- }
        }
    }

    /**
     * Door-event voiding hook (registered on [SnoozeDoorEventBridge]).
     * The server voids a snooze on any door event; while we show
     * Snoozing, one debounced refetch keeps the row honest. NotSnoozing
     * needs nothing — a snooze set from another device is covered by the
     * screen-entry revalidate.
     *
     * Called on whatever thread FCM delivers on, so the launch happens
     * first (thread-safe) and the Snoozing check + debounce flag are
     * read/written only under [writeMutex] — a plain check-then-launch
     * here raced two near-simultaneous door events into duplicate GETs.
     */
    private fun onDoorEventReceived() {
        externalScope.launch {
            val proceed = writeMutex.withLock {
                if (_snoozeState.value !is SnoozeState.Snoozing || doorRefetchInFlight) {
                    false
                } else {
                    doorRefetchInFlight = true
                    true
                }
            }
            if (!proceed) return@launch
            try {
                Logger.i { "snooze: door event while Snoozing; refetching" }
                doFetchSnoozeStatus()
            } finally {
                writeMutex.withLock { doorRefetchInFlight = false }
            }
        }
    }

    /** Caller holds [writeMutex]; [nowEpochSeconds] is the accept-time clock read. */
    private suspend fun persistEndTime(
        endTimeSeconds: Long,
        nowEpochSeconds: Long,
    ) {
        statusSnapshotStore.write(
            SnoozeSnapshot.KEY,
            SnoozeSnapshot.SCHEMA_VERSION,
            SnoozeSnapshotDto.serializer(),
            StatusSnapshot(
                payload = SnoozeSnapshotDto(endTimeSeconds = endTimeSeconds),
                fetchedAtEpochSeconds = nowEpochSeconds,
                confirmedAtEpochSeconds = nowEpochSeconds,
            ),
        )
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404

        /**
         * Screen-entry revalidate + init-fetch gate: a snapshot fresher
         * than this skips the network (STATUS_CACHE_PLAN.md D3). Self
         * expiry needs no fetch (recomputed from the clock); this bounds
         * staleness for server-side voiding + cross-device changes.
         */
        const val FETCH_TTL_SECONDS: Long = 5L * 60L
    }

    /** If still Loading (first fetch), fall back to NotSnoozing so the UI doesn't show "Loading..." forever. */
    private suspend fun clearLoadingState() {
        // Await hydration so a failed fetch racing the disk seed can't
        // convert the sentinel to NotSnoozing and discard a valid
        // snapshot (exactly the offline cold-start case).
        hydrated.await()
        writeMutex.withLock {
            if (_snoozeState.value is SnoozeState.Loading) {
                _snoozeState.value = SnoozeState.NotSnoozing
                Logger.i { "snoozeState <- NotSnoozing (source=clearLoading)" }
            }
        }
    }

    private fun snoozeStateFromEndTime(endTimeSeconds: Long): SnoozeState {
        if (endTimeSeconds <= 0) return SnoozeState.NotSnoozing
        return if (endTimeSeconds > currentTimeSeconds()) {
            SnoozeState.Snoozing(endTimeSeconds)
        } else {
            SnoozeState.NotSnoozing
        }
    }
}
