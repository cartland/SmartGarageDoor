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

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chriscartland.garage.config.APP_CONFIG
import com.chriscartland.garage.config.FetchOnViewModelInit
import com.chriscartland.garage.door.LoadingResult.Complete
import com.chriscartland.garage.door.LoadingResult.Error
import com.chriscartland.garage.door.LoadingResult.Loading
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

interface DoorViewModel {
    suspend fun fetchBuildTimestampCached(): String?
    val currentDoorEvent: StateFlow<LoadingResult<DoorEvent?>>
    val recentDoorEvents: StateFlow<LoadingResult<List<DoorEvent>>>
    fun fetchCurrentDoorEvent()
    fun fetchRecentDoorEvents()
}

@HiltViewModel
class DoorViewModelImpl @Inject constructor(
    private val doorRepository: DoorRepository,
) : ViewModel(), DoorViewModel {
    override suspend fun fetchBuildTimestampCached(): String? =
        doorRepository.fetchBuildTimestampCached()

    private val _currentDoorEvent =
        MutableStateFlow<LoadingResult<DoorEvent?>>(LoadingResult.Loading(null))
    override val currentDoorEvent: StateFlow<LoadingResult<DoorEvent?>> = _currentDoorEvent

    private val _recentDoorEvents =
        MutableStateFlow<LoadingResult<List<DoorEvent>>>(LoadingResult.Loading(listOf()))
    override val recentDoorEvents: StateFlow<LoadingResult<List<DoorEvent>>> = _recentDoorEvents

    init {
        Log.d("DoorViewModel", "init")
        viewModelScope.launch(Dispatchers.IO) {
            doorRepository.currentDoorEvent.collect {
                _currentDoorEvent.value = LoadingResult.Complete(it)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            doorRepository.recentDoorEvents.collect {
                _recentDoorEvents.value = LoadingResult.Complete(it)
            }
        }
        // Decide whether to fetch with network data when ViewModel is initialized
        when (APP_CONFIG.fetchOnViewModelInit) {
            FetchOnViewModelInit.Yes -> {
                fetchCurrentDoorEvent()
                fetchRecentDoorEvents()
            }
            FetchOnViewModelInit.No -> { /* Do nothing */
            }
        }
    }

    override fun fetchCurrentDoorEvent() {
        viewModelScope.launch(Dispatchers.IO) {
            _currentDoorEvent.value = LoadingResult.Loading(_currentDoorEvent.value.data)
            doorRepository.fetchCurrentDoorEvent()
        }
    }

    override fun fetchRecentDoorEvents() {
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
