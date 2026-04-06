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

import com.chriscartland.garage.domain.model.toFcmTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for FCM topic name generation.
 *
 * The topic name is critical: if it changes, the app subscribes to a different
 * topic than the server sends to, and push notifications silently stop working.
 * These tests lock down the exact format so accidental changes are caught.
 */
class FcmTopicTest {
    @Test
    fun topicNameHasDoorOpenPrefix() {
        val topic = "Sat Mar 13 14:45:00 2021".toFcmTopic()
        assertTrue(
            "FCM topic must start with 'door_open-' prefix",
            topic.string.startsWith("door_open-"),
        )
    }

    @Test
    fun topicNameMatchesKnownBuildTimestamp() {
        // This is the actual build timestamp format from the server.
        // If this test fails, push notifications will break in production.
        val topic = "Sat Mar 13 14:45:00 2021".toFcmTopic()
        assertEquals("door_open-Sat.Mar.13.14.45.00.2021", topic.string)
    }

    @Test
    fun topicNameReplacesSpecialCharactersWithDot() {
        // FCM topics only allow [a-zA-Z0-9-_.~%]
        // Our implementation replaces everything else with '.'
        val topic = "Test Build @ 2024!".toFcmTopic()
        assertEquals("door_open-Test.Build...2024.", topic.string)
    }

    @Test
    fun topicNamePreservesAllowedCharacters() {
        val topic = "safe-name_v1.0~test%20".toFcmTopic()
        assertEquals("door_open-safe-name_v1.0~test%20", topic.string)
    }

    @Test
    fun topicNameHandlesEmptyString() {
        val topic = "".toFcmTopic()
        assertEquals("door_open-", topic.string)
    }

    @Test
    fun topicNameIsStableAcrossMultipleCalls() {
        val input = "Sat Mar 13 14:45:00 2021"
        val topic1 = input.toFcmTopic()
        val topic2 = input.toFcmTopic()
        assertEquals(
            "Topic generation must be deterministic",
            topic1.string,
            topic2.string,
        )
    }
}
