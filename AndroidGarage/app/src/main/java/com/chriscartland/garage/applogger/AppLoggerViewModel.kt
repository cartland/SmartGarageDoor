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

package com.chriscartland.garage.applogger

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chriscartland.garage.config.AppLoggerKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

interface AppLoggerViewModel {
    fun log(key: String)
    fun writeCsvToUri(context: Context, uri: Uri)
    val initCurrentDoorCount: StateFlow<Long>
    val initRecentDoorCount: StateFlow<Long>
    val userFetchCurrentDoorCount: StateFlow<Long>
    val userFetchRecentDoorCount: StateFlow<Long>
    val fcmReceivedDoorCount: StateFlow<Long>
    val fcmSubscribeTopicCount: StateFlow<Long>
    val exceededExpectedTimeWithoutFcmCount: StateFlow<Long>
    val timeWithoutFcmInExpectedRangeCount: StateFlow<Long>
}

@HiltViewModel
class AppLoggerViewModelImpl @Inject constructor(
    private val appLoggerRepository: AppLoggerRepository,
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
        viewModelScope.launch(Dispatchers.IO) {
            appLoggerRepository.countKey(AppLoggerKeys.INIT_CURRENT_DOOR).collect {
                _initCurrentDoorCount.value = it
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            appLoggerRepository.countKey(AppLoggerKeys.INIT_RECENT_DOOR).collect {
                _initRecentDoorCount.value = it
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            appLoggerRepository.countKey(AppLoggerKeys.USER_FETCH_CURRENT_DOOR).collect {
                _userFetchCurrentDoorCount.value = it
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            appLoggerRepository.countKey(AppLoggerKeys.USER_FETCH_RECENT_DOOR).collect {
                _userFetchRecentDoorCount.value = it
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            appLoggerRepository.countKey(AppLoggerKeys.FCM_DOOR_RECEIVED).collect {
                _fcmReceivedDoorCount.value = it
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            appLoggerRepository.countKey(AppLoggerKeys.FCM_SUBSCRIBE_TOPIC).collect {
                _fcmSubscribeTopicCount.value = it
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            appLoggerRepository.countKey(AppLoggerKeys.EXCEEDED_EXPECTED_TIME_WITHOUT_FCM).collect {
                _exceededExpectedTimeWithoutFcmCount.value = it
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            appLoggerRepository.countKey(AppLoggerKeys.TIME_WITHOUT_FCM_IN_EXPECTED_RANGE).collect {
                _timeWithoutFcmInExpectedRangeCount.value = it
            }
        }
    }

    override fun log(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            appLoggerRepository.log(key)
        }
    }

    override fun writeCsvToUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            appLoggerRepository.writeCsvToUri(context, uri)
        }
    }
}
