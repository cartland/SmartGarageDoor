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
import com.chriscartland.garage.domain.coroutines.DispatcherProvider
import com.chriscartland.garage.domain.model.AppLoggerKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface AppLoggerViewModel {
    fun log(key: String)

    val initCurrentDoorCount: StateFlow<Long>
    val initRecentDoorCount: StateFlow<Long>
    val userFetchCurrentDoorCount: StateFlow<Long>
    val userFetchRecentDoorCount: StateFlow<Long>
    val fcmReceivedDoorCount: StateFlow<Long>
    val fcmSubscribeTopicCount: StateFlow<Long>
    val exceededExpectedTimeWithoutFcmCount: StateFlow<Long>
    val timeWithoutFcmInExpectedRangeCount: StateFlow<Long>
}

class DefaultAppLoggerViewModel(
    private val logAppEvent: LogAppEventUseCase,
    private val observeAppLogCount: ObserveAppLogCountUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel(),
    AppLoggerViewModel {
    private val _initCurrentDoorCount = MutableStateFlow<Long>(0L)
    override val initCurrentDoorCount = _initCurrentDoorCount

    private val _initRecentDoorCount = MutableStateFlow<Long>(0L)
    override val initRecentDoorCount = _initRecentDoorCount

    private val _userFetchCurrentDoorCount = MutableStateFlow<Long>(0L)
    override val userFetchCurrentDoorCount = _userFetchCurrentDoorCount

    private val _userFetchRecentDoorCount = MutableStateFlow<Long>(0L)
    override val userFetchRecentDoorCount = _userFetchRecentDoorCount

    private val _fcmReceivedDoorCount = MutableStateFlow<Long>(0L)
    override val fcmReceivedDoorCount = _fcmReceivedDoorCount

    private val _fcmSubscribeTopicCount = MutableStateFlow<Long>(0L)
    override val fcmSubscribeTopicCount = _fcmSubscribeTopicCount

    private val _exceededExpectedTimeWithoutFcmCount = MutableStateFlow<Long>(0L)
    override val exceededExpectedTimeWithoutFcmCount = _exceededExpectedTimeWithoutFcmCount

    private val _timeWithoutFcmInExpectedRangeCount = MutableStateFlow<Long>(0L)
    override val timeWithoutFcmInExpectedRangeCount = _timeWithoutFcmInExpectedRangeCount

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

    override fun log(key: String) {
        viewModelScope.launch(dispatchers.io) {
            logAppEvent(key)
        }
    }
}
