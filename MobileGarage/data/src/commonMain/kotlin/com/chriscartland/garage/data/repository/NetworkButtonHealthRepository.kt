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

package com.chriscartland.garage.data.repository

import co.touchlab.kermit.Logger
import com.chriscartland.garage.data.NetworkButtonHealthDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.data.statuscache.ButtonHealthSnapshot
import com.chriscartland.garage.data.statuscache.ButtonHealthSnapshotDto
import com.chriscartland.garage.data.statuscache.StatusSnapshot
import com.chriscartland.garage.data.statuscache.StatusSnapshotStore
import com.chriscartland.garage.domain.coroutines.AppClock
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.ButtonHealth
import com.chriscartland.garage.domain.model.ButtonHealthError
import com.chriscartland.garage.domain.model.ButtonHealthState
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.domain.repository.ButtonHealthRepository
import com.chriscartland.garage.domain.repository.ServerConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository for the remote-button health state.
 *
 * Pattern matches [NetworkSnoozeRepository] (ADR-019): all state
 * mutations happen on [externalScope] so VM-scope cancellation can
 * never strand the singleton.
 *
 * FCM-vs-fetch ordering rule: both fetch success and FCM updates
 * check [shouldOverwrite] before writing — a stale fetch result
 * cannot clobber a fresher FCM update, and vice versa. UNKNOWN is
 * treated as oldest (any non-UNKNOWN value wins over a current
 * UNKNOWN regardless of timestamp).
 *
 * Persisted last-known verdict (STATUS_CACHE_PLAN.md D2): `init`
 * hydrates the StateFlow from [statusSnapshotStore] so a cold start
 * shows the last verdict instantly instead of "Checking…"; accepted
 * server writes persist back. Rules layered on the in-memory SWR:
 *  - All writers (hydration seed, fetch, FCM) serialize on
 *    [writeMutex] so check-then-act can't interleave and memory/disk
 *    can't record different winners.
 *  - A disk seed gets NO UNKNOWN privilege: the first server result
 *    (any state, including UNKNOWN) replaces a seeded value — without
 *    this, a hydrated ONLINE would drop a legitimate post-rotation
 *    UNKNOWN fetch result for up to the pubsub sweep interval.
 *  - Display-TTL: a snapshot whose last server CONFIRMATION is older
 *    than [DISPLAY_TTL_SECONDS] (or future-skewed) is not seeded —
 *    never show an affirmative verdict that stale.
 *  - A successful fetch refreshes the envelope's `confirmedAt` even
 *    when the payload write is rejected as timestamp-equal (the fetch
 *    is an authoritative "still true now"); without this a stable
 *    device's revalidates never refresh freshness and the display-TTL
 *    hides a perfectly-confirmed verdict.
 *  - HTTP 401/403 clears BOTH the persisted snapshot and the
 *    in-memory value (targeted exception to
 *    [writeErrorPreservingComplete]) — a de-allowlisted user converges
 *    to Hidden, not a permanently cached "Available".
 */
class NetworkButtonHealthRepository(
    private val networkButtonHealthDataSource: NetworkButtonHealthDataSource,
    private val serverConfigRepository: ServerConfigRepository,
    private val authRepository: AuthRepository,
    private val statusSnapshotStore: StatusSnapshotStore,
    private val appClock: AppClock,
    private val externalScope: CoroutineScope,
) : ButtonHealthRepository {
    private val _buttonHealth =
        MutableStateFlow<LoadingResult<ButtonHealth>>(LoadingResult.Loading(null))

    override val buttonHealth: StateFlow<LoadingResult<ButtonHealth>> = _buttonHealth

    /** Serializes every StateFlow write + its disk write-through. */
    private val writeMutex = Mutex()

    /**
     * True while the current Complete value came from disk, not the
     * server. Guarded by [writeMutex].
     */
    private var currentIsDiskSeed = false

    init {
        externalScope.launch { hydrateFromSnapshot() }
    }

    override suspend fun fetchButtonHealth(): AppResult<ButtonHealth, ButtonHealthError> =
        externalScope
            .async {
                doFetchButtonHealth()
            }.await()

    override fun applyFcmUpdate(update: ButtonHealth) {
        externalScope.launch {
            tryWrite(update, source = "FCM")
        }
    }

    /**
     * Seeds the StateFlow with the persisted last-known verdict, if
     * fresh enough. Never re-persists (the envelope on disk is already
     * authoritative) and never overwrites a server value that raced
     * ahead of it — the mutex plus the `!is Complete` check make the
     * seed strictly first-writer-only.
     */
    private suspend fun hydrateFromSnapshot() {
        val snapshot = statusSnapshotStore.read(
            ButtonHealthSnapshot.KEY,
            ButtonHealthSnapshot.SCHEMA_VERSION,
            ButtonHealthSnapshotDto.serializer(),
        ) ?: return
        if (snapshot.confirmedAgeSeconds(appClock.nowEpochSeconds()) > DISPLAY_TTL_SECONDS) {
            Logger.i { "buttonHealth: persisted snapshot older than display-TTL; not seeding" }
            return
        }
        writeMutex.withLock {
            if (_buttonHealth.value is LoadingResult.Complete) {
                Logger.d { "buttonHealth: server value arrived before hydration; dropping disk seed" }
                return
            }
            val seeded = snapshot.payload.toDomain()
            _buttonHealth.value = LoadingResult.Complete(seeded)
            currentIsDiskSeed = true
            Logger.i { "buttonHealth <- $seeded (source=disk seed)" }
        }
    }

    private suspend fun doFetchButtonHealth(): AppResult<ButtonHealth, ButtonHealthError> {
        // Stale-while-revalidate: if we already have a Complete value, keep
        // it visible during the refresh. Only flip to Loading when there's
        // no prior data (initial fetch or after a hard error). Without this,
        // every fetch flashed the UI back to "Checking" — and combined with
        // the SubscriptionManager's pre-fix auth-state churn, produced
        // visible Checking/Online flicker on repeated fetches.
        writeMutex.withLock {
            if (_buttonHealth.value !is LoadingResult.Complete) {
                _buttonHealth.value = LoadingResult.Loading(_buttonHealth.value.data)
            }
        }
        val serverConfig = serverConfigRepository.serverConfig.value
            ?: serverConfigRepository.fetchServerConfig()
        if (serverConfig == null) {
            Logger.e { "Server config is null" }
            writeErrorPreservingComplete(IllegalStateException("Server config is null"))
            return AppResult.Error(ButtonHealthError.Network())
        }
        // ADR-027: token is fetched at the repository layer, not the UseCase.
        val idToken = authRepository.getIdToken(forceRefresh = true)
        if (idToken == null) {
            Logger.e { "Button health: getIdToken returned null" }
            writeErrorPreservingComplete(IllegalStateException("ID token unavailable"))
            return AppResult.Error(ButtonHealthError.NotAuthenticated())
        }
        return when (
            val result = networkButtonHealthDataSource.fetchButtonHealth(
                buildTimestamp = serverConfig.remoteButtonBuildTimestamp,
                remoteButtonPushKey = serverConfig.remoteButtonPushKey,
                idToken = idToken.asString(),
            )
        ) {
            is NetworkResult.Success -> {
                tryWrite(result.data, source = "fetch")
                AppResult.Success(result.data)
            }
            is NetworkResult.HttpError -> {
                Logger.e { "Button health HTTP ${result.code}" }
                if (result.code == 401 || result.code == 403) {
                    // Targeted exception to writeErrorPreservingComplete: a
                    // Forbidden means the server no longer vouches for this
                    // user seeing the verdict — and the manager unsubscribes
                    // from FCM on Forbidden, so nothing would ever correct a
                    // preserved (or persisted) "Available" again. Clear both.
                    clearVerdict(reason = "HTTP ${result.code}")
                    AppResult.Error(ButtonHealthError.Forbidden())
                } else {
                    writeErrorPreservingComplete(IllegalStateException("HTTP ${result.code}"))
                    AppResult.Error(ButtonHealthError.Network())
                }
            }
            NetworkResult.ConnectionFailed -> {
                Logger.e { "Button health connection failed" }
                writeErrorPreservingComplete(IllegalStateException("Connection failed"))
                AppResult.Error(ButtonHealthError.Network())
            }
        }
    }

    /**
     * Stale-while-revalidate for the error path: if we already have a
     * `Complete` value, keep it visible (the previous result is still the
     * best information we have). Only transition to `Error` if the current
     * state isn't already a known-good value.
     *
     * Without this, a single failed fetch (transient network blip, server
     * 5xx) discarded the previous good value — display logic mapped
     * `Error` to "Checking" — and the user saw the pill stuck on Checking
     * until something else fixed it (FCM update, manual retry).
     */
    private suspend fun writeErrorPreservingComplete(exception: Throwable) {
        writeMutex.withLock {
            if (_buttonHealth.value !is LoadingResult.Complete) {
                _buttonHealth.value = LoadingResult.Error(exception)
            }
        }
    }

    /** Forbidden path: drop the verdict everywhere (memory + disk). */
    private suspend fun clearVerdict(reason: String) {
        writeMutex.withLock {
            _buttonHealth.value = LoadingResult.Loading(null)
            currentIsDiskSeed = false
        }
        statusSnapshotStore.clear(setOf(ButtonHealthSnapshot.KEY))
        Logger.w { "buttonHealth: cleared verdict (memory + snapshot) after $reason" }
    }

    /** Apply [incoming] only if it should overwrite the current value. */
    private suspend fun tryWrite(
        incoming: ButtonHealth,
        source: String,
    ) {
        writeMutex.withLock {
            val current = _buttonHealth.value
            // A disk seed is last-process information: ANY server result —
            // including UNKNOWN, which shouldOverwrite would normally drop —
            // is fresher and replaces it.
            val accepted = currentIsDiskSeed || shouldOverwrite(current, incoming)
            if (accepted) {
                _buttonHealth.value = LoadingResult.Complete(incoming)
                currentIsDiskSeed = false
                persistVerdict(incoming)
                Logger.i { "buttonHealth <- $incoming (source=$source)" }
            } else {
                Logger.d { "buttonHealth: dropping stale $source update $incoming, current=$current" }
                if (source == "fetch") {
                    // The fetch still authoritatively confirmed the stored
                    // value as current — refresh confirmedAt so the
                    // display-TTL tracks confirmations, not accepted writes.
                    refreshConfirmedAt()
                }
            }
        }
    }

    /** Write-through for an ACCEPTED server value. Caller holds [writeMutex]. */
    private suspend fun persistVerdict(health: ButtonHealth) {
        val now = appClock.nowEpochSeconds()
        statusSnapshotStore.write(
            ButtonHealthSnapshot.KEY,
            ButtonHealthSnapshot.SCHEMA_VERSION,
            ButtonHealthSnapshotDto.serializer(),
            StatusSnapshot(
                payload = ButtonHealthSnapshotDto.fromDomain(health),
                fetchedAtEpochSeconds = now,
                confirmedAtEpochSeconds = now,
            ),
        )
    }

    /** Rejected-but-successful fetch: bump `confirmedAt`, keep the payload. Caller holds [writeMutex]. */
    private suspend fun refreshConfirmedAt() {
        val existing = statusSnapshotStore.read(
            ButtonHealthSnapshot.KEY,
            ButtonHealthSnapshot.SCHEMA_VERSION,
            ButtonHealthSnapshotDto.serializer(),
        )
        val now = appClock.nowEpochSeconds()
        if (existing != null) {
            statusSnapshotStore.write(
                ButtonHealthSnapshot.KEY,
                ButtonHealthSnapshot.SCHEMA_VERSION,
                ButtonHealthSnapshotDto.serializer(),
                existing.copy(confirmedAtEpochSeconds = now),
            )
            return
        }
        // No envelope (e.g. cleared while this fetch was in flight): persist
        // the current in-memory value so the confirmation isn't lost.
        val current = (_buttonHealth.value as? LoadingResult.Complete)?.data ?: return
        statusSnapshotStore.write(
            ButtonHealthSnapshot.KEY,
            ButtonHealthSnapshot.SCHEMA_VERSION,
            ButtonHealthSnapshotDto.serializer(),
            StatusSnapshot(
                payload = ButtonHealthSnapshotDto.fromDomain(current),
                fetchedAtEpochSeconds = now,
                confirmedAtEpochSeconds = now,
            ),
        )
    }

    /**
     * UNKNOWN is treated as oldest. Otherwise compare stateChangedAtSeconds
     * strictly (`>`, not `>=`) — same-timestamp updates from no-op writes
     * should not trigger a re-emit anyway.
     *
     * Visibility: internal so [NetworkButtonHealthRepositoryTest] can pin
     * the rule directly.
     */
    internal fun shouldOverwrite(
        current: LoadingResult<ButtonHealth>,
        incoming: ButtonHealth,
    ): Boolean {
        // No prior data — accept anything.
        if (current !is LoadingResult.Complete) return true
        val currentValue = current.data ?: return true
        // Current is UNKNOWN — any non-UNKNOWN incoming wins.
        if (currentValue.state == ButtonHealthState.UNKNOWN) {
            return incoming.state != ButtonHealthState.UNKNOWN
        }
        // Incoming is UNKNOWN over a known state — never overwrite.
        if (incoming.state == ButtonHealthState.UNKNOWN) return false
        // Both have known state — compare timestamps strictly.
        val currentTs = currentValue.stateChangedAtSeconds ?: return true
        val incomingTs = incoming.stateChangedAtSeconds ?: return false
        return incomingTs > currentTs
    }

    companion object {
        /**
         * Never show an affirmative verdict whose last server
         * confirmation is older than this (STATUS_CACHE_PLAN.md D2).
         */
        internal const val DISPLAY_TTL_SECONDS: Long = 24L * 60L * 60L
    }
}
