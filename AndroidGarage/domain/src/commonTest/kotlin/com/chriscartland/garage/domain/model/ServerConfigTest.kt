package com.chriscartland.garage.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins ServerConfig's `toString` behavior: the auto-generated
 * data-class toString leaks `remoteButtonPushKey` (the X-RemoteButtonPushKey
 * shared secret) via `Logger.i { "config <- $config" }` patterns. The
 * custom toString masks it; these tests fail loudly if a future refactor
 * "fixes" the override away.
 *
 * Security audit reference: H1 / C1.
 */
class ServerConfigTest {
    @Test
    fun toStringMasksPushKey() {
        val config = ServerConfig(
            buildTimestamp = "Sat Mar 13 14:45:00 2021",
            remoteButtonBuildTimestamp = "Sat Apr 10 23:57:32 2021",
            remoteButtonPushKey = "super-secret-shared-key-abc123",
        )
        val rendered = config.toString()
        assertFalse(
            "super-secret-shared-key-abc123" in rendered,
            "toString must not expose the push key (audit H1/C1); got: $rendered",
        )
        assertTrue(
            "remoteButtonPushKey=***" in rendered,
            "Non-empty push key should render as '***'; got: $rendered",
        )
    }

    @Test
    fun toStringDistinguishesEmptyPushKey() {
        val config = ServerConfig(
            buildTimestamp = "x",
            remoteButtonBuildTimestamp = "y",
            remoteButtonPushKey = "",
        )
        val rendered = config.toString()
        assertTrue(
            "remoteButtonPushKey=<empty>" in rendered,
            "Empty push key should render as '<empty>' for diagnostic clarity; got: $rendered",
        )
    }

    @Test
    fun toStringStillIncludesNonSecretFields() {
        // buildTimestamp + remoteButtonBuildTimestamp are device identifiers
        // (FCM topic seeds) but not credentials. They stay visible so the
        // log line remains useful for diagnosing config issues.
        val config = ServerConfig(
            buildTimestamp = "build-1",
            remoteButtonBuildTimestamp = "build-2",
            remoteButtonPushKey = "secret",
        )
        val rendered = config.toString()
        assertTrue("build-1" in rendered, "buildTimestamp should be visible; got: $rendered")
        assertTrue(
            "build-2" in rendered,
            "remoteButtonBuildTimestamp should be visible; got: $rendered",
        )
    }

    @Test
    fun equalsAndHashCodeAreUnaffectedByToStringOverride() {
        // The data-class auto-generated equals/hashCode/copy operate on
        // the actual field values, not toString output. Override only
        // touches toString. This test pins the contract so a future
        // refactor doesn't accidentally break MutableStateFlow dedup.
        val a = ServerConfig("b1", "b2", "secret")
        val b = ServerConfig("b1", "b2", "secret")
        val c = ServerConfig("b1", "b2", "different-secret")
        assertEquals(a, b, "equals should compare actual values")
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == c, "equals should detect a different push key")
        assertFalse(a.hashCode() == c.hashCode(), "hashCode should differ on different secret")
    }
}
