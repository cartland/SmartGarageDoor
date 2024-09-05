package com.chriscartland.garage.repository

import android.util.Log
import com.chriscartland.garage.internet.GarageService
import com.chriscartland.garage.model.DoorEvent
import com.chriscartland.garage.ui.demoDoorEvents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class GarageRepository @Inject constructor(
    private val service: GarageService
) {
    private val _currentEventData = MutableStateFlow<Result<DoorEvent?>>(Result.Success(demoDoorEvents.firstOrNull()))
    val currentEventData: StateFlow<Result<DoorEvent?>> = _currentEventData.asStateFlow()

    suspend fun fetchCurrentDoorEvent() {
        _currentEventData.value = Result.Loading(currentEventData.value.dataOrNull())
        try {
            val response = service.getCurrentEventData(
                buildTimestamp = "Sat Mar 13 14:45:00 2021",
                session = null,
            )
            if (response.code() != 200) {
                Log.e("fetchCurrentDoorEvent", "Response code is ${response.code()}")
                _currentEventData.value = Result.Error(Throwable("Response code is ${response.code()}"))
                return
            }
            val body = response.body()
            if (body == null) {
                Log.e("fetchCurrentDoorEvent", "Response body is null")
                return
            }
            Log.d("fetchCurrentDoorEvent", "Response: $response")
            if (body.currentEventData == null) {
                Log.e("fetchCurrentDoorEvent", "currentEventData is null")
            } else if (body.currentEventData.currentEvent == null) {
                Log.e("fetchCurrentDoorEvent", "currentEvent is null")
            }
            val doorEvent = body.currentEventData?.currentEvent?.asDoorEvent()
            if (doorEvent == null) {
                Log.e("fetchCurrentDoorEvent", "Door event is null")
            }
            Log.d("fetchCurrentDoorEvent", "Success: $doorEvent")
            _currentEventData.value = Result.Success(doorEvent)
        } catch (e: Exception) {
            Log.e("fetchCurrentDoorEvent", "Error: $e")
            _currentEventData.value = Result.Error(e)
        }
    }

    private val _recentEventsData = MutableStateFlow<Result<List<DoorEvent>>>(Result.Success(demoDoorEvents))
    val recentEventsData: StateFlow<Result<List<DoorEvent>>> = _recentEventsData.asStateFlow()

    suspend fun fetchRecentDoorEvents() {
        _recentEventsData.value = Result.Loading(recentEventsData.value.dataOrNull())
        try {
            val response = service.getRecentEventData(
                buildTimestamp = "Sat Mar 13 14:45:00 2021",
                session = null,
            )
            if (response.code() != 200) {
                Log.e("fetchRecentDoorEvents", "Response code is ${response.code()}")
                _recentEventsData.value = Result.Error(Throwable("Response code is ${response.code()}"))
                return
            }
            val body = response.body()
            if (body == null) {
                Log.e("fetchRecentDoorEvents", "Response body is null")
                _recentEventsData.value = Result.Success(null)
                return
            }
            Log.d("fetchRecentDoorEvents", "Response: $response")
            if (body.eventHistory.isNullOrEmpty()) {
                Log.i("fetchRecentDoorEvents", "recentEventData is empty")
                _recentEventsData.value = Result.Success(null)
                return
            }
            val doorEvents = body.eventHistory.map {
                it.currentEvent?.asDoorEvent()
            }.filterNotNull()
            if (doorEvents.size != body.eventHistory.size) {
                Log.e(
                    "fetchRecentDoorEvents",
                    "Door events size ${doorEvents.size} " +
                            "does not match response size ${body.eventHistory.size}"
                )
            }
            Log.d("fetchRecentDoorEvents", "Success: $doorEvents")
            _recentEventsData.value = Result.Success(doorEvents)
        } catch (e: IllegalArgumentException) {
            Log.e("fetchRecentDoorEvents", "IllegalArgumentException: $e")
            _recentEventsData.value = Result.Error(e)
        } catch (e: Exception) {
            Log.e("fetchRecentDoorEvents", "Exception: $e")
            _recentEventsData.value = Result.Error(e)
        }
    }

}

