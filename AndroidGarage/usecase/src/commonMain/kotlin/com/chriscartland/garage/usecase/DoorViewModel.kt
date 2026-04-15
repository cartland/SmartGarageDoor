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
import com.chriscartland.garage.domain.coroutines.AppClock
import com.chriscartland.garage.domain.coroutines.DispatcherProvider
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.FcmRegistrationStatus
import com.chriscartland.garage.domain.model.FetchError
import com.chriscartland.garage.domain.model.LoadingResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

interface DoorViewModel {
    val fcmRegistrationStatus: StateFlow<FcmRegistrationStatus>
    val currentDoorEvent: StateFlow<LoadingResult<DoorEvent?>>
    val recentDoorEvents: StateFlow<LoadingResult<List<DoorEvent>>>

    /** True when the last check-in is older than [CHECK_IN_STALE_THRESHOLD_SECONDS]. */
    val isCheckInStale: StateFlow<Boolean>

    fun deregisterFcm()

    fun fetchCurrentDoorEvent()

    fun fetchRecentDoorEvents()
}

class DefaultDoorViewModel(
    observeDoorEvents: ObserveDoorEventsUseCase,
    private val logAppEvent: LogAppEventUseCase,
    private val dispatchers: DispatcherProvider,
    private val fetchCurrentDoorEventUseCase: FetchCurrentDoorEventUseCase,
    private val fetchRecentDoorEventsUseCase: FetchRecentDoorEventsUseCase,
    private val deregisterFcmUseCase: DeregisterFcmUseCase,
    fcmRegistrationManager: FcmRegistrationManager,
    private val clock: AppClock = AppClock { System.currentTimeMillis() / 1000 },
    private val scope: CoroutineScope,
    private val fetchOnInit: Boolean = true,
) : ViewModel(),
    DoorViewModel {
    // Observe FCM status from the app-scoped manager (ADR-014, ADR-015).
    override val fcmRegistrationStatus: StateFlow<FcmRegistrationStatus> =
        fcmRegistrationManager.registrationStatus
            .stateIn(viewModelScope, SharingStarted.Eagerly, FcmRegistrationStatus.UNKNOWN)

    private val _currentDoorEvent =
        MutableStateFlow<LoadingResult<DoorEvent?>>(LoadingResult.Loading(null))
    override val currentDoorEvent: StateFlow<LoadingResult<DoorEvent?>> = _currentDoorEvent

    private val _recentDoorEvents =
        MutableStateFlow<LoadingResult<List<DoorEvent>>>(LoadingResult.Loading(listOf()))
    override val recentDoorEvents: StateFlow<LoadingResult<List<DoorEvent>>> = _recentDoorEvents

    private val _isCheckInStale = MutableStateFlow(false)
    override val isCheckInStale: StateFlow<Boolean> = _isCheckInStale

    init {
        Logger.d { "init" }
        viewModelScope.launch(dispatchers.io) {
            observeDoorEvents.current().collect {
                Logger.d { "currentDoorEvent collect: $it" }
                _currentDoorEvent.value = LoadingResult.Complete(it)
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
                logAppEvent(AppLoggerKeys.INIT_CURRENT_DOOR)
                logAppEvent(AppLoggerKeys.INIT_RECENT_DOOR)
            }
            fetchCurrentDoorEvent()
            fetchRecentDoorEvents()
        }
        // Staleness: re-evaluate on data change and periodically.
        // Uses injected scope (not viewModelScope) so tests control the lifecycle.
        scope.launch(dispatchers.io) {
            _currentDoorEvent.collect { event ->
                _isCheckInStale.value = computeStale(event)
            }
        }
        scope.launch(dispatchers.io) {
            while (true) {
                delay(STALE_CHECK_INTERVAL_MS)
                _isCheckInStale.value = computeStale(_currentDoorEvent.value)
            }
        }
        // Log staleness transitions (moved from Main.kt composable).
        scope.launch(dispatchers.io) {
            var isFirstEmission = true
            isCheckInStale.collect { stale ->
                if (stale) {
                    logAppEvent(AppLoggerKeys.EXCEEDED_EXPECTED_TIME_WITHOUT_FCM)
                } else if (!isFirstEmission) {
                    logAppEvent(AppLoggerKeys.TIME_WITHOUT_FCM_IN_EXPECTED_RANGE)
                }
                isFirstEmission = false
            }
        }
    }

    override fun deregisterFcm() {
        Logger.d { "deregisterFcm" }
        viewModelScope.launch(dispatchers.io) {
            deregisterFcmUseCase()
        }
    }

    override fun fetchCurrentDoorEvent() {
        Logger.d { "fetchCurrentDoorEvent" }
        viewModelScope.launch(dispatchers.io) {
            _currentDoorEvent.value = LoadingResult.Loading(_currentDoorEvent.value.data)
            when (val result = fetchCurrentDoorEventUseCase()) {
                is AppResult.Success -> {
                    // Data flows through repository → local cache → Flow observation
                }
                is AppResult.Error -> {
                    // Restore previous data so UI exits Loading state
                    _currentDoorEvent.value = LoadingResult.Complete(_currentDoorEvent.value.data)
                    when (result.error) {
                        FetchError.NotReady -> Logger.w { "Server config not ready" }
                        FetchError.NetworkFailed -> Logger.w { "Network request failed" }
                    }
                }
            }
        }
    }

    override fun fetchRecentDoorEvents() {
        Logger.d { "fetchRecentDoorEvents" }
        viewModelScope.launch(dispatchers.io) {
            _recentDoorEvents.value = LoadingResult.Loading(_recentDoorEvents.value.data)
            when (val result = fetchRecentDoorEventsUseCase()) {
                is AppResult.Success -> {
                    // Data flows through repository → local cache → Flow observation
                }
                is AppResult.Error -> {
                    // Restore previous data so UI exits Loading state
                    _recentDoorEvents.value = LoadingResult.Complete(_recentDoorEvents.value.data)
                    when (result.error) {
                        FetchError.NotReady -> Logger.w { "Server config not ready" }
                        FetchError.NetworkFailed -> Logger.w { "Network request failed" }
                    }
                }
            }
        }
    }

    private fun computeStale(event: LoadingResult<DoorEvent?>): Boolean {
        val checkInTime = event.data?.lastCheckInTimeSeconds ?: return false
        val age = clock.nowEpochSeconds() - checkInTime
        return age > CHECK_IN_STALE_THRESHOLD_SECONDS
    }

    companion object {
        /** 11 minutes — matches the old OldLastCheckInBanner threshold. */
        const val CHECK_IN_STALE_THRESHOLD_SECONDS = 11L * 60

        /** Re-evaluate staleness every 30 seconds. */
        const val STALE_CHECK_INTERVAL_MS = 30_000L
    }
}
