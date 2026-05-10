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

import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.ButtonHealth
import com.chriscartland.garage.domain.model.ButtonHealthState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Wire-contract tests for [KtorNetworkButtonHealthDataSource].
 *
 * These tests are the Android half of the wire-contract lock for
 * `GET /buttonHealth`. Server-side bytes come from the canonical
 * fixtures in `wire-contracts/buttonHealth/` (loaded by the server's
 * `ButtonHealthTest.ts`); this test feeds the same bytes into a Ktor
 * [MockEngine] and asserts the decoded model.
 *
 * Drift detection: a unilateral server rename (e.g. `buttonState` →
 * `state`) breaks at least one side of the wire-contract test.
 *
 * Portability: this test uses `java.io.File` to load fixtures from the
 * repo root. Today the data module only declares `androidTarget()`,
 * so this is fine. If a non-JVM KMP target is added later, move this
 * test to a JVM-only source set or copy fixtures into module resources.
 */
class KtorButtonHealthDataSourceTest {
    private val fixtureDir = File("../../wire-contracts/buttonHealth")

    private fun readFixture(name: String): String = File(fixtureDir, name).readText()

    private fun mockClient(
        statusCode: HttpStatusCode,
        responseBody: String,
        capturedRequests: MutableList<RecordedRequest> = mutableListOf(),
    ): HttpClient =
        HttpClient(
            MockEngine { request ->
                capturedRequests.add(
                    RecordedRequest(
                        method = request.method,
                        urlPath = request.url.encodedPath,
                        urlQuery = request.url.parameters
                            .entries()
                            .associate { it.key to it.value },
                        headers = request.headers.entries().associate { it.key to it.value },
                    ),
                )
                respond(
                    content = ByteReadChannel(responseBody),
                    status = statusCode,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) {
                // Mirror production wire shape: ignore unknown keys for forward-compat.
                // Drift detection is the deep-equal of decoded values against the canonical
                // fixtures below — a server-side rename flips the decoded model.
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    },
                )
            }
            defaultRequest {
                url(URLBuilder("https://example.invalid/").build().toString())
            }
        }

    private data class RecordedRequest(
        val method: HttpMethod,
        val urlPath: String,
        val urlQuery: Map<String, List<String>>,
        val headers: Map<String, List<String>>,
    )

    @Test
    fun decodesOnlineFixture() =
        runTest {
            val fixture = readFixture("response_online.json")
            val client = mockClient(HttpStatusCode.OK, fixture)
            val ds = KtorNetworkButtonHealthDataSource(client)

            val result = ds.fetchButtonHealth(
                buildTimestamp = "Sat Apr 10 23:57:32 2021",
                remoteButtonPushKey = "key",
                idToken = "token",
            )

            val success = assertIs<NetworkResult.Success<ButtonHealth>>(result)
            assertEquals(ButtonHealthState.ONLINE, success.data.state)
            assertEquals(1730000000L, success.data.stateChangedAtSeconds)
            assertEquals(1730000500L, success.data.lastPollAtSeconds)
        }

    @Test
    fun decodesOfflineFixture() =
        runTest {
            val fixture = readFixture("response_offline.json")
            val client = mockClient(HttpStatusCode.OK, fixture)
            val ds = KtorNetworkButtonHealthDataSource(client)

            val result = ds.fetchButtonHealth(
                buildTimestamp = "Sat Apr 10 23:57:32 2021",
                remoteButtonPushKey = "key",
                idToken = "token",
            )

            val success = assertIs<NetworkResult.Success<ButtonHealth>>(result)
            assertEquals(ButtonHealthState.OFFLINE, success.data.state)
            assertEquals(1730000000L, success.data.stateChangedAtSeconds)
            assertEquals(1729999700L, success.data.lastPollAtSeconds)
        }

    @Test
    fun decodesUnknownFixture() =
        runTest {
            // UNKNOWN appears on the wire only when no buttonHealthCurrent doc exists yet.
            // stateChangedAtSeconds and lastPollAtSeconds are both null in that case.
            val fixture = readFixture("response_unknown.json")
            val client = mockClient(HttpStatusCode.OK, fixture)
            val ds = KtorNetworkButtonHealthDataSource(client)

            val result = ds.fetchButtonHealth(
                buildTimestamp = "Sat Apr 10 23:57:32 2021",
                remoteButtonPushKey = "key",
                idToken = "token",
            )

            val success = assertIs<NetworkResult.Success<ButtonHealth>>(result)
            assertEquals(ButtonHealthState.UNKNOWN, success.data.state)
            assertEquals(null, success.data.stateChangedAtSeconds)
            assertEquals(null, success.data.lastPollAtSeconds)
        }

    @Test
    fun sendsAuthHeadersAndQueryAndUsesGet() =
        runTest {
            val fixture = readFixture("response_online.json")
            val captured = mutableListOf<RecordedRequest>()
            val client = mockClient(HttpStatusCode.OK, fixture, captured)
            val ds = KtorNetworkButtonHealthDataSource(client)

            ds.fetchButtonHealth(
                buildTimestamp = "Sat Apr 10 23:57:32 2021",
                remoteButtonPushKey = "test-push-key",
                idToken = "test-id-token-xyz",
            )

            assertEquals(1, captured.size)
            assertEquals(HttpMethod.Get, captured[0].method)
            assertEquals("/buttonHealth", captured[0].urlPath)
            assertEquals(
                listOf("Sat Apr 10 23:57:32 2021"),
                captured[0].urlQuery["buildTimestamp"],
            )
            assertEquals(
                listOf("test-push-key"),
                captured[0].headers["X-RemoteButtonPushKey"],
            )
            assertEquals(
                listOf("test-id-token-xyz"),
                captured[0].headers["X-AuthTokenGoogle"],
            )
        }

    @Test
    fun mapsHttp401ToHttpError() =
        runTest {
            val fixture = readFixture("response_unauthorized.json")
            val client = mockClient(HttpStatusCode.Unauthorized, fixture)
            val ds = KtorNetworkButtonHealthDataSource(client)

            val result = ds.fetchButtonHealth(
                buildTimestamp = "any",
                remoteButtonPushKey = "key",
                idToken = "token",
            )

            val httpError = assertIs<NetworkResult.HttpError>(result)
            assertEquals(401, httpError.code)
        }

    @Test
    fun mapsHttp403ToHttpError() =
        runTest {
            val fixture = readFixture("response_forbidden_user.json")
            val client = mockClient(HttpStatusCode.Forbidden, fixture)
            val ds = KtorNetworkButtonHealthDataSource(client)

            val result = ds.fetchButtonHealth(
                buildTimestamp = "any",
                remoteButtonPushKey = "key",
                idToken = "token",
            )

            val httpError = assertIs<NetworkResult.HttpError>(result)
            assertEquals(403, httpError.code)
        }
}
