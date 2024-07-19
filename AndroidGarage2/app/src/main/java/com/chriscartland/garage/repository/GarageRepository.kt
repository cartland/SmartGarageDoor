package com.chriscartland.garage.repository

import android.util.Log
import com.chriscartland.garage.internet.service
import com.chriscartland.garage.model.CurrentEventDataResponse
import com.chriscartland.garage.model.DoorEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

class GarageRepository {

    private val _currentEventData = MutableStateFlow<DoorEvent?>(null)
    val currentEventData: StateFlow<DoorEvent?> = _currentEventData.asStateFlow()

    suspend fun fetchCurrentEventData() {
        val call = service.getCurrentEventData(
            buildTimestamp = "Sat%20Mar%2013%2014%3A45%3A00%202021",
            session = null,
        )

        try {
            val response: CurrentEventDataResponse? = call.execute().body()
            _currentEventData.value = response?.currentEventData?.currentEvent?.asDoorEvent()
        } catch (e: IOException) {
            Log.e("GarageRepository", "Error fetching current event: $e")
        }
    }
}
