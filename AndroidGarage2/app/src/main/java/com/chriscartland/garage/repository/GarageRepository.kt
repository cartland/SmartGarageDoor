package com.chriscartland.garage.repository

import android.util.Log
import com.chriscartland.garage.internet.GarageService
import com.chriscartland.garage.model.DoorEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import javax.inject.Inject

class GarageRepository @Inject constructor(
    private val service: GarageService
) {
    private val _currentEventData = MutableStateFlow<Result<DoorEvent?>>(Result.Loading(null))
    val currentEventData: StateFlow<Result<DoorEvent?>> = _currentEventData.asStateFlow()

    suspend fun fetchCurrentDoorEvent() {
        _currentEventData.value = Result.Loading(currentEventData.value.dataOrNull())
        try {
            val response = service.getCurrentEventData(
                buildTimestamp = "Sat Mar 13 14:45:00 2021",
                session = null,
            )
            Log.d("GarageRepository", "Response: $response")
            if (response.currentEventData == null) {
                Log.e("GarageRepository", "currentEventData is null")
            } else if (response.currentEventData.currentEvent == null) {
                Log.e("GarageRepository", "currentEvent is null")
            }
            val doorEvent = response.currentEventData?.currentEvent?.asDoorEvent()
            if (doorEvent == null) {
                Log.e("GarageRepository", "Door event is null")
            }
            Log.d("GarageRepository", "Success: $doorEvent")
            _currentEventData.value = Result.Success(doorEvent)
        } catch (e: IOException) {
            Log.e("GarageRepository", "Error: $e")
            _currentEventData.value = Result.Error(e)
        }
    }
}

