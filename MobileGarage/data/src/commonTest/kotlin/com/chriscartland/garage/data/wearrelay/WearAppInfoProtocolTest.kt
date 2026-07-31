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

package com.chriscartland.garage.data.wearrelay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The watch writes this and the phone reads it, so it is a wire contract
 * between two separately-released apps.
 *
 * That is what makes the failure cases the interesting ones: the writer is
 * frequently a DIFFERENT version from the reader — a phone updated today
 * reading a watch that has not been updated in months, or the reverse — and
 * every mismatch has to degrade to "version unknown" rather than to a crash in
 * Settings or, worse, a confidently wrong version number.
 */
class WearAppInfoProtocolTest {
    @Test
    fun aPublishedVersionSurvivesTheRoundTrip() {
        val info = WearAppInfo(versionName = "0.5.1", versionCode = 1_000_019L)
        assertEquals(info, WearAppInfoProtocol.decode(WearAppInfoProtocol.encode(info)))
    }

    /**
     * A newer watch may add fields. The phone must keep reading the ones it
     * knows rather than rejecting the whole payload — otherwise shipping a new
     * field to the watch would silently blank the version on every phone that
     * has not been updated yet.
     */
    @Test
    fun anUnknownFieldFromANewerWatchIsIgnored() {
        val payload = """{"versionName":"9.9.9","versionCode":123,"somethingNew":true}"""
        assertEquals("9.9.9", WearAppInfoProtocol.decode(payload.encodeToByteArray())?.versionName)
    }

    /** versionCode is optional, so an older or partial writer still parses. */
    @Test
    fun versionCodeIsOptional() {
        val decoded = WearAppInfoProtocol.decode("""{"versionName":"0.4.0"}""".encodeToByteArray())
        assertEquals("0.4.0", decoded?.versionName)
        assertNull(decoded?.versionCode)
    }

    /**
     * Garbage decodes to null, never to an exception.
     *
     * This read happens inside the Settings status poll. A throw there would
     * take out the whole watch row — including the install button — over a
     * cosmetic line.
     */
    @Test
    fun malformedPayloadsDecodeToNull() {
        assertNull(WearAppInfoProtocol.decode("not json".encodeToByteArray()))
        assertNull(WearAppInfoProtocol.decode(ByteArray(0)))
        assertNull(WearAppInfoProtocol.decode("""{"versionCode":5}""".encodeToByteArray()))
    }

    /**
     * A blank version is not a version.
     *
     * Without this the phone would render "Version  on your watch" — the one
     * outcome worse than admitting the version is unknown, because it looks
     * like a rendering bug rather than missing data.
     */
    @Test
    fun aBlankVersionNameIsTreatedAsNoVersionAtAll() {
        assertNull(WearAppInfoProtocol.decode("""{"versionName":""}""".encodeToByteArray()))
        assertNull(WearAppInfoProtocol.decode("""{"versionName":"   "}""".encodeToByteArray()))
    }
}
