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

import com.chriscartland.garage.door.DoorEvent
import com.chriscartland.garage.door.DoorPosition
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Response to this curl command:
// curl https://us-central1-escape-echo.cloudfunctions.net/currentEventData\?session\=\&buildTimestamp\=Sat%20Mar%2013%2014%3A45%3A00%202021
@JsonClass(generateAdapter = true)
data class CurrentEventDataResponse(
    val currentEventData: EventData?,
    val queryParams: QueryParams?,
    val session: String?,
    val buildTimestamp: String?, // Assuming this field is for the time the data was built
    val body: Map<String, Any>? = null, // Handle the empty "body" object
    @Json(name = "FIRESTORE_databaseTimestamp") val firestoreDatabaseTimestamp: FirestoreDatabaseTimestamp? = null,
) {
    @JsonClass(generateAdapter = true)
    data class QueryParams(
        val session: String?,
        val buildTimestamp: String?,
    )

    @JsonClass(generateAdapter = true)
    data class EventData(
        @Json(name = "currentEvent") val currentEvent: Event?,
        @Json(name = "previousEvent") val previousEvent: Event?,
        @Json(name = "FIRESTORE_databaseTimestamp") val firestoreDatabaseTimestamp: FirestoreDatabaseTimestamp?,
        val buildTimestamp: String? = null,
        @Json(name = "FIRESTORE_databaseTimestampSeconds") val firestoreDatabaseTimestampSeconds: Long?,
    )

    @JsonClass(generateAdapter = true)
    data class Event(
        val type: String?,
        val message: String?,
        @Json(name = "timestampSeconds") val timestampSeconds: Long?,
        @Json(name = "checkInTimestampSeconds") val checkInTimestampSeconds: Long?,
    ) {
        fun asDoorEvent(): DoorEvent? {
            val doorPosition =
                when (type) {
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

            return DoorEvent(
                doorPosition = doorPosition,
                message = message,
                lastCheckInTimeSeconds = checkInTimestampSeconds,
                lastChangeTimeSeconds = timestampSeconds,
            )
        }
    }

    @JsonClass(generateAdapter = true)
    data class FirestoreDatabaseTimestamp(
        @Json(name = "_seconds") val seconds: Long?,
        @Json(name = "_nanoseconds") val nanoseconds: Long?,
    )
}
