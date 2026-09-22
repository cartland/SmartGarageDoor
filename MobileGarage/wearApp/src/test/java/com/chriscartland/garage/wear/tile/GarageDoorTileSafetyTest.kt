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

package com.chriscartland.garage.wear.tile

import com.chriscartland.garage.wear.ui.WearHomeViewModel
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The tile has no route to the garage button, and this is what says so.**
 *
 * A tile lives in a carousel the user swipes through, which makes it the
 * easiest surface in the system to touch without meaning to — and unlike the
 * door screen it cannot express a continuous press-and-hold, the gesture that
 * guards the button everywhere else on the watch. So the tile is read-only:
 * tapping it opens the app, where the real guard lives.
 *
 * That is a property worth asserting rather than trusting, for the same
 * reason `WearSimulatedVoiceViewModelTest.cannotReachTheRealRemoteButton`
 * asserts its own: the next person to work on this file will be looking at a
 * surface that already shows the door and be one small step from making it
 * act on the door too.
 */
class GarageDoorTileSafetyTest {
    @Test
    fun theTilePresenterCannotReachTheRealRemoteButton() {
        val violations = dependenciesOf(WearTilePresenter::class.java) intersect FORBIDDEN
        assertTrue(
            "WearTilePresenter must not depend on $violations. The tile shows the " +
                "door and opens the app; the button is reachable only from the door " +
                "screen, where a hold can guard it.",
            violations.isEmpty(),
        )
    }

    @Test
    fun theTileServiceCannotReachTheRealRemoteButton() {
        // The service is the other half: a presenter with no button is no
        // guarantee if the service reaches around it.
        val fields = GarageDoorTileService::class.java.declaredFields
            .map { it.type.simpleName }
            .toSet()
        val violations = fields intersect FORBIDDEN
        assertTrue(
            "GarageDoorTileService must not hold $violations.",
            violations.isEmpty(),
        )
    }

    @Test
    fun theCheckCanActuallyFail() {
        // Positive control. Both assertions above are "this set is empty",
        // which a typo in FORBIDDEN — or a reflection call that returned
        // nothing — would satisfy forever. WearHomeViewModel is the surface
        // that DOES press the button, so the same predicate run against it
        // must come back non-empty.
        val violations = dependenciesOf(WearHomeViewModel::class.java) intersect FORBIDDEN
        assertTrue(
            "the forbidden-dependency check matched nothing even against the " +
                "ViewModel that owns the button — the check is not measuring anything",
            violations.isNotEmpty(),
        )
    }

    private fun dependenciesOf(type: Class<*>): Set<String> =
        type.constructors
            .flatMap { it.parameterTypes.asList() }
            .map { it.simpleName }
            .toSet()

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
