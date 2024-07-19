package com.chriscartland.garage.model

// curl 'http://localhost:5001/escape-echo/us-central1/recentEventData?buildTimestamp=Sat%20Mar%2013%2014:45:00%202021&session=4f028c09-ebf9-49ef-bcf2-f661e2ec86b2' 
data class RecentEventDataResponse(
    val queryParams: Map<String, String>,
    val body: Any,
    val session: String,
    val buildTimestamp: String,
    val recentEventData: List<EventData>
)

data class EventData(
    val currentEvent: EventDetails,
    val previousEvent: EventDetails?,
    val FIRESTORE_databaseTimestamp: Timestamp,
    val buildTimestamp: String,
    val FIRESTORE_databaseTimestampSeconds: Long
)

data class EventDetails(
    val type: String,
    val message: String,
    val timestampSeconds: Long,
    val checkInTimestampSeconds: Long
)

data class Timestamp(
    val _seconds: Long,
    val _nanoseconds: Int
)
