package com.chriscartland.garage.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Response to this curl command:
// curl https://us-central1-escape-echo.cloudfunctions.net/currentEventData\?session\=\&buildTimestamp\=Sat%20Mar%2013%2014%3A45%3A00%202021
@JsonClass(generateAdapter = true)
data class CurrentEventDataResponse(
    val queryParams: QueryParams?,
    val session: String?,
    val buildTimestamp: String?, // Assuming this field is for the time the data was built
    val currentEventData: EventData?,
    val body: Map<String, Any>? = null, // Handle the empty "body" object
    @Json(name = "FIRESTORE_databaseTimestamp") val firestoreDatabaseTimestamp: FirestoreDatabaseTimestamp? = null
)

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
        val doorPosition = when (type) {
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
            lastChangeTimeSeconds = timestampSeconds
        )
    }
}

@JsonClass(generateAdapter = true)
data class FirestoreDatabaseTimestamp(
    @Json(name = "_seconds") val seconds: Long?,
    @Json(name = "_nanoseconds") val nanoseconds: Long?,
)
