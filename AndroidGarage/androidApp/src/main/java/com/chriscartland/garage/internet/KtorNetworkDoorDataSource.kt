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

package com.chriscartland.garage.internet

import co.touchlab.kermit.Logger
import com.chriscartland.garage.data.NetworkDoorDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class KtorNetworkDoorDataSource(
    private val client: HttpClient,
) : NetworkDoorDataSource {
    override suspend fun fetchCurrentDoorEvent(buildTimestamp: String): NetworkResult<DoorEvent> {
        return try {
            val response = client.get("currentEventData") {
                parameter("buildTimestamp", buildTimestamp)
            }
            if (!response.status.isSuccess()) {
                Logger.e { "Response code is ${response.status.value}" }
                return NetworkResult.HttpError(response.status.value)
            }
            val body = response.body<KtorCurrentEventDataResponse>()
            val doorEvent = body.currentEventData?.currentEvent?.toDoorEvent()
            if (doorEvent == null) {
                Logger.e { "Door event is null in response" }
                return NetworkResult.HttpError(response.status.value)
            }
            NetworkResult.Success(doorEvent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.e { "Error fetching current door event: $e" }
            NetworkResult.ConnectionFailed
        }
    }

    override suspend fun fetchRecentDoorEvents(
        buildTimestamp: String,
        count: Int,
    ): NetworkResult<List<DoorEvent>> {
        return try {
            val response = client.get("eventHistory") {
                parameter("buildTimestamp", buildTimestamp)
                parameter("eventHistoryMaxCount", count)
            }
            if (!response.status.isSuccess()) {
                Logger.e { "Response code is ${response.status.value}" }
                return NetworkResult.HttpError(response.status.value)
            }
            val body = response.body<KtorRecentEventDataResponse>()
            if (body.eventHistory.isNullOrEmpty()) {
                Logger.i { "recentEventData is empty" }
                return NetworkResult.Success(emptyList())
            }
            val doorEvents = body.eventHistory.mapNotNull {
                it.currentEvent?.toDoorEvent()
            }
            if (doorEvents.size != body.eventHistory.size) {
                Logger.e { "Door events size ${doorEvents.size} does not match response size ${body.eventHistory.size}" }
            }
            NetworkResult.Success(doorEvents)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.e { "Error fetching recent door events: $e" }
            NetworkResult.ConnectionFailed
        }
    }
}

// region Serializable response types

@Serializable
private data class KtorCurrentEventDataResponse(
    val currentEventData: KtorEventData? = null,
)

@Serializable
private data class KtorRecentEventDataResponse(
    val eventHistory: List<KtorEventData>? = null,
)

@Serializable
private data class KtorEventData(
    val currentEvent: KtorEvent? = null,
)

@Serializable
private data class KtorEvent(
    val type: String? = null,
    val message: String? = null,
    @SerialName("timestampSeconds") val timestampSeconds: Long? = null,
    @SerialName("checkInTimestampSeconds") val checkInTimestampSeconds: Long? = null,
) {
    fun toDoorEvent(): DoorEvent =
        DoorEvent(
            doorPosition = type.toDoorPosition(),
            message = message,
            lastCheckInTimeSeconds = checkInTimestampSeconds,
            lastChangeTimeSeconds = timestampSeconds,
        )
}

private fun String?.toDoorPosition(): DoorPosition =
    when (this) {
        "CLOSED" -> DoorPosition.CLOSED
        "OPENING" -> DoorPosition.OPENING
        "OPENING_TOO_LONG" -> DoorPosition.OPENING_TOO_LONG
        "CLOSING" -> DoorPosition.CLOSING
        "CLOSING_TOO_LONG" -> DoorPosition.CLOSING_TOO_LONG
        "OPEN" -> DoorPosition.OPEN
        "OPEN_MISALIGNED" -> DoorPosition.OPEN_MISALIGNED
        "ERROR_SENSOR_CONFLICT" -> DoorPosition.ERROR_SENSOR_CONFLICT
        else -> DoorPosition.UNKNOWN
    }

// endregion
