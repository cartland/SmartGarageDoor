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
import com.chriscartland.garage.domain.model.DoorEventPage
import com.chriscartland.garage.domain.model.DoorPosition
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
import kotlin.test.assertNull

/**
 * Wire-contract tests for [KtorNetworkDoorDataSource]'s `GET /eventHistory`.
 *
 * The Android half of the wire-contract lock: the canonical bytes come from
 * `wire-contracts/eventHistory/` (also loaded by the server's `HttpEventsTest.ts`
 * via `deep.equal`). This test feeds the same bytes into a Ktor [MockEngine] and
 * asserts the decoded [DoorEventPage]. A unilateral server rename (e.g.
 * `nextPageToken` → `cursor`) flips a decoded value here and fails the test.
 *
 * The eventHistory response carries echo fields (queryParams, session, …) the
 * client intentionally ignores, so this uses production-shape lenient decoding
 * and asserts the meaningful fields rather than a strict whole-object decode.
 */
class KtorNetworkDoorDataSourceTest {
    private val fixtureDir = File("../../wire-contracts/eventHistory")

    private fun readFixture(name: String): String = File(fixtureDir, name).readText()

    private val firstPageNextToken =
        "eyJ2IjoxLCJidCI6IlNhdCBNYXIgMTMgMTQ6NDU6MDAgMjAyMSIsInMiOjE2OTk5OTgwMDAsIm4iOjAsImQiOiJvbGRlciJ9"
    private val lastPagePrevToken =
        "eyJ2IjoxLCJidCI6IlNhdCBNYXIgMTMgMTQ6NDU6MDAgMjAyMSIsInMiOjE2OTkwMDAwMDAsIm4iOjAsImQiOiJuZXdlciJ9"

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
    )

    @Test
    fun decodesFirstPageWithNextToken() =
        runTest {
            val client = mockClient(HttpStatusCode.OK, readFixture("response_first_page.json"))
            val ds = KtorNetworkDoorDataSource(client)

            val result = ds.fetchDoorEventPage(buildTimestamp = "bt", pageSize = 50, pageToken = null)

            val success = assertIs<NetworkResult.Success<DoorEventPage>>(result)
            assertEquals(2, success.data.events.size)
            assertEquals(DoorPosition.OPEN, success.data.events[0].doorPosition)
            assertEquals(1699999000L, success.data.events[0].lastChangeTimeSeconds)
            assertEquals(DoorPosition.CLOSED, success.data.events[1].doorPosition)
            assertEquals(firstPageNextToken, success.data.nextPageToken)
            assertNull(success.data.prevPageToken)
            assertEquals(true, success.data.hasMore)
        }

    @Test
    fun decodesLastPageWithNoNextToken() =
        runTest {
            val client = mockClient(HttpStatusCode.OK, readFixture("response_last_page.json"))
            val ds = KtorNetworkDoorDataSource(client)

            val result = ds.fetchDoorEventPage(buildTimestamp = "bt", pageSize = 50, pageToken = firstPageNextToken)

            val success = assertIs<NetworkResult.Success<DoorEventPage>>(result)
            assertEquals(1, success.data.events.size)
            assertEquals(DoorPosition.OPENING, success.data.events[0].doorPosition)
            assertNull(success.data.nextPageToken)
            assertEquals(lastPagePrevToken, success.data.prevPageToken)
            assertEquals(false, success.data.hasMore)
        }

    @Test
    fun decodesEmptyPage() =
        runTest {
            val client = mockClient(HttpStatusCode.OK, readFixture("response_empty.json"))
            val ds = KtorNetworkDoorDataSource(client)

            val result = ds.fetchDoorEventPage(buildTimestamp = "bt", pageSize = 50, pageToken = null)

            val success = assertIs<NetworkResult.Success<DoorEventPage>>(result)
            assertEquals(0, success.data.events.size)
            assertNull(success.data.nextPageToken)
            assertNull(success.data.prevPageToken)
            assertEquals(false, success.data.hasMore)
        }

    @Test
    fun sendsGetWithPageSizeLegacyCountAndToken() =
        runTest {
            val captured = mutableListOf<RecordedRequest>()
            val client = mockClient(HttpStatusCode.OK, readFixture("response_first_page.json"), captured)
            val ds = KtorNetworkDoorDataSource(client)

            ds.fetchDoorEventPage(buildTimestamp = "build-123", pageSize = 50, pageToken = "tok-abc")

            assertEquals(1, captured.size)
            assertEquals(HttpMethod.Get, captured[0].method)
            assertEquals("/eventHistory", captured[0].urlPath)
            assertEquals(listOf("build-123"), captured[0].urlQuery["buildTimestamp"])
            assertEquals(listOf("50"), captured[0].urlQuery["pageSize"])
            // Legacy alias so a pre-pagination server still applies the limit.
            assertEquals(listOf("50"), captured[0].urlQuery["eventHistoryMaxCount"])
            assertEquals(listOf("tok-abc"), captured[0].urlQuery["pageToken"])
        }

    @Test
    fun omitsPageTokenOnFirstPage() =
        runTest {
            val captured = mutableListOf<RecordedRequest>()
            val client = mockClient(HttpStatusCode.OK, readFixture("response_first_page.json"), captured)
            val ds = KtorNetworkDoorDataSource(client)

            ds.fetchDoorEventPage(buildTimestamp = "build-123", pageSize = 50, pageToken = null)

            assertNull(captured[0].urlQuery["pageToken"])
        }

    @Test
    fun mapsHttp500ToHttpError() =
        runTest {
            val client = mockClient(HttpStatusCode.InternalServerError, "{}")
            val ds = KtorNetworkDoorDataSource(client)

            val result = ds.fetchDoorEventPage(buildTimestamp = "bt", pageSize = 50, pageToken = null)

            val httpError = assertIs<NetworkResult.HttpError>(result)
            assertEquals(500, httpError.code)
        }
}
