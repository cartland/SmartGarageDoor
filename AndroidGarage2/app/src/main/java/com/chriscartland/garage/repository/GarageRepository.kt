package com.chriscartland.garage.repository

import android.util.Log
import com.chriscartland.garage.db.LocalDataSource
import com.chriscartland.garage.internet.GarageNetworkService
import com.chriscartland.garage.model.DoorEvent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GarageRepository @Inject constructor(
    private val localDataSource: LocalDataSource,
    private val network: GarageNetworkService,
) {
    val currentDoorEvent: Flow<DoorEvent> = localDataSource.currentDoorEvent
    val recentDoorEvents: Flow<List<DoorEvent>> = localDataSource.recentDoorEvents

    fun insertDoorEvent(doorEvent: DoorEvent) {
        Log.d("insertDoorEvent", "Inserting door event: $doorEvent")
        localDataSource.insertDoorEvent(doorEvent)
    }

    suspend fun fetchCurrentDoorEvent() {
        try {
            Log.d("fetchCurrentDoorEvent", "Fetching current door event")
            val response = network.getCurrentEventData(
                buildTimestamp = "Sat Mar 13 14:45:00 2021",
                session = null,
            )
            if (response.code() != 200) {
                Log.e("fetchCurrentDoorEvent", "Response code is ${response.code()}")
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
                return
            }
            Log.d("fetchCurrentDoorEvent", "Success: $doorEvent")
            localDataSource.insertDoorEvent(doorEvent)
        } catch (e: Exception) {
            Log.e("fetchCurrentDoorEvent", "Error: $e")
        }
    }

    suspend fun fetchRecentDoorEvents() {
        try {
            Log.d("fetchRecentDoorEvents", "Fetching recent door events")
            val response = network.getRecentEventData(
                buildTimestamp = "Sat Mar 13 14:45:00 2021",
                session = null,
                count = 30,
            )
            if (response.code() != 200) {
                Log.e("fetchRecentDoorEvents", "Response code is ${response.code()}")
                return
            }
            val body = response.body()
            if (body == null) {
                Log.e("fetchRecentDoorEvents", "Response body is null")
                return
            }
            Log.d("fetchRecentDoorEvents", "Response: $response")
            if (body.eventHistory.isNullOrEmpty()) {
                Log.i("fetchRecentDoorEvents", "recentEventData is empty")
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
            localDataSource.replaceDoorEvents(doorEvents)
        } catch (e: IllegalArgumentException) {
            Log.e("fetchRecentDoorEvents", "IllegalArgumentException: $e")
        } catch (e: Exception) {
            Log.e("fetchRecentDoorEvents", "Exception: $e")
        }
    }
}
