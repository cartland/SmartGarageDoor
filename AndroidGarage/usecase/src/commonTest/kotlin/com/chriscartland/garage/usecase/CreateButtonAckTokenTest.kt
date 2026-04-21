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

package com.chriscartland.garage.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Contract tests for [ButtonAckToken.create].
 *
 * The token format is part of the ESP32 / server protocol (the server uses the
 * token to deduplicate button presses). Changes to the format here mean a server
 * change is needed too — fail loudly if anyone tweaks this without thinking.
 */
class CreateButtonAckTokenTest {
    @Test
    fun tokenFormatIsAndroidVersionTimestamp() {
        val token = ButtonAckToken.create(
            currentTimeMillis = 1234567890L,
            appVersion = "2.4.0",
        )
        assertEquals("android-2.4.0-1234567890", token)
    }

    @Test
    fun tokenIsDeterministicForSameInputs() {
        val a = ButtonAckToken.create(currentTimeMillis = 1000L, appVersion = "1.0")
        val b = ButtonAckToken.create(currentTimeMillis = 1000L, appVersion = "1.0")
        assertEquals(a, b)
    }

    @Test
    fun tokenChangesWhenTimestampChanges() {
        val a = ButtonAckToken.create(currentTimeMillis = 1000L, appVersion = "1.0")
        val b = ButtonAckToken.create(currentTimeMillis = 1001L, appVersion = "1.0")
        assertNotEquals(a, b)
    }

    @Test
    fun tokenChangesWhenVersionChanges() {
        val a = ButtonAckToken.create(currentTimeMillis = 1000L, appVersion = "1.0")
        val b = ButtonAckToken.create(currentTimeMillis = 1000L, appVersion = "1.1")
        assertNotEquals(a, b)
    }

    @Test
    fun specialCharactersInVersionAreReplacedWithDots() {
        // The server URL-encodes tokens, so non-alphanumeric chars must be
        // replaced with `.` before sending to keep the token URL-safe.
        val token = ButtonAckToken.create(
            currentTimeMillis = 100L,
            appVersion = "2.4.0+release/special chars",
        )
        // `+`, `/`, ` ` should be replaced with `.`; alphanumerics, `-`, `_`, `.` kept.
        assertTrue(
            "+" !in token && "/" !in token && " " !in token,
            "Token contained banned chars: $token",
        )
    }

    @Test
    fun emptyVersionStillProducesValidToken() {
        val token = ButtonAckToken.create(currentTimeMillis = 99L, appVersion = "")
        assertEquals("android--99", token)
    }
}
