package com.chriscartland.garage.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chriscartland.garage.APP_CONFIG
import com.chriscartland.garage.FetchOnViewModelInit
import com.chriscartland.garage.model.DoorEvent
import com.chriscartland.garage.repository.GarageRepository
import com.chriscartland.garage.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val garageRepository: GarageRepository,
) : ViewModel() {

    val currentDoorEvent: StateFlow<Result<DoorEvent?>> = garageRepository.currentEventData

    val recentDoorEvents: StateFlow<Result<List<DoorEvent>?>> = garageRepository.recentEventsData

    init {
        when (APP_CONFIG.fetchOnViewModelInit) {
            FetchOnViewModelInit.Yes -> {
                fetchCurrentDoorEvent()
                fetchRecentDoorEvents()
            }
            FetchOnViewModelInit.No -> { /* Do nothing */ }
        }
    }

    fun fetchCurrentDoorEvent() {
        viewModelScope.launch {
            garageRepository.fetchCurrentDoorEvent()
        }
    }

    fun fetchRecentDoorEvents() {
        viewModelScope.launch {
            garageRepository.fetchRecentDoorEvents()
        }
    }
}
