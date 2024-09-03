package com.chriscartland.garage.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chriscartland.garage.model.DoorEvent
import com.chriscartland.garage.repository.GarageRepository
import com.chriscartland.garage.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
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
//        fetchCurrentDoorEvent()
//        fetchRecentDoorEvents()
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
