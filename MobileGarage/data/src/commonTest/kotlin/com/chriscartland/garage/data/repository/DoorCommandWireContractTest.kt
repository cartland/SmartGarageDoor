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

package com.chriscartland.garage.data.repository

import com.chriscartland.garage.data.NetworkDoorCommandDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.data.ktor.KtorNetworkDoorCommandDataSource
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.DoorCommandRejection
import com.chriscartland.garage.domain.model.DoorCommandVerdict
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.ServerConfig
import com.chriscartland.garage.domain.model.VoiceIntent
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeNetworkConfigDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What the client SENDS to `doorCommand`, and what it understands of the answer.
 *
 * ## Why this file exists
 *
 * The watch deliberately does not judge check-in staleness before speaking a
 * command — it passes `isCheckInStale = false` and lets the server decide,
 * because the server holds the authoritative door state and the authoritative
 * check-in (see `LiveVoiceDoor`'s KDoc on the Wear side). That division is only
 * sound on one condition: **the request has to say correctly what it is trying
 * to do.** A direction the server cannot parse does not produce a safer
 * command, it produces an unanswerable one — and the gate collapses to
 * "refused" with nothing reporting why.
 *
 * Nothing pinned that condition before this file. Two string literals in
 * [NetworkDoorCommandRepository] carried the whole vocabulary, and
 * [KtorNetworkDoorCommandDataSource]'s KDoc claimed a counterpart in the tests
 * that decodes the `wire-contracts/doorCommand/` fixtures — which had never
 * been written. This is that counterpart.
 *
 * ## The failure mode it guards
 *
 * Every field of the response DTO has a default, so a server-side rename does
 * not throw. It decodes to `accepted = false` and refuses every spoken command,
 * silently, on the path that moves the real door. That is how `server/35`
 * shipped: one wrong read, every command refused with `DOOR_STATE_UNKNOWN`, for
 * a whole release. Fail-closed is the right default and is not a substitute for
 * noticing.
 *
 * ## Teeth
 *
 * `accepted` gets its teeth from the ACCEPTED fixture, not the rejected one:
 * dropping the key would decode to `false`, which the rejected case expects
 * anyway. The pair is what checks something, and
 * [theTwoDirectionsDoNotSendTheSameThing] is the positive control for the
 * vocabulary assertions — a mapping degenerated to a single constant would
 * satisfy every "is it in the table" check on its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DoorCommandWireContractTest {
    // Working directory is the module dir (MobileGarage/data), so two levels up
    // is the repo root. Same relative form the other wire-contract tests use.
    private val fixtureDir = File("../../wire-contracts/doorCommand")

    private fun readFixture(name: String): String = File(fixtureDir, name).readText()

    /**
     * The command vocabulary as the SERVER states it: the per-direction keys of
     * every row of the shared decision table. Read from the same file
     * `FirebaseServer/test/controller/DoorCommandGateTest.ts` and
     * `VoiceGateVerdictTableTest` read, so neither side can rename a direction
     * without the other noticing.
     */
    private val tableCommandNames: Set<String> by lazy {
        val file = File(fixtureDir, "verdict_table.json")
        assertTrue(
            file.exists(),
            "Missing shared fixture ${file.absolutePath} — it is committed at " +
                "<repo>/wire-contracts/doorCommand/verdict_table.json.",
        )
        val rows = Json
            .parseToJsonElement(file.readText())
            .jsonObject["rows"]
            ?.jsonArray
            ?.map { it.jsonObject }
            ?: error("verdict_table.json has no rows array")
        assertTrue(rows.isNotEmpty(), "verdict_table.json has no rows — this check would be vacuous.")
        val perRow = rows.map { row -> row.keys - "sensorEventType" - "doorState" }
        // Every row describes every direction, so the vocabulary is identical in
        // each. Asserting that first means the value below cannot be a union
        // that quietly papers over a row missing a direction.
        perRow.forEach { names ->
            assertEquals(
                perRow.first(),
                names,
                "verdict_table.json rows disagree about which commands exist",
            )
        }
        perRow.first()
    }

    private val validConfig = NetworkResult.Success(
        ServerConfig(
            buildTimestamp = "test",
            remoteButtonBuildTimestamp = "test",
            remoteButtonPushKey = "push-key",
        ),
    )

    /** Captures the wire value the repository chose, and answers with a fixed verdict. */
    private class CapturingDataSource : NetworkDoorCommandDataSource {
        val commands = mutableListOf<String>()

        override suspend fun checkDoorCommand(
            command: String,
            remoteButtonPushKey: String,
            idToken: String,
        ): NetworkResult<DoorCommandVerdict> {
            commands.add(command)
            return NetworkResult.Success(
                DoorCommandVerdict(
                    accepted = true,
                    rejection = null,
                    doorState = "CLOSED",
                    checkInStale = false,
                ),
            )
        }
    }

    private fun repositoryWith(
        dataSource: NetworkDoorCommandDataSource,
        scope: CoroutineScope,
    ): NetworkDoorCommandRepository {
        val configDs = FakeNetworkConfigDataSource().apply { setServerConfigResult(validConfig) }
        return NetworkDoorCommandRepository(
            networkDoorCommandDataSource = dataSource,
            serverConfigRepository = CachedServerConfigRepository(configDs, "key", scope),
            authRepository = FakeAuthRepository().apply {
                setIdTokenResult(FirebaseIdToken(idToken = "id-token", exp = Long.MAX_VALUE))
            },
        )
    }

    // ---------------------------------------------------------------- request

    @Test
    fun theRequestNamesTheDirectionItIsTryingToDo() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val ds = CapturingDataSource()
            val repo = repositoryWith(ds, scope)

            repo.checkCommand(VoiceIntent.OPEN)
            repo.checkCommand(VoiceIntent.CLOSE)

            assertEquals(listOf("open", "close"), ds.commands)
        }

    @Test
    fun everyDirectionWeCanSendIsOneTheServersTableJudges() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val ds = CapturingDataSource()
            val repo = repositoryWith(ds, scope)

            // Every intent the voice loop can commit. UNKNOWN is covered
            // separately below, because it must never reach the wire at all.
            repo.checkCommand(VoiceIntent.OPEN)
            repo.checkCommand(VoiceIntent.CLOSE)

            assertEquals(
                tableCommandNames,
                ds.commands.toSet(),
                "The directions this client sends must be exactly the ones the server's verdict " +
                    "table judges. A direction the server cannot parse is refused with no way to " +
                    "tell that apart from a genuinely refused door.",
            )
        }

    @Test
    fun theTwoDirectionsDoNotSendTheSameThing() =
        runTest {
            // Positive control. Without it, a mapping that returned one constant
            // for everything would satisfy every assertion above.
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val ds = CapturingDataSource()
            val repo = repositoryWith(ds, scope)

            repo.checkCommand(VoiceIntent.OPEN)
            repo.checkCommand(VoiceIntent.CLOSE)

            assertEquals(
                2,
                ds.commands.toSet().size,
                "open and close must not send the same wire value",
            )
        }

    @Test
    fun anUnknownIntentNeverReachesTheWire() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val ds = CapturingDataSource()
            val repo = repositoryWith(ds, scope)

            val result = repo.checkCommand(VoiceIntent.UNKNOWN)

            assertTrue(result is AppResult.Error, "UNKNOWN must not produce a verdict")
            assertTrue(
                ds.commands.isEmpty(),
                "UNKNOWN has no direction to state, so there is nothing honest to send. " +
                    "Guessing one would ask the server to judge a command the user never gave.",
            )
        }

    // --------------------------------------------------------------- response

    @Test
    fun decodesTheAcceptedFixture() =
        runTest {
            val ds = KtorNetworkDoorCommandDataSource(mockClient(readFixture("response_accepted.json")))

            val result = ds.checkDoorCommand("close", "push-key", "id-token")

            assertEquals(
                NetworkResult.Success(
                    DoorCommandVerdict(
                        accepted = true,
                        rejection = null,
                        doorState = "OPEN",
                        checkInStale = false,
                    ),
                ),
                result,
            )
        }

    @Test
    fun decodesTheRejectedStuckFixture() =
        runTest {
            val ds = KtorNetworkDoorCommandDataSource(mockClient(readFixture("response_rejected_stuck.json")))

            val result = ds.checkDoorCommand("open", "push-key", "id-token")

            assertEquals(
                NetworkResult.Success(
                    DoorCommandVerdict(
                        accepted = false,
                        rejection = DoorCommandRejection.DOOR_STUCK,
                        doorState = "STUCK",
                        checkInStale = false,
                    ),
                ),
                result,
            )
        }

    @Test
    fun theFixturesActuallyCarryTheKeysThisTestClaimsToPin() =
        runTest {
            // Without this, regenerating a fixture WITHOUT `accepted` would let
            // the rejected case pass on the DTO's `false` default, and the
            // deep-equals above would look like they were checking something.
            listOf("response_accepted.json", "response_rejected_stuck.json").forEach { name ->
                val verdict = Json
                    .parseToJsonElement(readFixture(name))
                    .jsonObject["verdict"]
                    ?.jsonObject
                    ?: error("$name has no verdict object")
                listOf("accepted", "doorState", "checkInStale").forEach { key ->
                    assertTrue(key in verdict.keys, "$name is missing the `$key` key this test pins")
                }
            }
            val accepted = Json
                .parseToJsonElement(readFixture("response_accepted.json"))
                .jsonObject["verdict"]!!
                .jsonObject
            assertEquals(
                "true",
                accepted["accepted"].toString(),
                "The accepted fixture is what gives `accepted` teeth. If it stops saying true, a " +
                    "rename of that key would decode to false and both cases would still pass.",
            )
        }

    @Test
    fun theRequestBodyAndHeadersAreTheOnesTheServerReads() =
        runTest {
            val methods = mutableListOf<HttpMethod>()
            val paths = mutableListOf<String>()
            val bodies = mutableListOf<String>()
            val headers = mutableListOf<Map<String, List<String>>>()
            val client = HttpClient(
                MockEngine { request ->
                    methods.add(request.method)
                    paths.add(request.url.encodedPath)
                    bodies.add((request.body as TextContent).text)
                    headers.add(request.headers.entries().associate { it.key to it.value })
                    respond(
                        content = ByteReadChannel(readFixture("response_accepted.json")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) {
                install(ContentNegotiation) { json(permissiveJson) }
                defaultRequest { url(URLBuilder("https://example.invalid/").build().toString()) }
            }

            KtorNetworkDoorCommandDataSource(client).checkDoorCommand("open", "push-key", "id-token")

            assertEquals(HttpMethod.Post, methods.single())
            assertEquals("/doorCommand", paths.single())
            assertEquals("""{"command":"open"}""", bodies.single())
            assertEquals(listOf("push-key"), headers.single()["X-RemoteButtonPushKey"])
            assertEquals(listOf("id-token"), headers.single()["X-AuthTokenGoogle"])
        }

    @Test
    fun aRejectionThisBuildDoesNotKnowStillRefuses() =
        runTest {
            val body = """{"executed":false,"verdict":{"accepted":false,"rejection":"SOMETHING_NEWER"}}"""
            val ds = KtorNetworkDoorCommandDataSource(mockClient(body))

            val result = ds.checkDoorCommand("open", "push-key", "id-token")

            val verdict = assertIs<NetworkResult.Success<DoorCommandVerdict>>(result).data
            assertEquals(DoorCommandRejection.UNRECOGNIZED, verdict.rejection)
            assertTrue(!verdict.accepted, "not understanding the answer is not permission")
        }

    private val permissiveJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun mockClient(responseBody: String): HttpClient =
        HttpClient(
            MockEngine {
                respond(
                    content = ByteReadChannel(responseBody),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) { json(permissiveJson) }
            defaultRequest { url(URLBuilder("https://example.invalid/").build().toString()) }
        }
}
