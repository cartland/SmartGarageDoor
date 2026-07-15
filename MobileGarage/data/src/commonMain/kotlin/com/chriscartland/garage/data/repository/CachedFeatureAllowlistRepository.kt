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
import com.chriscartland.garage.data.NetworkFeatureAllowlistDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.data.statuscache.AllowlistSnapshot
import com.chriscartland.garage.data.statuscache.AllowlistSnapshotDto
import com.chriscartland.garage.data.statuscache.StatusSnapshot
import com.chriscartland.garage.data.statuscache.StatusSnapshotStore
import com.chriscartland.garage.domain.coroutines.AppClock
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.FeatureAllowlist
import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.domain.repository.FeatureAllowlistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Caches the per-user feature allowlist in a [StateFlow] owned by the
 * repository (ADR-022). On construction, an always-on collector watches
 * [AuthRepository.authState]:
 *  - On `Authenticated` → fetch the allowlist using the user's ID token.
 *  - On `Unauthenticated` → clear the cache to null so a stale "yes"
 *    from the previous user can't leak into the next session.
 *  - On `Unknown` → no-op (haven't decided yet).
 *
 * Network errors during fetch leave the previous successful value in
 * place (matching `CachedServerConfigRepository`'s behavior). Sign-out
 * always clears, even after a successful fetch — security boundary.
 *
 * Persisted last-known allowlist (STATUS_CACHE_PLAN.md D4): hydration
 * removes the cold-start pop-in of the Developer/Function-List rows,
 * and the repo still ALWAYS revalidates on the Authenticated emission
 * — no fetch-skip TTL, because app restart is the only
 * grant-propagation path (Settings has no pull-to-refresh) and
 * skipping the fetch would trade one tiny request for a stuck grant.
 *
 * Account-keyed hydration is the cross-account guard: the keyed check
 * runs INSIDE the auth gate (`authState.first { it !is Unknown }`) —
 * at construction the auth state is typically `Unknown`, so there is
 * no email to compare yet; `Unknown` must never seed and never delete.
 * A snapshot whose `accountEmail` differs from the signed-in user is
 * deleted (confirmed mismatch — covers a sign-out clear missed by
 * StateFlow conflation or process death); a snapshot with NO email is
 * treated as absent but not deleted (never destroy data on missing
 * information). Only after the keyed hydration does the ordinary auth
 * collector start — it replays the current value, so the Authenticated
 * fetch still fires after the seed (hydrate-before-fetch, sequenced in
 * one coroutine).
 */
class CachedFeatureAllowlistRepository(
    private val networkDataSource: NetworkFeatureAllowlistDataSource,
    private val authRepository: AuthRepository,
    private val statusSnapshotStore: StatusSnapshotStore,
    private val appClock: AppClock,
    externalScope: CoroutineScope,
) : FeatureAllowlistRepository {
    private val _allowlist = MutableStateFlow<FeatureAllowlist?>(null)
    override val allowlist: StateFlow<FeatureAllowlist?> = _allowlist

    private val fetchMutex: Mutex = Mutex()

    init {
        externalScope.launch {
            // Keyed hydration inside the auth gate — see class KDoc.
            when (val first = authRepository.authState.first { it !is AuthState.Unknown }) {
                is AuthState.Authenticated -> hydrate(signedInEmail = first.user.email.asString())
                AuthState.Unauthenticated -> statusSnapshotStore.clear(setOf(AllowlistSnapshot.KEY))
                AuthState.Unknown -> Unit // unreachable: filtered above
            }
            authRepository.authState.collect { state ->
                when (state) {
                    is AuthState.Authenticated -> fetchAllowlist()
                    AuthState.Unauthenticated -> {
                        if (_allowlist.value != null) {
                            Logger.i { "allowlist cleared on sign-out" }
                            _allowlist.value = null
                        }
                        // Best-effort disk clear (account-keying is the
                        // guarantee; SignOutCacheClearManager also clears).
                        statusSnapshotStore.clear(setOf(AllowlistSnapshot.KEY))
                    }
                    AuthState.Unknown -> Unit
                }
            }
        }
    }

    /** Seeds the in-memory cache from a snapshot owned by [signedInEmail]. */
    private suspend fun hydrate(signedInEmail: String) {
        val snapshot = statusSnapshotStore.read(
            AllowlistSnapshot.KEY,
            AllowlistSnapshot.SCHEMA_VERSION,
            AllowlistSnapshotDto.serializer(),
        ) ?: return
        val snapshotEmail = snapshot.accountEmail
        if (snapshotEmail == null) {
            // No owner recorded: treat as absent, but never delete on
            // missing information.
            Logger.w { "allowlist snapshot has no accountEmail; ignoring" }
            return
        }
        if (snapshotEmail != signedInEmail) {
            // Confirmed cross-account snapshot (a sign-out clear missed by
            // conflation or process death) — refuse AND delete.
            Logger.w { "allowlist snapshot belongs to another account; deleting" }
            statusSnapshotStore.clear(setOf(AllowlistSnapshot.KEY))
            return
        }
        if (snapshot.confirmedAgeSeconds(appClock.nowEpochSeconds()) > DISPLAY_TTL_SECONDS) {
            Logger.i { "allowlist snapshot older than display-TTL; not seeding" }
            return
        }
        // CAS: only seed if the collector's fetch hasn't landed yet (it
        // can't have — the collector starts after hydration — but keep
        // the guard).
        if (_allowlist.value == null) {
            val seeded = snapshot.payload.toDomain()
            _allowlist.value = seeded
            Logger.i { "allowlist <- $seeded (source=disk seed)" }
        }
    }

    /**
     * Force-fetch from the server. On success the value is written to
     * [allowlist] and persisted (keyed to the fetching account). The
     * user-switch guard discards a response that raced a sign-out or
     * account change.
     */
    override suspend fun fetchAllowlist(): FeatureAllowlist? =
        fetchMutex.withLock {
            val initialAuth = authRepository.authState.value
            if (initialAuth !is AuthState.Authenticated) {
                Logger.d { "Skipping fetch — not authenticated" }
                return@withLock null
            }
            val initialEmail = initialAuth.user.email
            // ADR-027: token is no longer in AuthState; fetch it explicitly.
            val token = authRepository.getIdToken(forceRefresh = true)
            if (token == null) {
                Logger.w { "Skipping fetch — getIdToken returned null" }
                return@withLock null
            }
            val result = try {
                when (val r = networkDataSource.fetchAllowlist(token.asString())) {
                    is NetworkResult.Success -> r.data
                    is NetworkResult.HttpError -> null
                    NetworkResult.ConnectionFailed -> null
                }
            } catch (e: Exception) {
                Logger.e(e) { "Allowlist fetch threw — leaving cache untouched" }
                null
            }
            // User-switch guard: if the signed-in email changed during the
            // network call, discard the result rather than writing it.
            val finalAuth = authRepository.authState.value
            if (finalAuth !is AuthState.Authenticated || finalAuth.user.email != initialEmail) {
                Logger.w { "Auth state changed during fetch; discarding result for stale user" }
                return@withLock null
            }
            if (result != null) {
                _allowlist.value = result
                persistAllowlist(result, accountEmail = initialEmail.asString())
                Logger.i { "allowlist <- $result (source=GET)" }
            }
            result
        }

    private suspend fun persistAllowlist(
        allowlist: FeatureAllowlist,
        accountEmail: String,
    ) {
        val now = appClock.nowEpochSeconds()
        statusSnapshotStore.write(
            AllowlistSnapshot.KEY,
            AllowlistSnapshot.SCHEMA_VERSION,
            AllowlistSnapshotDto.serializer(),
            StatusSnapshot(
                payload = AllowlistSnapshotDto.fromDomain(allowlist),
                fetchedAtEpochSeconds = now,
                confirmedAtEpochSeconds = now,
                accountEmail = accountEmail,
            ),
        )
    }

    private companion object {
        /**
         * Never seed an allowlist whose last server confirmation is
         * older than this — access grants/revocations must not ride a
         * week-old snapshot (the always-revalidate fetch corrects it
         * seconds later anyway; this bounds the wrong-rows window).
         */
        const val DISPLAY_TTL_SECONDS: Long = 24L * 60L * 60L
    }
}
