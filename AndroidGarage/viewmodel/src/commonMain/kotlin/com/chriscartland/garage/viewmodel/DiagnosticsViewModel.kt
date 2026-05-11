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

package com.chriscartland.garage.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chriscartland.garage.domain.coroutines.DispatcherProvider
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.usecase.ClearDiagnosticsUseCase
import com.chriscartland.garage.usecase.ObserveDiagnosticsCountUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Drives the Diagnostics screen — one VM per screen (ADR-026). Exposes
 * the lifetime counters surfaced by [ObserveDiagnosticsCountUseCase] and
 * the user-initiated "Clear all diagnostics" action. Phase 43 — depends
 * on UseCases only.
 *
 * Replaces the legacy `AppLoggerViewModel`, which doubled as the
 * Diagnostics screen VM AND an injectable dependency for `AppStartup`.
 * Startup-time concerns (seed + prune + initial fcm-subscribe log) now
 * live in dedicated UseCases that `AppStartup` invokes directly.
 */
interface DiagnosticsViewModel {
    val initCurrentDoorCount: StateFlow<Long>
    val initRecentDoorCount: StateFlow<Long>
    val userFetchCurrentDoorCount: StateFlow<Long>
    val userFetchRecentDoorCount: StateFlow<Long>
    val fcmReceivedDoorCount: StateFlow<Long>
    val fcmSubscribeTopicCount: StateFlow<Long>
    val exceededExpectedTimeWithoutFcmCount: StateFlow<Long>
    val timeWithoutFcmInExpectedRangeCount: StateFlow<Long>

    /**
     * `true` while the most recent [clearDiagnostics] call is in flight,
     * `false` otherwise. UI uses this to render the Clear button in a
     * loading state during the (suspending, two-store) clear operation
     * — the action takes long enough on a heavily-populated DB that
     * silent button-tap is jarring.
     */
    val clearInFlight: StateFlow<Boolean>

    /**
     * User-initiated "Clear all diagnostics" action. Wipes both the
     * Room app-event log and the lifetime DataStore counters.
     * Confirmation dialog is the caller's responsibility.
     */
    fun clearDiagnostics()
}

class DefaultDiagnosticsViewModel(
    private val observeAppLogCount: ObserveDiagnosticsCountUseCase,
    private val clearDiagnosticsUseCase: ClearDiagnosticsUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel(),
    DiagnosticsViewModel {
    private val _initCurrentDoorCount = MutableStateFlow(0L)
    override val initCurrentDoorCount: StateFlow<Long> = _initCurrentDoorCount

    private val _initRecentDoorCount = MutableStateFlow(0L)
    override val initRecentDoorCount: StateFlow<Long> = _initRecentDoorCount

    private val _userFetchCurrentDoorCount = MutableStateFlow(0L)
    override val userFetchCurrentDoorCount: StateFlow<Long> = _userFetchCurrentDoorCount

    private val _userFetchRecentDoorCount = MutableStateFlow(0L)
    override val userFetchRecentDoorCount: StateFlow<Long> = _userFetchRecentDoorCount

    private val _fcmReceivedDoorCount = MutableStateFlow(0L)
    override val fcmReceivedDoorCount: StateFlow<Long> = _fcmReceivedDoorCount

    private val _fcmSubscribeTopicCount = MutableStateFlow(0L)
    override val fcmSubscribeTopicCount: StateFlow<Long> = _fcmSubscribeTopicCount

    private val _exceededExpectedTimeWithoutFcmCount = MutableStateFlow(0L)
    override val exceededExpectedTimeWithoutFcmCount: StateFlow<Long> = _exceededExpectedTimeWithoutFcmCount

    private val _timeWithoutFcmInExpectedRangeCount = MutableStateFlow(0L)
    override val timeWithoutFcmInExpectedRangeCount: StateFlow<Long> = _timeWithoutFcmInExpectedRangeCount

    private val _clearInFlight = MutableStateFlow(false)
    override val clearInFlight: StateFlow<Boolean> = _clearInFlight

    init {
        observeCount(AppLoggerKeys.INIT_CURRENT_DOOR, _initCurrentDoorCount)
        observeCount(AppLoggerKeys.INIT_RECENT_DOOR, _initRecentDoorCount)
        observeCount(AppLoggerKeys.USER_FETCH_CURRENT_DOOR, _userFetchCurrentDoorCount)
        observeCount(AppLoggerKeys.USER_FETCH_RECENT_DOOR, _userFetchRecentDoorCount)
        observeCount(AppLoggerKeys.FCM_DOOR_RECEIVED, _fcmReceivedDoorCount)
        observeCount(AppLoggerKeys.FCM_SUBSCRIBE_TOPIC, _fcmSubscribeTopicCount)
        observeCount(AppLoggerKeys.EXCEEDED_EXPECTED_TIME_WITHOUT_FCM, _exceededExpectedTimeWithoutFcmCount)
        observeCount(AppLoggerKeys.TIME_WITHOUT_FCM_IN_EXPECTED_RANGE, _timeWithoutFcmInExpectedRangeCount)
    }

    private fun observeCount(
        key: String,
        target: MutableStateFlow<Long>,
    ) {
        viewModelScope.launch(dispatchers.io) {
            observeAppLogCount(key).collect { target.value = it }
        }
    }

    override fun clearDiagnostics() {
        viewModelScope.launch(dispatchers.io) {
            _clearInFlight.value = true
            try {
                clearDiagnosticsUseCase()
            } finally {
                // try/finally so cancellation or unexpected throw still
                // resets the flag — the button shouldn't appear stuck in
                // a "Clearing..." state if the underlying work errors out.
                _clearInFlight.value = false
            }
        }
    }
}
