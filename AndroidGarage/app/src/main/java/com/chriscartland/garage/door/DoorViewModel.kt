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
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chriscartland.garage.applogger.AppLoggerRepository
import com.chriscartland.garage.config.APP_CONFIG
import com.chriscartland.garage.config.AppLoggerKeys
import com.chriscartland.garage.config.FetchOnViewModelInit
import com.chriscartland.garage.door.LoadingResult.Complete
import com.chriscartland.garage.door.LoadingResult.Error
import com.chriscartland.garage.door.LoadingResult.Loading
import com.chriscartland.garage.fcm.DoorFcmRepository
import com.chriscartland.garage.fcm.DoorFcmState
import com.chriscartland.garage.fcm.toFcmTopic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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

@HiltViewModel
class DoorViewModelImpl @Inject constructor(
    private val appLoggerRepository: AppLoggerRepository,
    private val doorRepository: DoorRepository,
    private val doorFcmRepository: DoorFcmRepository,
) : ViewModel(), DoorViewModel {
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
        Log.d(TAG, "init")
        viewModelScope.launch(Dispatchers.IO) {
            doorRepository.currentDoorEvent.collect {
                Log.d(TAG, "currentDoorEvent collect: $it")
                _currentDoorEvent.value = LoadingResult.Complete(it)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            doorRepository.recentDoorEvents.collect {
                Log.d(TAG, "recentDoorEvents collect: $it")
                _recentDoorEvents.value = LoadingResult.Complete(it)
            }
        }
        // Decide whether to fetch with network data when ViewModel is initialized
        when (APP_CONFIG.fetchOnViewModelInit) {
            FetchOnViewModelInit.Yes -> {
                viewModelScope.launch(Dispatchers.IO) {
                    appLoggerRepository.log(AppLoggerKeys.INIT_CURRENT_DOOR)
                    appLoggerRepository.log(AppLoggerKeys.INIT_RECENT_DOOR)
                }
                fetchCurrentDoorEvent()
                fetchRecentDoorEvents()
            }
            FetchOnViewModelInit.No -> { /* Do nothing */
            }
        }
    }

    override fun fetchFcmRegistrationStatus(activity: Activity) {
        Log.d(TAG, "fetchFcmRegistrationStatus")
        viewModelScope.launch(Dispatchers.Main) {
            Log.d(TAG, "Fetching FCM registration status")
            val status = doorFcmRepository.fetchStatus(activity)
            Log.d(TAG, "Fetched FCM registration status: $status")
            _fcmRegistrationStatus.value = when (status) {
                is DoorFcmState.Registered -> FcmRegistrationStatus.REGISTERED
                DoorFcmState.NotRegistered -> FcmRegistrationStatus.NOT_REGISTERED
                DoorFcmState.Unknown -> FcmRegistrationStatus.UNKNOWN
            }
        }
    }

    override fun registerFcm(activity: Activity) {
        Log.d(TAG, "registerFcm")
        viewModelScope.launch(Dispatchers.Main) {
            val buildTimestamp = doorRepository.fetchBuildTimestampCached()
            if (buildTimestamp == null) {
                Log.e(TAG, "buildTimestamp is null, cannot register FCM")
                _fcmRegistrationStatus.value = FcmRegistrationStatus.NOT_REGISTERED
                return@launch
            }
            Log.d(TAG, "Registering FCM for buildTimestamp: $buildTimestamp")
            val result = doorFcmRepository.registerDoor(activity, buildTimestamp.toFcmTopic())
            _fcmRegistrationStatus.value = when (result) {
                is DoorFcmState.Registered -> FcmRegistrationStatus.REGISTERED
                DoorFcmState.NotRegistered -> FcmRegistrationStatus.NOT_REGISTERED
                DoorFcmState.Unknown -> FcmRegistrationStatus.UNKNOWN
            }.also {
                Log.d(TAG, "Updated FcmRegistrationStatus: $it")
            }
        }
    }

    override fun deregisterFcm(activity: Activity) {
        Log.d(TAG, "deregisterFcm")
        viewModelScope.launch(Dispatchers.IO) {
            val result = doorFcmRepository.deregisterDoor(activity)
            _fcmRegistrationStatus.value = when(result) {
                is DoorFcmState.Registered -> FcmRegistrationStatus.REGISTERED
                DoorFcmState.NotRegistered -> FcmRegistrationStatus.NOT_REGISTERED
                DoorFcmState.Unknown -> FcmRegistrationStatus.UNKNOWN
            }.also {
                Log.d(TAG, "Updated FcmRegistrationStatus: $it")
            }
        }
    }

    override fun fetchCurrentDoorEvent() {
        Log.d(TAG, "fetchCurrentDoorEvent")
        viewModelScope.launch(Dispatchers.IO) {
            _currentDoorEvent.value = LoadingResult.Loading(_currentDoorEvent.value.data)
            doorRepository.fetchCurrentDoorEvent()
        }
    }

    override fun fetchRecentDoorEvents() {
        Log.d(TAG, "fetchRecentDoorEvents")
        viewModelScope.launch(Dispatchers.IO) {
            _recentDoorEvents.value = LoadingResult.Loading(_recentDoorEvents.value.data)
            doorRepository.fetchRecentDoorEvents()
        }
    }
}

/**
 * A sealed class to represent the state of a loading operation.
 *
 * When [Loading] or [Complete], the current data is available.
 * When [Error] ,the current data is null.
 */
sealed class LoadingResult<out T> {
    data class Loading<out T>(internal val d: T?) : LoadingResult<T>()
    data class Complete<out T>(internal val d: T?) : LoadingResult<T>()
    data class Error(val exception: Throwable) : LoadingResult<Nothing>()

    val data: T?
        get() = when (this) {
            is Loading -> this.d
            is Complete -> this.d
            is Error -> null
        }
}

private const val TAG = "DoorViewModel"
