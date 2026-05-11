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
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable

class KtorNetworkFeatureAllowlistDataSource(
    private val client: HttpClient,
) : NetworkFeatureAllowlistDataSource {
    /**
     * Two independent server endpoints (`functionListAccess` and
     * `developerAccess`). Calls are issued in parallel via `coroutineScope`
     * + `async`. One-fail-all semantics: if either returns a non-2xx status
     * the whole fetch maps to [NetworkResult.HttpError]; if either throws
     * we map to [NetworkResult.ConnectionFailed]. Partial success would
     * leave the cache in a confusing half-state, so we treat the pair as
     * one transaction.
     */
    override suspend fun fetchAllowlist(idToken: String): NetworkResult<FeatureAllowlist> {
        return try {
            coroutineScope {
                val functionListAsync = async {
                    client.get("functionListAccess") {
                        header("X-AuthTokenGoogle", idToken)
                    }
                }
                val developerAsync = async {
                    client.get("developerAccess") {
                        header("X-AuthTokenGoogle", idToken)
                    }
                }
                val functionListResponse: HttpResponse = functionListAsync.await()
                val developerResponse: HttpResponse = developerAsync.await()

                if (!functionListResponse.status.isSuccess()) {
                    Logger.e { "functionListAccess response code is ${functionListResponse.status.value}" }
                    return@coroutineScope NetworkResult.HttpError(functionListResponse.status.value)
                }
                if (!developerResponse.status.isSuccess()) {
                    Logger.e { "developerAccess response code is ${developerResponse.status.value}" }
                    return@coroutineScope NetworkResult.HttpError(developerResponse.status.value)
                }
                val functionListBody = functionListResponse.body<KtorAccessResponse>()
                val developerBody = developerResponse.body<KtorAccessResponse>()
                NetworkResult.Success(
                    FeatureAllowlist(
                        functionList = functionListBody.enabled,
                        developer = developerBody.enabled,
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.e { "Error fetching feature allowlist: $e" }
            NetworkResult.ConnectionFailed
        }
    }
}

@Serializable
private data class KtorAccessResponse(
    val enabled: Boolean = false,
)
