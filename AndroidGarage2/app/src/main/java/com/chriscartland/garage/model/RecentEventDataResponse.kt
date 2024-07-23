package com.chriscartland.garage.model

// curl 'http://localhost:5001/escape-echo/us-central1/recentEventData?buildTimestamp=Sat%20Mar%2013%2014:45:00%202021&session=4f028c09-ebf9-49ef-bcf2-f661e2ec86b2' 
data class RecentEventDataResponse(
    val queryParams: Map<String, String>,
    val body: Any,
    val session: String,
    val buildTimestamp: String,
    val recentEventData: List<Event>
)
