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

package com.chriscartland.garage.fcm

import org.junit.Assert.assertEquals
import org.junit.Test

class DoorFcmModelTest {
    @Test
    fun alphanumericStringUnchanged() {
        val topic = "abc123".toFcmTopic()
        assertEquals("door_open-abc123", topic.string)
    }

    @Test
    fun specialCharsReplacedWithDot() {
        val topic = "a b/c@d".toFcmTopic()
        assertEquals("door_open-a.b.c.d", topic.string)
    }

    @Test
    fun allowedSpecialCharsPreserved() {
        val topic = "a-b_c.d~e%f".toFcmTopic()
        assertEquals("door_open-a-b_c.d~e%f", topic.string)
    }

    @Test
    fun emptyStringProducesPrefix() {
        val topic = "".toFcmTopic()
        assertEquals("door_open-", topic.string)
    }

    @Test
    fun allInvalidCharsBecomeDots() {
        val topic = "!@#".toFcmTopic()
        assertEquals("door_open-...", topic.string)
    }

    @Test
    fun typicalBuildTimestamp() {
        // Colons and slashes should be replaced, hyphens and dots preserved
        val topic = "2024-01-15T10:30:00Z".toFcmTopic()
        assertEquals("door_open-2024-01-15T10.30.00Z", topic.string)
    }
}
