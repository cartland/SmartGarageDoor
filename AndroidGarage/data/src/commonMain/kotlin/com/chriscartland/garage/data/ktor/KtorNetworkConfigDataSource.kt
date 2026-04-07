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
import com.chriscartland.garage.data.NetworkConfigDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.ServerConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class KtorNetworkConfigDataSource(
    private val client: HttpClient,
) : NetworkConfigDataSource {
    override suspend fun fetchServerConfig(serverConfigKey: String): NetworkResult<ServerConfig> {
        return try {
            val response = client.get("serverConfig") {
                header("X-ServerConfigKey", serverConfigKey)
            }
            if (!response.status.isSuccess()) {
                Logger.e { "Response code is ${response.status.value}" }
                return NetworkResult.HttpError(response.status.value)
            }
            val result = response.body<KtorServerConfigResponse>()
            val body = result.body
            if (body == null) {
                Logger.e { "Response body is null" }
                return NetworkResult.HttpError(response.status.value)
            }
            if (body.buildTimestamp.isNullOrEmpty()) {
                Logger.e { "buildTimestamp is empty" }
                return NetworkResult.HttpError(response.status.value)
            }
            val remoteButtonBuildTimestamp = body.remoteButtonBuildTimestamp
            if (remoteButtonBuildTimestamp.isNullOrEmpty()) {
                Logger.e { "remoteButtonBuildTimestamp is empty" }
                return NetworkResult.HttpError(response.status.value)
            }
            if (body.remoteButtonPushKey.isNullOrEmpty()) {
                Logger.e { "remoteButtonPushKey is empty" }
                return NetworkResult.HttpError(response.status.value)
            }
            NetworkResult.Success(
                ServerConfig(
                    buildTimestamp = body.buildTimestamp,
                    remoteButtonBuildTimestamp = percentDecode(remoteButtonBuildTimestamp),
                    remoteButtonPushKey = body.remoteButtonPushKey,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.e { "Error fetching server config: $e" }
            NetworkResult.ConnectionFailed
        }
    }
}

// region Serializable response types

@Serializable
private data class KtorServerConfigResponse(
    val body: KtorServerConfigBody? = null,
)

@Serializable
private data class KtorServerConfigBody(
    val buildTimestamp: String? = null,
    @SerialName("remoteButtonBuildTimestamp") val remoteButtonBuildTimestamp: String? = null,
    val remoteButtonPushKey: String? = null,
)

// endregion

/**
 * KMP-compatible percent-decode (replaces java.net.URLDecoder).
 */
private fun percentDecode(encoded: String): String =
    buildString {
        var i = 0
        while (i < encoded.length) {
            if (encoded[i] == '%' && i + 2 < encoded.length) {
                val hex = encoded.substring(i + 1, i + 3)
                val code = hex.toIntOrNull(16)
                if (code != null) {
                    append(code.toChar())
                    i += 3
                    continue
                }
            }
            if (encoded[i] == '+') {
                append(' ')
            } else {
                append(encoded[i])
            }
            i++
        }
    }
