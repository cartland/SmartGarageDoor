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

import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.FeatureAllowlist
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
 * Wire-contract tests for [KtorNetworkFeatureAllowlistDataSource].
 *
 * These tests are the Android half of the wire-contract lock for both
 * `GET /functionListAccess` AND `GET /developerAccess`. The data source
 * issues both calls in parallel and combines the results into a single
 * [FeatureAllowlist]. Server-side bytes come from the canonical fixtures
 * in `wire-contracts/functionListAccess/` and `wire-contracts/developerAccess/`
 * (loaded by `HttpFunctionListAccessTest.ts` and `HttpDeveloperAccessTest.ts`);
 * this test feeds those exact same bytes into a Ktor [MockEngine] keyed by
 * URL path and asserts the decoded model.
 *
 * Drift detection: the happy-path tests decode the canonical fixtures
 * through the production data source. A unilateral server rename of
 * `enabled` would change the fixture (so the server's deep-equal test
 * fails), or fail to update the fixture (so the Android decoder reads
 * a default `false` and the per-endpoint assertion fails). Either way,
 * one of the two trees breaks.
 *
 * Portability: this test uses `java.io.File` to load fixtures from the
 * repo root (`../../wire-contracts/...`), which depends on the JVM
 * test runtime. Today the data module only declares `androidTarget()`,
 * so this is fine. If a non-JVM KMP target (iOS / JS / Desktop) is
 * added later, move this test to a JVM-only source set or copy
 * fixtures into module resources at Gradle config time.
 */
class KtorNetworkFeatureAllowlistDataSourceTest {
    // Test cwd is the data module's projectDir (`AndroidGarage/data/`); fixtures
    // live at the repository root. Path is verified by the readFixture call —
    // failure here means the wire-contracts directory moved and both sides
    // need updating in lockstep.
    private val functionListFixtureDir = File("../../wire-contracts/functionListAccess")
    private val developerFixtureDir = File("../../wire-contracts/developerAccess")

    private fun readFunctionListFixture(name: String): String = File(functionListFixtureDir, name).readText()

    private fun readDeveloperFixture(name: String): String = File(developerFixtureDir, name).readText()

    private fun mockClient(
        responses: Map<String, EndpointResponse>,
        capturedRequests: MutableList<RecordedRequest> = mutableListOf(),
    ): HttpClient =
        HttpClient(
            MockEngine { request ->
                capturedRequests.add(
                    RecordedRequest(
                        method = request.method,
                        urlPath = request.url.encodedPath,
                        headers = request.headers.entries().associate { it.key to it.value },
                    ),
                )
                val match = responses[request.url.encodedPath]
                    ?: error("No mock response configured for ${request.url.encodedPath}")
                respond(
                    content = ByteReadChannel(match.body),
                    status = match.status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) {
                // Mirror production wire shape: tolerate unknown keys for
                // forward-compat. Drift detection is the deep-equal of the
                // canonical fixture in the per-endpoint decode tests — a
                // server-side rename of `enabled` flips the decoded model
                // value to the default and the assertion fails.
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

    private data class EndpointResponse(
        val status: HttpStatusCode,
        val body: String,
    )

    private data class RecordedRequest(
        val method: HttpMethod,
        val urlPath: String,
        val headers: Map<String, List<String>>,
    )

    @Test
    fun decodesBothEndpointsEnabledTrue() =
        runTest {
            val client = mockClient(
                mapOf(
                    "/functionListAccess" to EndpointResponse(
                        HttpStatusCode.OK,
                        readFunctionListFixture("response_enabled_true.json"),
                    ),
                    "/developerAccess" to EndpointResponse(
                        HttpStatusCode.OK,
                        readDeveloperFixture("response_enabled_true.json"),
                    ),
                ),
            )
            val ds = KtorNetworkFeatureAllowlistDataSource(client)

            val result = ds.fetchAllowlist(idToken = "any")

            val success = assertIs<NetworkResult.Success<FeatureAllowlist>>(result)
            assertEquals(
                FeatureAllowlist(functionList = true, developer = true),
                success.data,
            )
        }

    @Test
    fun decodesBothEndpointsEnabledFalse() =
        runTest {
            val client = mockClient(
                mapOf(
                    "/functionListAccess" to EndpointResponse(
                        HttpStatusCode.OK,
                        readFunctionListFixture("response_enabled_false.json"),
                    ),
                    "/developerAccess" to EndpointResponse(
                        HttpStatusCode.OK,
                        readDeveloperFixture("response_enabled_false.json"),
                    ),
                ),
            )
            val ds = KtorNetworkFeatureAllowlistDataSource(client)

            val result = ds.fetchAllowlist(idToken = "any")

            val success = assertIs<NetworkResult.Success<FeatureAllowlist>>(result)
            assertEquals(
                FeatureAllowlist(functionList = false, developer = false),
                success.data,
            )
        }

    @Test
    fun decodesMixedEndpoints() =
        runTest {
            // The two endpoints are independent; the data source must combine
            // them faithfully rather than collapse to a single boolean.
            val client = mockClient(
                mapOf(
                    "/functionListAccess" to EndpointResponse(
                        HttpStatusCode.OK,
                        readFunctionListFixture("response_enabled_true.json"),
                    ),
                    "/developerAccess" to EndpointResponse(
                        HttpStatusCode.OK,
                        readDeveloperFixture("response_enabled_false.json"),
                    ),
                ),
            )
            val ds = KtorNetworkFeatureAllowlistDataSource(client)

            val result = ds.fetchAllowlist(idToken = "any")

            val success = assertIs<NetworkResult.Success<FeatureAllowlist>>(result)
            assertEquals(
                FeatureAllowlist(functionList = true, developer = false),
                success.data,
            )
        }

    @Test
    fun sendsAuthTokenHeaderAndUsesGetForBothEndpoints() =
        runTest {
            val captured = mutableListOf<RecordedRequest>()
            val client = mockClient(
                mapOf(
                    "/functionListAccess" to EndpointResponse(
                        HttpStatusCode.OK,
                        readFunctionListFixture("response_enabled_true.json"),
                    ),
                    "/developerAccess" to EndpointResponse(
                        HttpStatusCode.OK,
                        readDeveloperFixture("response_enabled_true.json"),
                    ),
                ),
                captured,
            )
            val ds = KtorNetworkFeatureAllowlistDataSource(client)

            ds.fetchAllowlist(idToken = "test-id-token-xyz")

            assertEquals(2, captured.size)
            // Order is non-deterministic (parallel async); check by path.
            val byPath = captured.associateBy { it.urlPath }
            val functionListReq = byPath.getValue("/functionListAccess")
            val developerReq = byPath.getValue("/developerAccess")
            assertEquals(HttpMethod.Get, functionListReq.method)
            assertEquals(HttpMethod.Get, developerReq.method)
            assertEquals(
                listOf("test-id-token-xyz"),
                functionListReq.headers["X-AuthTokenGoogle"],
            )
            assertEquals(
                listOf("test-id-token-xyz"),
                developerReq.headers["X-AuthTokenGoogle"],
            )
        }

    @Test
    fun httpErrorOnFunctionListMapsToHttpError() =
        runTest {
            val client = mockClient(
                mapOf(
                    "/functionListAccess" to EndpointResponse(
                        HttpStatusCode.Unauthorized,
                        """{"error":"Unauthorized"}""",
                    ),
                    "/developerAccess" to EndpointResponse(
                        HttpStatusCode.OK,
                        readDeveloperFixture("response_enabled_true.json"),
                    ),
                ),
            )
            val ds = KtorNetworkFeatureAllowlistDataSource(client)

            val result = ds.fetchAllowlist(idToken = "any")

            val httpError = assertIs<NetworkResult.HttpError>(result)
            assertEquals(401, httpError.code)
        }

    @Test
    fun httpErrorOnDeveloperEndpointMapsToHttpError() =
        runTest {
            val client = mockClient(
                mapOf(
                    "/functionListAccess" to EndpointResponse(
                        HttpStatusCode.OK,
                        readFunctionListFixture("response_enabled_true.json"),
                    ),
                    "/developerAccess" to EndpointResponse(
                        HttpStatusCode.Unauthorized,
                        """{"error":"Unauthorized"}""",
                    ),
                ),
            )
            val ds = KtorNetworkFeatureAllowlistDataSource(client)

            val result = ds.fetchAllowlist(idToken = "any")

            val httpError = assertIs<NetworkResult.HttpError>(result)
            assertEquals(401, httpError.code)
        }
}
