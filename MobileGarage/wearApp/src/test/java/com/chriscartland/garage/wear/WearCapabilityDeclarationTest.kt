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

package com.chriscartland.garage.wear

import com.chriscartland.garage.domain.repository.WearCompanionRepository
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the Wear capability declaration (`res/values/wear.xml`) to the
 * shared constant the phone queries. XML can't reference a Kotlin const,
 * so the two are kept in sync by hand — this test catches drift (same
 * pattern as `RoomSchemaTest` parsing source files).
 */
class WearCapabilityDeclarationTest {
    @Test
    fun wearXmlDeclaresTheWatchAppCapability() {
        val wearXml = File("src/main/res/values/wear.xml")
        assertTrue("wear.xml missing at ${wearXml.absolutePath}", wearXml.exists())
        val content = wearXml.readText()
        assertTrue(
            "wear.xml must declare the '${WearCompanionRepository.WATCH_APP_CAPABILITY}' capability " +
                "(WearCompanionRepository.WATCH_APP_CAPABILITY) so the phone can detect the watch app.",
            content.contains("<item>${WearCompanionRepository.WATCH_APP_CAPABILITY}</item>"),
        )
    }
}
