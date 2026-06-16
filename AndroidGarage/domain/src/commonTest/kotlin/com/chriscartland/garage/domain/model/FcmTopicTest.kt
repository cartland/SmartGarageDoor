package com.chriscartland.garage.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
            topic.string.startsWith("door_open-"),
            "FCM topic must start with 'door_open-' prefix",
        )
    }

    @Test
    fun topicNameMatchesKnownBuildTimestamp() {
        val topic = "Sat Mar 13 14:45:00 2021".toFcmTopic()
        assertEquals("door_open-Sat.Mar.13.14.45.00.2021", topic.string)
    }

    @Test
    fun topicNameReplacesSpecialCharactersWithDot() {
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
        assertEquals(topic1.string, topic2.string, "Topic generation must be deterministic")
    }

    // --- Additive resolved-on-close v2 topic ---
    // Must stay byte-identical to the server's buildTimestampToFcmTopicV2
    // (FcmTopic.ts) — a unilateral rename on either side breaks the resolved
    // notification silently.

    @Test
    fun v2TopicMatchesKnownBuildTimestamp() {
        assertEquals(
            "door_open_v2-Sat.Mar.13.14.45.00.2021",
            "Sat Mar 13 14:45:00 2021".toDoorResolvedFcmTopic(),
        )
    }

    @Test
    fun v2TopicHasDistinctPrefixFromLegacyDoorTopic() {
        val input = "Sat Mar 13 14:45:00 2021"
        assertTrue(input.toDoorResolvedFcmTopic().startsWith(DOOR_RESOLVED_FCM_TOPIC_PREFIX))
        // door_open_v2- does NOT start with door_open- (next char is '_', not '-'),
        // so prefix routing never confuses the two.
        assertTrue(!input.toDoorResolvedFcmTopic().startsWith("door_open-"))
        assertTrue(!input.toFcmTopic().string.startsWith(DOOR_RESOLVED_FCM_TOPIC_PREFIX))
    }

    @Test
    fun v2TopicReplacesSpecialCharactersWithDot() {
        assertEquals("door_open_v2-Test.Build...2024.", "Test Build @ 2024!".toDoorResolvedFcmTopic())
    }
}
