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

import com.chriscartland.garage.domain.model.CheckInStaleness
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The client's staleness threshold is the SERVER's, pinned through the shared
 * fixture — the same file `DoorCommandGateTest.ts` asserts against.
 *
 * Before strategy 4.3 the server was bound to `verdict_table.json` and the
 * two Kotlin copies agreed with each other by coincidence; nothing bound the
 * Kotlin side to the fixture, so the watch could have called a reading fresh
 * that the server's voice gate refused as stale. Same relative fixture path
 * as [VoiceGateVerdictTableTest].
 */
class CheckInStalenessThresholdTest {
    private val fixtureFile = File("../../wire-contracts/doorCommand/verdict_table.json")

    @Test
    fun theKotlinThresholdIsTheServersThreshold() {
        assertTrue(fixtureFile.exists(), "Missing shared fixture ${fixtureFile.absolutePath}")
        val fixtureSeconds = Json
            .parseToJsonElement(fixtureFile.readText())
            .jsonObject["staleThresholdSeconds"]
            ?.jsonPrimitive
            ?.long
            ?: error("verdict_table.json has no staleThresholdSeconds — the fixture changed shape")
        assertEquals(fixtureSeconds, CheckInStaleness.THRESHOLD_SECONDS, "domain threshold vs server fixture")
        assertEquals(
            CheckInStaleness.THRESHOLD_SECONDS,
            CheckInStalenessManager.CHECK_IN_STALE_THRESHOLD_SECONDS,
            "the manager must alias the one threshold, never restate it",
        )
    }
}
