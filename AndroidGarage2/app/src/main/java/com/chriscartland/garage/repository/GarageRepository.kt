package com.chriscartland.garage.repository

import android.util.Log
import com.chriscartland.garage.APP_CONFIG
import com.chriscartland.garage.db.LocalDataSource
import com.chriscartland.garage.internet.GarageNetworkService
import com.chriscartland.garage.model.DoorEvent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GarageRepository @Inject constructor(
    private val localDataSource: LocalDataSource,
    private val network: GarageNetworkService,
    private val serverConfigRepository: ServerConfigRepository,
) {
    val currentDoorEvent: Flow<DoorEvent> = localDataSource.currentDoorEvent
    val recentDoorEvents: Flow<List<DoorEvent>> = localDataSource.recentDoorEvents

    suspend fun buildTimestamp(): String? =
        serverConfigRepository.serverConfigCached()?.buildTimestamp

    fun insertDoorEvent(doorEvent: DoorEvent) {
        Log.d("insertDoorEvent", "Inserting door event: $doorEvent")
        localDataSource.insertDoorEvent(doorEvent)
    }

    suspend fun fetchCurrentDoorEvent() {
        val tag = "fetchCurrentDoorEvent"
        val buildTimestamp = buildTimestamp()
        if (buildTimestamp == null) {
            Log.e(tag, "Server config is null")
            return
        }
        try {
            Log.d(tag, "Fetching current door event")
            val response = network.getCurrentEventData(
                buildTimestamp = buildTimestamp,
                session = null,
            )
            if (response.code() != 200) {
                Log.e(tag, "Response code is ${response.code()}")
                return
            }
            val body = response.body()
            if (body == null) {
                Log.e(tag, "Response body is null")
                return
            }
            Log.d(tag, "Response: $response")
            if (body.currentEventData == null) {
                Log.e(tag, "currentEventData is null")
            } else if (body.currentEventData.currentEvent == null) {
                Log.e(tag, "currentEvent is null")
            }
            val doorEvent = body.currentEventData?.currentEvent?.asDoorEvent()
            if (doorEvent == null) {
                Log.e(tag, "Door event is null")
                return
            }
            Log.d(tag, "Success: $doorEvent")
            localDataSource.insertDoorEvent(doorEvent)
        } catch (e: Exception) {
            Log.e(tag, "Error: $e")
        }
    }

    suspend fun fetchRecentDoorEvents() {
        val tag = "fetchRecentDoorEvents"
        val buildTimestamp = buildTimestamp()
        if (buildTimestamp == null) {
            Log.e(tag, "Server config is null")
            return
        }
        try {
            Log.d(tag, "Fetching recent door events")
            val response = network.getRecentEventData(
                buildTimestamp = buildTimestamp,
                session = null,
                count = APP_CONFIG.recentEventCount,
            )
            if (response.code() != 200) {
                Log.e(tag, "Response code is ${response.code()}")
                return
            }
            val body = response.body()
            if (body == null) {
                Log.e(tag, "Response body is null")
                return
            }
            Log.d(tag, "Response: $response")
            if (body.eventHistory.isNullOrEmpty()) {
                Log.i(tag, "recentEventData is empty")
                return
            }
            val doorEvents = body.eventHistory.map {
                it.currentEvent?.asDoorEvent()
            }.filterNotNull()
            if (doorEvents.size != body.eventHistory.size) {
                Log.e(
                    tag,
                    "Door events size ${doorEvents.size} " +
                            "does not match response size ${body.eventHistory.size}"
                )
            }
            Log.d(tag, "Success: $doorEvents")
            localDataSource.replaceDoorEvents(doorEvents)
        } catch (e: IllegalArgumentException) {
            Log.e(tag, "IllegalArgumentException: $e")
        } catch (e: Exception) {
            Log.e(tag, "Exception: $e")
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GarageRepositoryEntryPoint {
    fun garageRepository(): GarageRepository
}
