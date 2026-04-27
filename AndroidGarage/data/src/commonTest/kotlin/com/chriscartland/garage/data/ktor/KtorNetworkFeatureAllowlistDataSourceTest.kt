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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Wire-contract tests for [KtorNetworkFeatureAllowlistDataSource].
 *
 * These tests are the Android half of the wire-contract lock for
 * `GET /functionListAccess`. Server-side bytes come from the canonical
 * fixtures in `wire-contracts/functionListAccess/` (loaded by
 * `HttpFunctionListAccessTest.ts`); this test feeds those exact same
 * bytes into a Ktor [MockEngine] and asserts the decoded model.
 *
 * **Why strict-mode JSON in tests when production uses
 * `ignoreUnknownKeys = true`:** production must be forward-compatible
 * with new keys the server adds (so a v2 server doesn't break v1
 * clients). But the tests should fail loudly when the server's *known*
 * keys disappear or get renamed. Strict-mode decoding here catches
 * exactly that case — a unilateral rename of `enabled` → `allowed`
 * would deserialize as `enabled = false` in production (silent deny)
 * but throws here.
 */
class KtorNetworkFeatureAllowlistDataSourceTest {
    // Test cwd is the data module's projectDir (`AndroidGarage/data/`); fixtures
    // live at the repository root. Path is verified by the readFixture call —
    // failure here means the wire-contracts directory moved and both sides
    // need updating in lockstep.
    private val fixtureDir = File("../../wire-contracts/functionListAccess")

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
                // Mirror production wire shape: tolerate unknown keys for
                // forward-compat. The strict-mode test below covers drift.
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
        val headers: Map<String, List<String>>,
    )

    @Test
    fun decodesEnabledTrueFixture() =
        runTest {
            val fixture = readFixture("response_enabled_true.json")
            val client = mockClient(HttpStatusCode.OK, fixture)
            val ds = KtorNetworkFeatureAllowlistDataSource(client)

            val result = ds.fetchAllowlist(idToken = "any")

            val success = assertIs<NetworkResult.Success<FeatureAllowlist>>(result)
            assertEquals(FeatureAllowlist(functionList = true), success.data)
        }

    @Test
    fun decodesEnabledFalseFixture() =
        runTest {
            val fixture = readFixture("response_enabled_false.json")
            val client = mockClient(HttpStatusCode.OK, fixture)
            val ds = KtorNetworkFeatureAllowlistDataSource(client)

            val result = ds.fetchAllowlist(idToken = "any")

            val success = assertIs<NetworkResult.Success<FeatureAllowlist>>(result)
            assertEquals(FeatureAllowlist(functionList = false), success.data)
        }

    /**
     * Wire-contract drift detector: strict-mode decoding fails if the
     * fixture's `enabled` key is renamed. Catches a unilateral server
     * rename that would otherwise silently deserialize as `false`.
     */
    @Test
    fun strictDecodeRejectsRenamedField() {
        val fixture = readFixture("response_enabled_true.json")
        val strict = Json { ignoreUnknownKeys = false }

        // First, sanity: the canonical fixture decodes under strict mode.
        // (If this assertion ever fails, the fixture itself drifted.)
        @Suppress("UNUSED_VARIABLE")
        val canonical = strict.decodeFromString(KtorFunctionListAccessResponseForTest.serializer(), fixture)

        // Now: an imagined renamed-field response fails strict-mode decode.
        // Both "missing field" and "unknown key" surface as
        // SerializationException subclasses — tests pin the parent so the
        // assertion survives kotlinx.serialization version churn.
        val drifted = """{"allowed":true}"""
        assertFailsWith<SerializationException> {
            strict.decodeFromString(KtorFunctionListAccessResponseForTest.serializer(), drifted)
        }
    }

    @Test
    fun sendsAuthTokenHeaderAndUsesGet() =
        runTest {
            val fixture = readFixture("response_enabled_true.json")
            val captured = mutableListOf<RecordedRequest>()
            val client = mockClient(HttpStatusCode.OK, fixture, captured)
            val ds = KtorNetworkFeatureAllowlistDataSource(client)

            ds.fetchAllowlist(idToken = "test-id-token-xyz")

            assertEquals(1, captured.size)
            assertEquals(HttpMethod.Get, captured[0].method)
            assertEquals("/functionListAccess", captured[0].urlPath)
            assertEquals(
                listOf("test-id-token-xyz"),
                captured[0].headers["X-AuthTokenGoogle"],
            )
        }

    @Test
    fun httpErrorMaps() =
        runTest {
            val client = mockClient(HttpStatusCode.Unauthorized, """{"error":"Unauthorized"}""")
            val ds = KtorNetworkFeatureAllowlistDataSource(client)

            val result = ds.fetchAllowlist(idToken = "any")

            val httpError = assertIs<NetworkResult.HttpError>(result)
            assertEquals(401, httpError.code)
        }
}

/**
 * Mirror of [KtorNetworkFeatureAllowlistDataSource]'s private response
 * type — duplicated here only so the strict-mode drift test can use the
 * @Serializable shape without changing visibility on the production
 * type. If the production response shape changes, update this mirror
 * to match.
 */
@kotlinx.serialization.Serializable
private data class KtorFunctionListAccessResponseForTest(
    val enabled: Boolean,
)
