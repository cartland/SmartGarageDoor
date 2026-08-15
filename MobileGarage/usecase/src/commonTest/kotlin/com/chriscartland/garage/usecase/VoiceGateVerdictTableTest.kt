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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.VoiceIntent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The client half of the door-command decision table, asserted against the
 * SAME file the server asserts against.
 *
 * `wire-contracts/doorCommand/verdict_table.json` is loaded here and by
 * `FirebaseServer/test/controller/DoorCommandGateTest.ts`. Before this test
 * existed, the rule was implemented twice — once in TypeScript, once in
 * Kotlin — from two copies of the reasoning, with nothing that would report a
 * disagreement. A drift would have surfaced as a spoken command that one side
 * allowed and the other refused, which on the watch means a press that either
 * silently does not happen or gets refused by the server after the countdown
 * already completed.
 *
 * This is the same shape of protection as the rest of `wire-contracts/`: pin
 * the contract in one document, make both sides read it, and a unilateral
 * change fails on at least one side.
 *
 * ## What this test does NOT cover
 *
 * Staleness. The server judges check-in age itself and the fixture records
 * its threshold; the client's [VoiceDoorStateMapper] takes `isCheckInStale`
 * as a parameter decided upstream. The one assertion worth making here is
 * that a stale check-in forces UNKNOWN, which it does below — the actual
 * threshold value lives on the server and is deliberately not duplicated.
 */
class VoiceGateVerdictTableTest {
    // Working directory for these tests is the module dir (MobileGarage/usecase),
    // so two levels up is the repo root. Same relative form the :data
    // wire-contract tests use.
    private val fixtureFile = File("../../wire-contracts/doorCommand/verdict_table.json")

    private val rows: List<Row> by lazy {
        assertTrue(
            fixtureFile.exists(),
            "Missing shared fixture ${fixtureFile.absolutePath}. It is committed at " +
                "<repo>/wire-contracts/doorCommand/verdict_table.json and is also loaded by " +
                "the server's DoorCommandGateTest.ts.",
        )
        val root = Json.parseToJsonElement(fixtureFile.readText()).jsonObject
        root.getValue("rows").jsonArray.map { it.jsonObject.toRow() }
    }

    private data class Row(
        val sensorEventType: String?,
        val doorState: String,
        val open: String?,
        val close: String?,
    )

    /** `contentOrNull` is already JsonNull-aware, which is the whole subtlety here. */
    private fun JsonObject.stringOrNull(key: String): String? = getValue(key).jsonPrimitive.contentOrNull

    private fun JsonObject.toRow() =
        Row(
            sensorEventType = stringOrNull("sensorEventType"),
            doorState = getValue("doorState").jsonPrimitive.content,
            open = stringOrNull("open"),
            close = stringOrNull("close"),
        )

    /**
     * The server names its refusals slightly differently (`ALREADY_OPEN` vs
     * `DOOR_ALREADY_OPEN`), so the correspondence is stated once, here, rather
     * than being implied by a name match that could quietly stop holding.
     */
    private fun reasonFor(serverRejection: String): VoiceCommandIgnoreReason =
        when (serverRejection) {
            "ALREADY_OPEN" -> VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN
            "ALREADY_CLOSED" -> VoiceCommandIgnoreReason.DOOR_ALREADY_CLOSED
            "DOOR_MOVING" -> VoiceCommandIgnoreReason.DOOR_MOVING
            "DOOR_STUCK" -> VoiceCommandIgnoreReason.DOOR_STUCK
            "DOOR_STATE_UNKNOWN" -> VoiceCommandIgnoreReason.DOOR_STATE_UNKNOWN
            else -> fail(
                "The shared verdict table uses a rejection this client has no reason for: " +
                    "$serverRejection. Add it to VoiceCommandIgnoreReason and to this mapping, " +
                    "or the two sides no longer agree about what can be refused.",
            )
        }

    /** `null` is the fixture's way of writing "no reading at all". */
    private fun positionFor(sensorEventType: String?): DoorPosition? =
        sensorEventType?.let { name ->
            DoorPosition.entries.firstOrNull { it.name == name }
                ?: fail(
                    "The shared verdict table has a sensor event type this client cannot " +
                        "represent: $name. DoorPosition needs the new value before voice can " +
                        "judge a door in that state.",
                )
        }

    @Test
    fun everyRowInTheSharedTableMatchesThisClient() {
        rows.forEach { row ->
            val position = positionFor(row.sensorEventType)
            val projected = VoiceDoorStateMapper.project(position, isCheckInStale = false)
            assertEquals(
                row.doorState,
                projected.name,
                "Projection disagrees with the shared table for ${row.sensorEventType}",
            )

            assertEquals(
                row.open?.let { reasonFor(it) },
                VoiceCommandGate.reasonFor(VoiceIntent.OPEN, projected),
                "OPEN verdict disagrees with the shared table for ${row.sensorEventType}",
            )
            assertEquals(
                row.close?.let { reasonFor(it) },
                VoiceCommandGate.reasonFor(VoiceIntent.CLOSE, projected),
                "CLOSE verdict disagrees with the shared table for ${row.sensorEventType}",
            )
        }
    }

    /**
     * Coverage, in the direction the row-by-row loop cannot check: the loop
     * walks the FIXTURE, so a `DoorPosition` the table forgot would never be
     * visited and the suite would stay green while voice had an unjudged
     * state. This walks the enum instead.
     */
    @Test
    fun theSharedTableCoversEveryDoorPositionThisClientKnows() {
        val covered = rows.mapNotNull { it.sensorEventType }.toSet()
        val missing = DoorPosition.entries.map { it.name }.filterNot { it in covered }
        assertTrue(
            missing.isEmpty(),
            "wire-contracts/doorCommand/verdict_table.json has no row for: $missing. " +
                "A DoorPosition with no row is a door state the two sides have never " +
                "agreed about.",
        )
    }

    /** The fixture must also carry the no-reading case, which is not a DoorPosition. */
    @Test
    fun theSharedTableCoversTheNoReadingCase() {
        assertNotNull(
            rows.firstOrNull { it.sensorEventType == null },
            "The table needs a null-sensorEventType row: 'the door has never reported' is a " +
                "real state on both sides and must refuse both directions.",
        )
    }

    /**
     * Positive control. Every other assertion here is an equality, so a gate
     * that refused everything — or a projection that answered UNKNOWN for all
     * inputs — would satisfy most of this file while voice was entirely dead.
     * At least one row must ALLOW a direction, and the states must not all
     * collapse to one value.
     */
    @Test
    fun theTableIsNotUniformlyRefusing() {
        val allowsSomething = rows.any { it.open == null || it.close == null }
        assertTrue(
            allowsSomething,
            "No row in the shared table allows any direction. Either the fixture is " +
                "degenerate or this test would pass against a gate that refuses everything.",
        )

        val distinctStates = rows.map { it.doorState }.toSet()
        assertTrue(
            distinctStates.size > 1,
            "Every row projects to the same door state ($distinctStates), so the projection " +
                "assertions above would hold for a mapper that ignored its input.",
        )
    }

    /**
     * The one staleness fact the client owns: whatever the position says, a
     * stale check-in forces UNKNOWN and both directions are refused. The
     * server reaches the same verdict by its own clock.
     */
    @Test
    fun aStaleCheckInRefusesEveryDirectionRegardlessOfPosition() {
        DoorPosition.entries.forEach { position ->
            val projected = VoiceDoorStateMapper.project(position, isCheckInStale = true)
            assertEquals(VoiceDoorState.UNKNOWN, projected, "stale $position")
            assertEquals(
                VoiceCommandIgnoreReason.DOOR_STATE_UNKNOWN,
                VoiceCommandGate.reasonFor(VoiceIntent.OPEN, projected),
            )
            assertEquals(
                VoiceCommandIgnoreReason.DOOR_STATE_UNKNOWN,
                VoiceCommandGate.reasonFor(VoiceIntent.CLOSE, projected),
            )
        }
    }
}
