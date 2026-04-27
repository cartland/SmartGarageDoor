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
     * Force-refresh the allowlist. Reads the current auth state at call
     * time — if the user signed out between the trigger and this run,
     * skip the fetch (the auth-listener path will have cleared the
     * cache).
     */
    override suspend fun fetchAllowlist(): FeatureAllowlist? =
        fetchMutex.withLock {
            val authState = authRepository.authState.value
            if (authState !is AuthState.Authenticated) {
                Logger.d { "Skipping fetch — not authenticated" }
                return@withLock null
            }
            val idToken = authState.user.idToken.asString()
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
            if (result != null) {
                _allowlist.value = result
                Logger.i { "allowlist <- $result (source=GET)" }
            }
            result
        }
}
