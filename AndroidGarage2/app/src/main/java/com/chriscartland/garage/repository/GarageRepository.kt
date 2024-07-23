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

    private val _recentEventsData = MutableStateFlow<Result<List<DoorEvent>>>(Result.Loading(emptyList()))
    val recentEventsData: StateFlow<Result<List<DoorEvent>>> = _recentEventsData.asStateFlow()

    suspend fun fetchRecentDoorEvents(buildTimestamp: String, session: String) {
        _recentEventsData.value = Result.Loading(recentEventsData.value.dataOrNull())
        try {
            val response = service.getRecentEventData(
                buildTimestamp = buildTimestamp,
                session = session
            )

            Log.d("GarageRepository", "Response: $response")
            if (response.recentEventData.isEmpty()) {
                Log.e("GarageRepository", "recentEventData is empty")
            }
            val doorEvents = response.recentEventData.map {
                it.asDoorEvent()
            }.filterNotNull()
            if (doorEvents.size != response.recentEventData.size) {
                Log.e(
                    "GarageRepository",
                    "Door events size ${doorEvents.size} " +
                            "does not match response size ${response.recentEventData.size}"
                )
            }
            Log.d("GarageRepository", "Success: $doorEvents")
            _recentEventsData.value = Result.Success(doorEvents)
        } catch (e: IOException) {
            _recentEventsData.value = Result.Error(e)
        }
    }

}

