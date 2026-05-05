/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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
import com.chriscartland.garage.data.NetworkButtonHealthDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.ButtonHealth
import com.chriscartland.garage.domain.model.ButtonHealthState
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class KtorNetworkButtonHealthDataSource(
    private val client: HttpClient,
) : NetworkButtonHealthDataSource {
    override suspend fun fetchButtonHealth(
        buildTimestamp: String,
        remoteButtonPushKey: String,
        idToken: String,
    ): NetworkResult<ButtonHealth> {
        return try {
            val response = client.get("buttonHealth") {
                parameter("buildTimestamp", buildTimestamp)
                header("X-RemoteButtonPushKey", remoteButtonPushKey)
                header("X-AuthTokenGoogle", idToken)
            }
            if (!response.status.isSuccess()) {
                Logger.e { "Button health response code is ${response.status.value}" }
                return NetworkResult.HttpError(response.status.value)
            }
            val body = response.body<KtorButtonHealthResponse>()
            NetworkResult.Success(body.toButtonHealth())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.e { "Error fetching button health: $e" }
            NetworkResult.ConnectionFailed
        }
    }
}

@Serializable
private data class KtorButtonHealthResponse(
    @SerialName("buttonState") val buttonState: String,
    @SerialName("stateChangedAtSeconds") val stateChangedAtSeconds: Long? = null,
    // buildTimestamp echoed by server but not consumed here (already known to caller).
    @SerialName("buildTimestamp") val buildTimestamp: String? = null,
) {
    fun toButtonHealth(): ButtonHealth =
        ButtonHealth(
            state = buttonState.toButtonHealthState(),
            stateChangedAtSeconds = stateChangedAtSeconds,
        )
}

/**
 * Forward-compat: unrecognized server-side state strings (e.g. a future
 * `MAINTENANCE`) deserialize to [ButtonHealthState.UNKNOWN] rather than
 * throwing. Old clients keep working when the server adds new states.
 */
private fun String.toButtonHealthState(): ButtonHealthState =
    when (this) {
        "ONLINE" -> ButtonHealthState.ONLINE
        "OFFLINE" -> ButtonHealthState.OFFLINE
        "UNKNOWN" -> ButtonHealthState.UNKNOWN
        else -> ButtonHealthState.UNKNOWN
    }
