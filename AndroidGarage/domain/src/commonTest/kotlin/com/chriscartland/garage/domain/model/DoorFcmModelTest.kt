package com.chriscartland.garage.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

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
        val topic = "2024-01-15T10:30:00Z".toFcmTopic()
        assertEquals("door_open-2024-01-15T10.30.00Z", topic.string)
    }
}
