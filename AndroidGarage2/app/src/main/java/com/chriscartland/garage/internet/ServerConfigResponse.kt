package com.chriscartland.garage.internet

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Response to:
// curl -H "Content-Type: application/json" -H "X-ServerConfigKey: YourKey" https://us-central1-escape-echo.cloudfunctions.net/serverConfig
@JsonClass(generateAdapter = true)
data class ServerConfigResponse(
    @Json(name = "FIRESTORE_databaseTimestamp") val firestoreDatabaseTimestamp: FirestoreDatabaseTimestamp?,
    @Json(name = "FIRESTORE_databaseTimestampSeconds") val firestoreDatabaseTimestampSeconds: Long?,
    val body: Body?,
) {
    @JsonClass(generateAdapter = true)
    data class FirestoreDatabaseTimestamp(
        @Json(name = "_seconds") val seconds: Long?,
        @Json(name = "_nanoseconds") val nanoseconds: Long?,
    )

    @JsonClass(generateAdapter = true)
    data class Body(
        val buildTimestamp: String?,
        val deleteOldDataEnabledDryRun: Boolean?,
        val remoteButtonEnabled: Boolean?,
        val deleteOldDataEnabled: Boolean?,
        val path: String?, // Path of remote button command, e.g. "addRemoteButtonCommand"
        val remoteButtonPushKey: String?,
        val remoteButtonBuildTimestamp: String?,
        val host: String?,
        val remoteButtonAuthorizedEmails: List<String>?,
    )
}
