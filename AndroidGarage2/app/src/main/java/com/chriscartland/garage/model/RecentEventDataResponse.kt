package com.chriscartland.garage.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass


// curl 'http://localhost:5001/escape-echo/us-central1/eventHistory?buildTimestamp=Sat%20Mar%2013%2014:45:00%202021&session=4f028c09-ebf9-49ef-bcf2-f661e2ec86b2'
@JsonClass(generateAdapter = true)
data class RecentEventDataResponse(
    val eventHistory: List<EventData>?,
    val eventHistoryCount: Int?,
    val queryParams: QueryParams?,
    val session: String?,
    val buildTimestamp: String?, // Assuming this field is for the time the data was built
    val body: Map<String, Any>? = null, // Handle the empty "body" object
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