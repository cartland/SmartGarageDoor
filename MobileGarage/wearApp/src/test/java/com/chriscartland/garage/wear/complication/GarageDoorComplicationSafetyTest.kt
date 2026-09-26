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

package com.chriscartland.garage.wear.complication

import com.chriscartland.garage.wear.glance.WearGlanceStatus
import com.chriscartland.garage.wear.ui.WearHomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **The complication has no route to the garage button.**
 *
 * The same property the tile asserts, and it matters more here. A tile is in
 * a carousel you swipe through; a complication sits on the watch face itself,
 * which is the surface a sleeve or a wrist meets all day. There is also no
 * gesture available: a complication tap is a single tap, so the
 * press-and-hold that guards the button everywhere else could not be
 * expressed even if we wanted it.
 */
class GarageDoorComplicationSafetyTest {
    @Test
    fun theSharedGlanceReaderCannotReachTheRealRemoteButton() {
        // The complication builds nothing of its own: it reads
        // WearGlanceStatus. If that cannot hold the button, neither surface
        // that reads it can.
        val violations = dependenciesOf(WearGlanceStatus::class.java) intersect FORBIDDEN
        assertTrue(
            "WearGlanceStatus must not depend on $violations. The glance surfaces show " +
                "the door and open the app; the button is reachable only from the door " +
                "screen, where a hold can guard it.",
            violations.isEmpty(),
        )
    }

    @Test
    fun theComplicationServiceHoldsNoRouteToTheDoor() {
        val fields = GarageDoorComplicationService::class.java.declaredFields
            .map { it.type.simpleName }
            .toSet()
        val violations = fields intersect FORBIDDEN
        assertTrue("GarageDoorComplicationService must not hold $violations.", violations.isEmpty())
    }

    @Test
    fun theCheckCanActuallyFail() {
        // Positive control: both assertions above are "this set is empty",
        // which a typo in FORBIDDEN would satisfy forever. The ViewModel that
        // DOES own the button must trip the same predicate.
        val violations = dependenciesOf(WearHomeViewModel::class.java) intersect FORBIDDEN
        assertTrue(
            "the forbidden-dependency check matched nothing even against the " +
                "ViewModel that owns the button — it is not measuring anything",
            violations.isNotEmpty(),
        )
    }

    /**
     * The manifest is the other half: a service with no button is no
     * guarantee if it advertises a slot it cannot be honest in.
     *
     * Icon-only and ranged-value complications have nowhere to state the age,
     * so a door glyph there would look the same whether the reading arrived a
     * minute ago or last week. Offering nothing is the honest answer.
     */
    @Test
    fun onlyTypesThatCanStateAnAgeAreAdvertised() {
        val manifest = candidateManifests().firstOrNull { it.exists() }
            ?: error("could not locate the wear manifest from ${File(".").absolutePath}")
        val declared = SUPPORTED_TYPES
            .find(manifest.readText())
            ?.groupValues
            ?.get(1)
            ?: error("SUPPORTED_TYPES meta-data not found — the complication is not declared")
        val types = declared.split(",").map { it.trim() }.toSet()
        assertEquals(
            "a complication type with no room for the reading's age must not be offered",
            setOf("SHORT_TEXT", "LONG_TEXT"),
            types,
        )
    }

    private fun dependenciesOf(type: Class<*>): Set<String> =
        type.constructors
            .flatMap { it.parameterTypes.asList() }
            .map { it.simpleName }
            .toSet()

    private fun candidateManifests() =
        listOf(
            File("src/main/AndroidManifest.xml"),
            File("wearApp/src/main/AndroidManifest.xml"),
            File("MobileGarage/wearApp/src/main/AndroidManifest.xml"),
        )

    private companion object {
        val FORBIDDEN = setOf(
            "PushRemoteButtonUseCase",
            "RemoteButtonRepository",
            "NetworkButtonDataSource",
            "ButtonStateMachine",
            "CheckDoorCommandUseCase",
            "WearHomeViewModel",
            "WearLiveVoiceViewModel",
        )

        val SUPPORTED_TYPES =
            Regex(
                """SUPPORTED_TYPES"\s*\n\s*android:value="([^"]+)"""",
            )
    }
}
