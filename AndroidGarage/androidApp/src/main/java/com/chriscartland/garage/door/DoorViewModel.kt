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

package com.chriscartland.garage.door

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.chriscartland.garage.applogger.AppLoggerRepository
import com.chriscartland.garage.config.AppLoggerKeys
import com.chriscartland.garage.domain.coroutines.DispatcherProvider
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.FcmRegistrationStatus
import com.chriscartland.garage.domain.model.FetchError
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.repository.DoorRepository
import com.chriscartland.garage.usecase.DeregisterFcmUseCase
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import com.chriscartland.garage.usecase.FetchFcmStatusUseCase
import com.chriscartland.garage.usecase.FetchRecentDoorEventsUseCase
import com.chriscartland.garage.usecase.RegisterFcmUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

interface DoorViewModel {
    val fcmRegistrationStatus: StateFlow<FcmRegistrationStatus>
    val currentDoorEvent: StateFlow<LoadingResult<DoorEvent?>>
    val recentDoorEvents: StateFlow<LoadingResult<List<DoorEvent>>>

    fun fetchFcmRegistrationStatus(activity: Activity)

    fun registerFcm(activity: Activity)

    fun deregisterFcm(activity: Activity)

    fun fetchCurrentDoorEvent()

    fun fetchRecentDoorEvents()
}

@Inject
class DefaultDoorViewModel(
    private val appLoggerRepository: AppLoggerRepository,
    private val doorRepository: DoorRepository,
    private val dispatchers: DispatcherProvider,
    private val fetchCurrentDoorEventUseCase: FetchCurrentDoorEventUseCase,
    private val fetchRecentDoorEventsUseCase: FetchRecentDoorEventsUseCase,
    private val fetchFcmStatusUseCase: FetchFcmStatusUseCase,
    private val registerFcmUseCase: RegisterFcmUseCase,
    private val deregisterFcmUseCase: DeregisterFcmUseCase,
    private val fetchOnInit: Boolean = true,
) : ViewModel(),
    DoorViewModel {
    private val _fcmRegistrationStatus =
        MutableStateFlow<FcmRegistrationStatus>(FcmRegistrationStatus.UNKNOWN)
    override val fcmRegistrationStatus: StateFlow<FcmRegistrationStatus> = _fcmRegistrationStatus

    private val _currentDoorEvent =
        MutableStateFlow<LoadingResult<DoorEvent?>>(LoadingResult.Loading(null))
    override val currentDoorEvent: StateFlow<LoadingResult<DoorEvent?>> = _currentDoorEvent

    private val _recentDoorEvents =
        MutableStateFlow<LoadingResult<List<DoorEvent>>>(LoadingResult.Loading(listOf()))
    override val recentDoorEvents: StateFlow<LoadingResult<List<DoorEvent>>> = _recentDoorEvents

    init {
        Logger.d { "init" }
        viewModelScope.launch(dispatchers.io) {
            doorRepository.currentDoorEvent.collect {
                Logger.d { "currentDoorEvent collect: $it" }
                _currentDoorEvent.value = LoadingResult.Complete(it)
            }
        }
        viewModelScope.launch(dispatchers.io) {
            doorRepository.recentDoorEvents.collect {
                Logger.d { "recentDoorEvents collect: $it" }
                _recentDoorEvents.value = LoadingResult.Complete(it)
            }
        }
        if (fetchOnInit) {
            viewModelScope.launch(dispatchers.io) {
                appLoggerRepository.log(AppLoggerKeys.INIT_CURRENT_DOOR)
                appLoggerRepository.log(AppLoggerKeys.INIT_RECENT_DOOR)
            }
            fetchCurrentDoorEvent()
            fetchRecentDoorEvents()
        }
    }

    override fun fetchFcmRegistrationStatus(activity: Activity) {
        Logger.d { "fetchFcmRegistrationStatus" }
        viewModelScope.launch(dispatchers.io) {
            _fcmRegistrationStatus.value = fetchFcmStatusUseCase(activity)
        }
    }

    override fun registerFcm(activity: Activity) {
        Logger.d { "registerFcm" }
        viewModelScope.launch(dispatchers.io) {
            _fcmRegistrationStatus.value = registerFcmUseCase(activity).also {
                Logger.d { "Updated FcmRegistrationStatus: $it" }
            }
        }
    }

    override fun deregisterFcm(activity: Activity) {
        Logger.d { "deregisterFcm" }
        viewModelScope.launch(dispatchers.io) {
            _fcmRegistrationStatus.value = deregisterFcmUseCase(activity).also {
                Logger.d { "Updated FcmRegistrationStatus: $it" }
            }
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
