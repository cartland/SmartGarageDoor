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
import com.chriscartland.garage.data.NetworkFeatureAllowlistDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.FeatureAllowlist
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

class KtorNetworkFeatureAllowlistDataSource(
    private val client: HttpClient,
) : NetworkFeatureAllowlistDataSource {
    override suspend fun fetchAllowlist(idToken: String): NetworkResult<FeatureAllowlist> {
        return try {
            val response = client.get("functionListAccess") {
                header("X-AuthTokenGoogle", idToken)
            }
            if (!response.status.isSuccess()) {
                Logger.e { "Allowlist response code is ${response.status.value}" }
                return NetworkResult.HttpError(response.status.value)
            }
            val body = response.body<KtorFunctionListAccessResponse>()
            NetworkResult.Success(
                FeatureAllowlist(
                    functionList = body.enabled,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.e { "Error fetching feature allowlist: $e" }
            NetworkResult.ConnectionFailed
        }
    }
}

@Serializable
private data class KtorFunctionListAccessResponse(
    val enabled: Boolean = false,
)
