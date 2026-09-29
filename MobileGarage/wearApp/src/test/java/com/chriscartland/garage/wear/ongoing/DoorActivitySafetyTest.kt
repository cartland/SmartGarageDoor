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

package com.chriscartland.garage.wear.ongoing

import com.chriscartland.garage.wear.ui.WearHomeViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The chip on the watch face is READ-ONLY, and this is what enforces it.
 *
 * Same shape as `GarageDoorTileSafetyTest`: a service that cannot reach the
 * button by construction, plus a positive control so the check cannot pass
 * vacuously.
 */
class DoorActivitySafetyTest {
    @Test
    fun theServiceHoldsNothingThatPresses() {
        val held = DoorActivityService::class.java.declaredFields
            .map { it.type.simpleName }
            .toSet()
        val violations = held intersect FORBIDDEN
        assertTrue("DoorActivityService must not hold $violations.", violations.isEmpty())
    }

    @Test
    fun theManifestKeepsTheServicePrivate() {
        // Not exported and no intent filter: nothing outside the app can start
        // it, so the only thing that can put a chip on the face is the
        // ViewModel's own decision.
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val start = manifest.indexOf(".ongoing.DoorActivityService")
        assertTrue("the service must be declared", start >= 0)
        val declaration = manifest.substring(start, manifest.indexOf("/>", start))
        assertTrue("must be exported=false: $declaration", declaration.contains("android:exported=\"false\""))
        assertFalse("must have no intent filter", declaration.contains("intent-filter"))
        assertTrue("must be a short foreground service", declaration.contains("shortService"))
    }

    @Test
    fun theCheckCanActuallyFail() {
        // The class that DOES hold the button must trip the same list.
        val violations = WearHomeViewModel::class.java.constructors
            .flatMap { it.parameterTypes.asList() }
            .map { it.simpleName }
            .toSet() intersect FORBIDDEN
        assertTrue("positive control: WearHomeViewModel should hold $FORBIDDEN, found $violations", violations.isNotEmpty())
    }

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
    }
}
