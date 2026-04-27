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
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.FeatureAllowlist
import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.domain.repository.FeatureAllowlistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 */
class CachedFeatureAllowlistRepository(
    private val networkDataSource: NetworkFeatureAllowlistDataSource,
    private val authRepository: AuthRepository,
    externalScope: CoroutineScope,
) : FeatureAllowlistRepository {
    private val _allowlist = MutableStateFlow<FeatureAllowlist?>(null)
    override val allowlist: StateFlow<FeatureAllowlist?> = _allowlist

    private val fetchMutex: Mutex = Mutex()

    init {
        externalScope.launch {
            authRepository.authState.collect { state ->
                when (state) {
                    is AuthState.Authenticated -> fetchAllowlist()
                    AuthState.Unauthenticated -> {
                        if (_allowlist.value != null) {
                            Logger.i { "allowlist cleared on sign-out" }
                            _allowlist.value = null
                        }
                    }
                    AuthState.Unknown -> Unit
                }
            }
        }
    }

    /**
     * Force-refresh the allowlist. Captures the email at fetch start and
     * re-checks after the network call — if the user switched mid-fetch,
     * the result is for the previous user and is discarded. This is the
     * only place the user-switch race is closed: the auth-listener path
     * clears `_allowlist` on `Unauthenticated` but does not acquire the
     * mutex, so an in-flight fetch with user A's token can resolve while
     * user B is signing in. The post-fetch email check ensures A's
     * answer is never written under B's session.
     */
    override suspend fun fetchAllowlist(): FeatureAllowlist? =
        fetchMutex.withLock {
            val initialAuth = authRepository.authState.value
            if (initialAuth !is AuthState.Authenticated) {
                Logger.d { "Skipping fetch — not authenticated" }
                return@withLock null
            }
            val initialEmail = initialAuth.user.email
            val idToken = initialAuth.user.idToken.asString()
            val result = try {
                when (val r = networkDataSource.fetchAllowlist(idToken)) {
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
                Logger.i { "allowlist <- $result (source=GET)" }
            }
            result
        }
}
