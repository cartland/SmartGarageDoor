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

package com.chriscartland.garage.fcm

import org.junit.Assert.assertEquals
import org.junit.Test

class TestNotificationPayloadTest {
    @Test
    fun parsesAllFields() {
        val content =
            TestNotificationPayload.parse(
                mapOf("title" to "Garage open", "body" to "Open 12 min", "tag" to "door-1"),
            )
        assertEquals("Garage open", content.title)
        assertEquals("Open 12 min", content.body)
        assertEquals("door-1", content.tag)
    }

    @Test
    fun defaultsWhenFieldsMissing() {
        val content = TestNotificationPayload.parse(emptyMap())
        assertEquals("Test notification", content.title)
        assertEquals("", content.body)
        assertEquals(TestNotificationPayload.DEFAULT_TAG, content.tag)
    }

    @Test
    fun emptyTitleAndTagFallBackToDefaults() {
        val content = TestNotificationPayload.parse(mapOf("title" to "", "tag" to ""))
        assertEquals("Test notification", content.title)
        assertEquals(TestNotificationPayload.DEFAULT_TAG, content.tag)
    }
}
