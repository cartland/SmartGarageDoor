package com.chriscartland.garage.internet

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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
        @Json(name = "remoteButtonBuildTimestamp")
        val _remoteButtonBuildTimestamp: String?,
        // Add a new property to hold the decoded value
        val host: String?,
        val remoteButtonAuthorizedEmails: List<String>?,
    ) {
        /**
         * The server accidentally is storing a URL encoded value.
         * This is encoded again and sent over HTTP, and decoded once.
         * We need to decode the value again.
         * Implementing a private backing field for the value from the server
         * so that usage of this data class can use the decoded value.
         */
        val remoteButtonBuildTimestamp: String?
            get() = _remoteButtonBuildTimestamp?.let {
                URLDecoder.decode(it, StandardCharsets.UTF_8.name())
            }
    }
}
