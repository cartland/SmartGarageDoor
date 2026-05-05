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

package com.chriscartland.garage.data

import com.chriscartland.garage.domain.model.ButtonHealth
import com.chriscartland.garage.domain.model.ButtonHealthState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ButtonHealthFcmPayloadParserTest {
    @Test
    fun parsesOnlinePayload() {
        val data = mapOf(
            "buttonState" to "ONLINE",
            "stateChangedAtSeconds" to "1730000000",
            "buildTimestamp" to "Sat Apr 10 23:57:32 2021",
        )
        assertEquals(
            ButtonHealth(state = ButtonHealthState.ONLINE, stateChangedAtSeconds = 1730000000L),
            ButtonHealthFcmPayloadParser.parse(data),
        )
    }

    @Test
    fun parsesOfflinePayload() {
        val data = mapOf(
            "buttonState" to "OFFLINE",
            "stateChangedAtSeconds" to "1730000500",
            "buildTimestamp" to "Sat Apr 10 23:57:32 2021",
        )
        assertEquals(
            ButtonHealth(state = ButtonHealthState.OFFLINE, stateChangedAtSeconds = 1730000500L),
            ButtonHealthFcmPayloadParser.parse(data),
        )
    }

    @Test
    fun unknownStateStringMapsToUnknownEnum() {
        // Forward-compat: a future server-added state (e.g. "MAINTENANCE") must
        // not crash; it deserializes to UNKNOWN so old clients keep working.
        val data = mapOf(
            "buttonState" to "MAINTENANCE",
            "stateChangedAtSeconds" to "1730000000",
        )
        assertEquals(
            ButtonHealth(state = ButtonHealthState.UNKNOWN, stateChangedAtSeconds = 1730000000L),
            ButtonHealthFcmPayloadParser.parse(data),
        )
    }

    @Test
    fun returnsNullWhenButtonStateMissing() {
        val data = mapOf("stateChangedAtSeconds" to "1730000000")
        assertNull(ButtonHealthFcmPayloadParser.parse(data))
    }

    @Test
    fun returnsNullWhenStateChangedAtSecondsMissing() {
        val data = mapOf("buttonState" to "ONLINE")
        assertNull(ButtonHealthFcmPayloadParser.parse(data))
    }

    @Test
    fun returnsNullWhenStateChangedAtSecondsNotANumber() {
        val data = mapOf(
            "buttonState" to "ONLINE",
            "stateChangedAtSeconds" to "not-a-number",
        )
        assertNull(ButtonHealthFcmPayloadParser.parse(data))
    }
}
