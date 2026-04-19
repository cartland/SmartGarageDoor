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

package com.chriscartland.garage.data.ktor

import co.touchlab.kermit.Logger
import com.chriscartland.garage.data.NetworkButtonDataSource
import com.chriscartland.garage.data.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val snoozeSubmitJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

class KtorNetworkButtonDataSource(
    private val client: HttpClient,
) : NetworkButtonDataSource {
    override suspend fun pushButton(
        remoteButtonBuildTimestamp: String,
        buttonAckToken: String,
        remoteButtonPushKey: String,
        idToken: String,
    ): NetworkResult<Unit> =
        try {
            val response = client.post("addRemoteButtonCommand") {
                parameter("buildTimestamp", remoteButtonBuildTimestamp)
                parameter("buttonAckToken", buttonAckToken)
                header("X-RemoteButtonPushKey", remoteButtonPushKey)
                header("X-AuthTokenGoogle", idToken)
            }
            Logger.i { "Push response: ${response.status.value}" }
            if (response.status.isSuccess()) {
                NetworkResult.Success(Unit)
            } else {
                NetworkResult.HttpError(response.status.value)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.e { "Push error: $e" }
            NetworkResult.ConnectionFailed
        }

    override suspend fun snoozeNotifications(
        buildTimestamp: String,
        remoteButtonPushKey: String,
        idToken: String,
        snoozeDurationHours: String,
        snoozeEventTimestampSeconds: Long,
    ): NetworkResult<Long> {
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
                return NetworkResult.HttpError(response.status.value)
            }
            // Server returns the SnoozeRequest object directly (see
            // FirebaseServer/src/functions/http/Snooze.ts — it sends the
            // snooze, not a wrapper). The authoritative end time is here.
            // Read the body once as text so we can log it verbatim for
            // on-device diagnosis, then parse from string.
            val rawBody: String = response.body()
            val body = snoozeSubmitJson.decodeFromString(
                KtorSnoozeSubmitResponse.serializer(),
                rawBody,
            )
            if (body.error != null) {
                Logger.e { "Snooze error: ${body.error}" }
                return NetworkResult.HttpError(response.status.value)
            }
            val endTime = body.snoozeEndTimeSeconds ?: 0L
            Logger.i { "Snooze POST parsed endTime=$endTime rawBody=$rawBody" }
            NetworkResult.Success(endTime)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.e { "Snooze error: $e" }
            NetworkResult.ConnectionFailed
        }
    }

    override suspend fun fetchSnoozeEndTimeSeconds(buildTimestamp: String): NetworkResult<Long> {
        return try {
            val response = client.get("snoozeNotificationsLatest") {
                parameter("buildTimestamp", buildTimestamp)
            }
            if (!response.status.isSuccess()) {
                return NetworkResult.HttpError(response.status.value)
            }
            val body = response.body<KtorGetSnoozeResponse>()
            if (body.error != null) {
                Logger.e { "Snooze fetch error: ${body.error}" }
                return NetworkResult.HttpError(response.status.value)
            }
            NetworkResult.Success(body.snooze?.snoozeEndTimeSeconds ?: 0L)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.e { "Snooze fetch error: $e" }
            NetworkResult.ConnectionFailed
        }
    }
}

// region Serializable response types

/**
 * Server response for POST /snoozeNotificationsRequest. The server sends the
 * `SnoozeRequest` object at the top level (not nested under `snooze`), so
 * `snoozeEndTimeSeconds` is a direct field here.
 */
@Serializable
private data class KtorSnoozeSubmitResponse(
    val snoozeEndTimeSeconds: Long? = null,
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
