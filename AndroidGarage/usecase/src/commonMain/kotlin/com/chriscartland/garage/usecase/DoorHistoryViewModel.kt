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

package com.chriscartland.garage.usecase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.coroutines.DispatcherProvider
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.FetchError
import com.chriscartland.garage.domain.model.LoadingResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Drives the History screen — one VM per screen (ADR-026). Aggregates the
 * UseCases that the legacy `DoorViewModel` + `AppLoggerViewModel` pair
 * exposed to `DoorHistoryContent.kt`. Phase 43 — depends on UseCases only.
 *
 * The Wide-screen Home dashboard renders this VM in its right pane. On
 * phones it backs the History tab. The legacy VMs remain for callers that
 * haven't been refactored yet (`ProfileContent`).
 */
interface DoorHistoryViewModel {
    val recentDoorEvents: StateFlow<LoadingResult<List<DoorEvent>>>

    /** True when the last check-in is older than the staleness threshold (11 min). */
    val isCheckInStale: StateFlow<Boolean>

    /**
     * Wall-clock time as epoch seconds, ticking on the [LiveClock] cadence.
     * Pass-through of [LiveClock.nowEpochSeconds] (ADR-022).
     */
    val nowEpochSeconds: StateFlow<Long>

    fun fetchRecentDoorEvents()

    fun deregisterFcm()

    fun log(key: String)
}

class DefaultDoorHistoryViewModel(
    observeDoorEvents: ObserveDoorEventsUseCase,
    private val logAppEvent: LogAppEventUseCase,
    private val dispatchers: DispatcherProvider,
    private val fetchRecentDoorEventsUseCase: FetchRecentDoorEventsUseCase,
    private val deregisterFcmUseCase: DeregisterFcmUseCase,
    private val checkInStalenessManager: CheckInStalenessManager,
    private val liveClock: LiveClock,
    // Default false — cold-start fetch lives in `InitialDoorFetchManager`
    // (singleton, idempotent, fires once per process from `AppStartup`).
    // Per-VM init fetch fired on every fresh `NavBackStackEntry`, causing
    // a redundant round-trip on every tab tap even though FCM already
    // covers live updates while the app is open.
    private val fetchOnInit: Boolean = false,
) : ViewModel(),
    DoorHistoryViewModel {
    // ADR-022: pass through LiveClock's StateFlow — no mirror.
    override val nowEpochSeconds: StateFlow<Long> = liveClock.nowEpochSeconds

    private val _recentDoorEvents =
        MutableStateFlow<LoadingResult<List<DoorEvent>>>(LoadingResult.Loading(listOf()))
    override val recentDoorEvents: StateFlow<LoadingResult<List<DoorEvent>>> = _recentDoorEvents

    private val _isCheckInStale = MutableStateFlow(false)
    override val isCheckInStale: StateFlow<Boolean> = _isCheckInStale

    init {
        Logger.d { "init" }
        viewModelScope.launch(dispatchers.io) {
            checkInStalenessManager.isCheckInStale.collect {
                _isCheckInStale.value = it
            }
        }
        viewModelScope.launch(dispatchers.io) {
            observeDoorEvents.recent().collect {
                Logger.d { "recentDoorEvents collect: $it" }
                _recentDoorEvents.value = LoadingResult.Complete(it)
            }
        }
        if (fetchOnInit) {
            viewModelScope.launch(dispatchers.io) {
                logAppEvent(AppLoggerKeys.INIT_RECENT_DOOR)
            }
            fetchRecentDoorEvents()
        }
    }

    override fun fetchRecentDoorEvents() {
        Logger.d { "fetchRecentDoorEvents" }
        viewModelScope.launch(dispatchers.io) {
            // ADR-023: explicit `Complete(...)` write on success — relying on
            // the repo StateFlow to fire is unsafe because MutableStateFlow
            // dedups by equality.
            _recentDoorEvents.value = LoadingResult.Loading(_recentDoorEvents.value.data)
            when (val result = fetchRecentDoorEventsUseCase()) {
                is AppResult.Success -> {
                    _recentDoorEvents.value = LoadingResult.Complete(result.data)
                }
                is AppResult.Error -> {
                    // Restore previous data so UI exits Loading state.
                    _recentDoorEvents.value = LoadingResult.Complete(_recentDoorEvents.value.data)
                    when (result.error) {
                        FetchError.NotReady -> Logger.w { "Server config not ready" }
                        FetchError.NetworkFailed -> Logger.w { "Network request failed" }
                    }
                }
            }
        }
    }

    override fun deregisterFcm() {
        Logger.d { "deregisterFcm" }
        viewModelScope.launch(dispatchers.io) {
            deregisterFcmUseCase()
        }
    }

    override fun log(key: String) {
        viewModelScope.launch(dispatchers.io) {
            logAppEvent(key)
        }
    }
}
