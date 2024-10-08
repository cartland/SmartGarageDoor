package com.chriscartland.garage.internet

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RemoteButtonPushResponse(
    @Json(name = "FIRESTORE_databaseTimestamp") val firestoreDatabaseTimestamp: FirestoreDatabaseTimestamp?,
    @Json(name = "buildTimestamp") val buildTimestamp: String?,
    @Json(name = "queryParams") val queryParams: QueryParams?,
    @Json(name = "session") val session: String?,
    @Json(name = "FIRESTORE_databaseTimestampSeconds") val firestoreDatabaseTimestampSeconds: Long?,
    @Json(name = "buttonAckToken") val buttonAckToken: String?,
    @Json(name = "body") val body: Any?, // Since the body is empty in this case, we use Any
    @Json(name = "email") val email: String?
) {
    @JsonClass(generateAdapter = true)
    data class FirestoreDatabaseTimestamp(
        @Json(name = "_seconds") val seconds: Long?,
        @Json(name = "_nanoseconds") val nanoseconds: Long?
    )

    @JsonClass(generateAdapter = true)
    data class QueryParams(
        @Json(name = "buildTimestamp") val buildTimestamp: String?,
        @Json(name = "buttonAckToken") val buttonAckToken: String?
    )
}
