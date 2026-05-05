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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * This test pins the FCM topic format on the ANDROID side.
 * `FirebaseServer/test/model/ButtonHealthFcmTopicTest.ts` MUST share
 * the exact same input/output pairs to catch drift between the server
 * topic builder and Android topic-string assumption.
 */
class ButtonHealthFcmTopicTest {
    @Test
    fun convertsPlainAsciiBuildTimestampToSanitizedTopic() {
        val input = "Sat Mar 13 14:45:00 2021"
        val expected = "buttonHealth-Sat.Mar.13.14.45.00.2021"
        assertEquals(expected, ButtonHealthFcmTopic.fromBuildTimestamp(input))
    }

    @Test
    fun decodesUrlEncodedBuildTimestampBeforeSanitizing() {
        // URL-encoded form of 'Sat Apr 10 23:57:32 2021' (the production button buildTimestamp shape).
        val input = "Sat%20Apr%2010%2023%3A57%3A32%202021"
        val expected = "buttonHealth-Sat.Apr.10.23.57.32.2021"
        assertEquals(expected, ButtonHealthFcmTopic.fromBuildTimestamp(input))
    }

    @Test
    fun fallsBackToRawInputWhenDecodeThrows() {
        // '%ZZ' is invalid percent encoding; decoder throws NumberFormatException.
        // The builder must catch and proceed with the raw string + sanitization.
        val input = "%ZZ"
        // % is allowed in FCM topic chars; Z stays.
        val expected = "buttonHealth-%ZZ"
        assertEquals(expected, ButtonHealthFcmTopic.fromBuildTimestamp(input))
    }

    @Test
    fun throwsOnEmptyBuildTimestamp() {
        assertFailsWith<IllegalArgumentException> {
            ButtonHealthFcmTopic.fromBuildTimestamp("")
        }
    }

    @Test
    fun preservesValidFcmTopicCharsWithoutSanitization() {
        // a-zA-Z0-9-_.~% are all valid FCM topic chars per Firebase spec.
        val input = "abc-123_xyz.~%"
        val expected = "buttonHealth-abc-123_xyz.~%"
        assertEquals(expected, ButtonHealthFcmTopic.fromBuildTimestamp(input))
    }
}
