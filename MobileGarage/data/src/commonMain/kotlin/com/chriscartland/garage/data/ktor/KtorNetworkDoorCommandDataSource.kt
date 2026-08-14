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
import com.chriscartland.garage.data.NetworkDoorCommandDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.DoorCommandRejection
import com.chriscartland.garage.domain.model.DoorCommandVerdict
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Forward-compatible on purpose: `ignoreUnknownKeys` so a server that starts
 * reporting more about its decision does not break a shipped client. The strict
 * counterpart lives in the tests, which decode the same
 * `wire-contracts/doorCommand/` fixtures with unknown keys REJECTED, so a
 * renamed field fails there instead of silently defaulting here.
 */
private val doorCommandJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Serializable
internal data class KtorDoorCommandResponse(
    @SerialName("verdict") val verdict: KtorDoorCommandVerdict? = null,
    @SerialName("executed") val executed: Boolean = false,
)

@Serializable
internal data class KtorDoorCommandVerdict(
    @SerialName("accepted") val accepted: Boolean = false,
    @SerialName("rejection") val rejection: String? = null,
    @SerialName("doorState") val doorState: String? = null,
    @SerialName("checkInStale") val checkInStale: Boolean = false,
)

class KtorNetworkDoorCommandDataSource(
    private val client: HttpClient,
) : NetworkDoorCommandDataSource {
    override suspend fun checkDoorCommand(
        command: String,
        remoteButtonPushKey: String,
        idToken: String,
    ): NetworkResult<DoorCommandVerdict> =
        try {
            val response = client.post("doorCommand") {
                contentType(ContentType.Application.Json)
                header("X-RemoteButtonPushKey", remoteButtonPushKey)
                header("X-AuthTokenGoogle", idToken)
                setBody("""{"command":"$command"}""")
            }
            Logger.i { "doorCommand response: ${response.status.value}" }
            if (!response.status.isSuccess()) {
                NetworkResult.HttpError(response.status.value)
            } else {
                // Read as text first so the raw body is in the log for on-device
                // diagnosis — the same treatment the snooze decode gets, and for
                // the same reason: this decides whether a door command proceeds.
                val rawBody: String = response.body()
                val parsed = doorCommandJson.decodeFromString(
                    KtorDoorCommandResponse.serializer(),
                    rawBody,
                )
                val verdict = parsed.verdict
                if (verdict == null) {
                    Logger.e { "doorCommand: no verdict in body: $rawBody" }
                    NetworkResult.HttpError(response.status.value)
                } else {
                    NetworkResult.Success(
                        DoorCommandVerdict(
                            accepted = verdict.accepted,
                            rejection = parseRejection(verdict.rejection),
                            doorState = verdict.doorState ?: "UNKNOWN",
                            checkInStale = verdict.checkInStale,
                        ),
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.e { "doorCommand error: $e" }
            NetworkResult.ConnectionFailed
        }

    companion object {
        /**
         * A rejection this build does not recognize maps to
         * [DoorCommandRejection.UNRECOGNIZED], which still refuses. An
         * `accepted:true` verdict never carries one, so the only way to reach
         * here is a server that refused for a reason we cannot word — and
         * "I do not understand why you said no" is not permission.
         */
        internal fun parseRejection(raw: String?): DoorCommandRejection? {
            if (raw == null) return null
            return DoorCommandRejection.entries.firstOrNull { it.name == raw }
                ?: DoorCommandRejection.UNRECOGNIZED
        }
    }
}
