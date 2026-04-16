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
import com.chriscartland.garage.domain.model.FcmRegistrationStatus
import com.chriscartland.garage.domain.model.FetchError
import com.chriscartland.garage.domain.model.LoadingResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface DoorViewModel {
    val fcmRegistrationStatus: StateFlow<FcmRegistrationStatus>
    val currentDoorEvent: StateFlow<LoadingResult<DoorEvent?>>
    val recentDoorEvents: StateFlow<LoadingResult<List<DoorEvent>>>

    /** True when the last check-in is older than the staleness threshold (11 min). */
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
    private val fcmRegistrationManager: FcmRegistrationManager,
    private val checkInStalenessManager: CheckInStalenessManager,
    private val fetchOnInit: Boolean = true,
) : ViewModel(),
    DoorViewModel {
    // ADR-017 Rule 6: explicit MutableStateFlow + collect, no stateIn in ViewModels.
    private val _fcmRegistrationStatus = MutableStateFlow(FcmRegistrationStatus.UNKNOWN)
    override val fcmRegistrationStatus: StateFlow<FcmRegistrationStatus> = _fcmRegistrationStatus

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
            fcmRegistrationManager.registrationStatus.collect {
                _fcmRegistrationStatus.value = it
            }
        }
        viewModelScope.launch(dispatchers.io) {
            checkInStalenessManager.isCheckInStale.collect {
                _isCheckInStale.value = it
            }
        }
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
}
