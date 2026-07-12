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

package com.chriscartland.garage.usecase

import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.domain.repository.UserScopedCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * App-scoped manager that clears user-scoped cache entries on sign-out
 * (ADR-015 Manager pattern; started from [AppStartup]). Part of the
 * status-snapshot cache — see `MobileGarage/docs/STATUS_CACHE_PLAN.md`.
 *
 * Best-effort by design: the persisted-cache safety story for per-user
 * entries is account-keyed hydration (a snapshot written by a
 * different account is refused at read time), because this clear can
 * be missed — StateFlow conflation can skip a transient
 * `Unauthenticated` emission during a fast account switch, and process
 * death can land between sign-out and the disk write. This manager
 * handles the common case promptly; account keying is the guarantee.
 *
 * Projects `authState` to a boolean before reacting so token-refresh
 * writes of new `Authenticated` instances never re-trigger it.
 */
class SignOutCacheClearManager(
    private val authRepository: AuthRepository,
    private val userScopedCache: UserScopedCache,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    private var job: Job? = null

    /** Idempotent — calling twice does not start a second collector. */
    fun start() {
        if (job?.isActive == true) {
            Logger.d { "SignOutCacheClearManager: already running" }
            return
        }
        job = scope.launch(dispatcher) {
            authRepository.authState
                .map { it is AuthState.Unauthenticated }
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    Logger.i { "SignOutCacheClearManager: signed out; clearing user-scoped cache entries" }
                    userScopedCache.clearUserScopedEntries()
                }
        }
    }
}
