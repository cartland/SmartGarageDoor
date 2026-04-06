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
import com.chriscartland.garage.data.NetworkConfigDataSource
import com.chriscartland.garage.domain.model.ServerConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class KtorNetworkConfigDataSource(
    private val client: HttpClient,
) : NetworkConfigDataSource {
    override suspend fun fetchServerConfig(serverConfigKey: String): ServerConfig? {
        return try {
            val response = client.get("serverConfig") {
                header("X-ServerConfigKey", serverConfigKey)
            }
            if (!response.status.isSuccess()) {
                Logger.e { "Response code is ${response.status.value}" }
                return null
            }
            val result = response.body<KtorServerConfigResponse>()
            val body = result.body
            if (body == null) {
                Logger.e { "Response body is null" }
                return null
            }
            if (body.buildTimestamp.isNullOrEmpty()) {
                Logger.e { "buildTimestamp is empty" }
                return null
            }
            val remoteButtonBuildTimestamp = body.remoteButtonBuildTimestamp
            if (remoteButtonBuildTimestamp.isNullOrEmpty()) {
                Logger.e { "remoteButtonBuildTimestamp is empty" }
                return null
            }
            if (body.remoteButtonPushKey.isNullOrEmpty()) {
                Logger.e { "remoteButtonPushKey is empty" }
                return null
            }
            ServerConfig(
                buildTimestamp = body.buildTimestamp,
                remoteButtonBuildTimestamp = URLDecoder.decode(
                    remoteButtonBuildTimestamp,
                    StandardCharsets.UTF_8.name(),
                ),
                remoteButtonPushKey = body.remoteButtonPushKey,
            )
        } catch (e: Exception) {
            Logger.e { "Error fetching server config: $e" }
            null
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
