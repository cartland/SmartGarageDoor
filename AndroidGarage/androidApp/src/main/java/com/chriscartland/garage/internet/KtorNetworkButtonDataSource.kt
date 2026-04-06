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
import com.chriscartland.garage.data.NetworkButtonDataSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

class KtorNetworkButtonDataSource(
    private val client: HttpClient,
) : NetworkButtonDataSource {
    override suspend fun pushButton(
        remoteButtonBuildTimestamp: String,
        buttonAckToken: String,
        remoteButtonPushKey: String,
        idToken: String,
    ): Boolean =
        try {
            val response = client.post("addRemoteButtonCommand") {
                parameter("buildTimestamp", remoteButtonBuildTimestamp)
                parameter("buttonAckToken", buttonAckToken)
                header("X-RemoteButtonPushKey", remoteButtonPushKey)
                header("X-AuthTokenGoogle", idToken)
            }
            Logger.i { "Push response: ${response.status.value}" }
            response.status.isSuccess()
        } catch (e: Exception) {
            Logger.e { "Push error: $e" }
            false
        }

    override suspend fun snoozeNotifications(
        buildTimestamp: String,
        remoteButtonPushKey: String,
        idToken: String,
        snoozeDurationHours: String,
        snoozeEventTimestampSeconds: Long,
    ): Boolean {
        return try {
            val response = client.post("snoozeNotificationsRequest") {
                parameter("buildTimestamp", buildTimestamp)
                header("X-RemoteButtonPushKey", remoteButtonPushKey)
                header("X-AuthTokenGoogle", idToken)
                parameter("snoozeDuration", snoozeDurationHours)
                parameter("snoozeEventTimestamp", snoozeEventTimestampSeconds)
            }
            Logger.i { "Snooze response: ${response.status.value}" }
            if (!response.status.isSuccess()) {
                return false
            }
            val body = response.body<KtorSnoozeResponse>()
            if (body.error != null) {
                Logger.e { "Snooze error: ${body.error}" }
                return false
            }
            true
        } catch (e: Exception) {
            Logger.e { "Snooze error: $e" }
            false
        }
    }

    override suspend fun fetchSnoozeEndTimeSeconds(buildTimestamp: String): Long {
        return try {
            val response = client.get("snoozeNotificationsLatest") {
                parameter("buildTimestamp", buildTimestamp)
            }
            val body = response.body<KtorGetSnoozeResponse>()
            if (body.error != null) {
                Logger.e { "Snooze fetch error: ${body.error}" }
                return 0L
            }
            body.snooze?.snoozeEndTimeSeconds ?: 0L
        } catch (e: Exception) {
            Logger.e { "Snooze fetch error: $e" }
            0L
        }
    }
}

// region Serializable response types

@Serializable
private data class KtorSnoozeResponse(
    val error: String? = null,
)

@Serializable
private data class KtorGetSnoozeResponse(
    val snooze: KtorSnooze? = null,
    val error: String? = null,
) {
    @Serializable
    data class KtorSnooze(
        val snoozeEndTimeSeconds: Long? = null,
    )
}

// endregion
