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

package com.chriscartland.garage.ui.home

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class HomeMapperTest {
    // 2026-04-29 12:00:00 UTC — used to compute the relative `lastChangeTimeSeconds`
    // for test events (see [event]). The mapper itself no longer takes
    // now/zone — duration formatting moved to HomeStatusFormatter + the
    // Composable layer in Phase 2C.
    private val now: Instant = Instant.parse("2026-04-29T12:00:00Z")

    private fun event(
        position: DoorPosition,
        secondsAgo: Long = 0,
        message: String? = null,
    ): DoorEvent =
        DoorEvent(
            doorPosition = position,
            lastChangeTimeSeconds = now.epochSecond - secondsAgo,
            message = message,
        )

    private val authedUser = User(
        name = DisplayName("Chris"),
        email = Email("chris@example.com"),
    )

    // (region stateLabel removed in Phase 2B — function deleted from
    //  HomeMapper. The Composable doorStateLabel(doorPosition) in
    //  HomeContent.kt does the position → string resolution at render
    //  time. New DoorPosition variants are caught by exhaustiveness on
    //  the `when` block in that Composable.)

    // (region warning was removed in the presentation-model realization
    //  (ADR-031) — the typed `DoorWarning` + its mapping moved to the shared
    //  `presentation-model` module. The contract is now guarded by
    //  `DoorWarningMapperTest` in that module's commonTest, so it runs on every
    //  platform. `DefaultHomeViewModel.warning` exposes the typed value; the
    //  Composable renders it.)

    // (regions formatDuration, formatTimeOrDate, sinceLine were removed in
    //  Phase 2C — those helpers moved to HomeStatusFormatter as pure
    //  functions and are tested in HomeStatusFormatterTest. The Composable
    //  rememberSinceLine in HomeContent.kt assembles the final localized
    //  "Since X · Y" string at render time using stringResource +
    //  pluralStringResource.)

    // region toHomeStatusDisplay

    @Test
    fun toHomeStatusDisplay_null_event_returns_unknown() {
        val display = HomeMapper.toHomeStatusDisplay(LoadingResult.Complete(null))
        assertEquals(DoorPosition.UNKNOWN, display.doorPosition)
        assertNull(display.lastChangeTimeSeconds)
        // No event at all → the calm connecting presentation, not the
        // alarming "Unknown" + warning-badge look.
        assertEquals(false, display.hasData)
    }

    @Test
    fun toHomeStatusDisplay_real_unknown_event_keeps_warning_presentation() {
        // A server-reported UNKNOWN is an actual event: hasData stays true so
        // the full warning presentation (badge + "Unknown" label) renders.
        val display = HomeMapper.toHomeStatusDisplay(
            LoadingResult.Complete(event(DoorPosition.UNKNOWN)),
        )
        assertEquals(DoorPosition.UNKNOWN, display.doorPosition)
        assertEquals(true, display.hasData)
    }

    @Test
    fun toHomeStatusDisplay_loading_with_cached_event() {
        // Loading variant should still surface its cached event data —
        // the redesigned UI already shows a pull-to-refresh spinner for
        // the loading affordance, so the status card should keep showing
        // the latest known state instead of blanking to "Unknown".
        val event = event(DoorPosition.OPEN, secondsAgo = 60 * 38)
        val display = HomeMapper.toHomeStatusDisplay(LoadingResult.Loading(event))
        assertEquals(DoorPosition.OPEN, display.doorPosition)
        assertEquals(event.lastChangeTimeSeconds, display.lastChangeTimeSeconds)
    }

    @Test
    fun toHomeStatusDisplay_error_blanks_to_unknown() {
        // Error has no cached data; the alert banner surfaces the error.
        val display = HomeMapper.toHomeStatusDisplay(
            LoadingResult.Error(RuntimeException("boom")),
        )
        assertEquals(DoorPosition.UNKNOWN, display.doorPosition)
        assertNull(display.lastChangeTimeSeconds)
    }

    @Test
    fun toHomeStatusDisplay_open_today() {
        val event = event(DoorPosition.OPEN, secondsAgo = 2 * 3_600 + 13 * 60)
        val display = HomeMapper.toHomeStatusDisplay(LoadingResult.Complete(event))
        assertEquals(DoorPosition.OPEN, display.doorPosition)
        assertEquals(event.lastChangeTimeSeconds, display.lastChangeTimeSeconds)
    }

    @Test
    fun toHomeStatusDisplay_openingTooLong_maps_position() {
        // The typed warning moved to DoorWarningMapper (shared); this mapper
        // now only carries the door position + timestamp for the status card.
        val event = event(DoorPosition.OPENING_TOO_LONG, secondsAgo = 4 * 60)
        val display = HomeMapper.toHomeStatusDisplay(LoadingResult.Complete(event))
        assertEquals(DoorPosition.OPENING_TOO_LONG, display.doorPosition)
    }

    // endregion

    // (region toHomeAlerts was removed in the presentation-model realization
    //  (ADR-031 Phase 4) — `HomeAlert` + the banner-selection logic moved to
    //  the shared `presentation-model` module. The contract is now guarded by
    //  `HomeAlertMapperTest` in that module's commonTest, so it runs on every
    //  platform. The route wrapper calls `HomeAlertMapper.toHomeAlerts`; the
    //  Composable `HomeAlertCard` resolves each typed alert to localized copy.)

    // region toHomeAuthState

    @Test
    fun toHomeAuthState_unknown() = assertEquals(HomeAuthState.Unknown, HomeMapper.toHomeAuthState(AuthState.Unknown))

    @Test
    fun toHomeAuthState_unauthenticated() = assertEquals(HomeAuthState.SignedOut, HomeMapper.toHomeAuthState(AuthState.Unauthenticated))

    @Test
    fun toHomeAuthState_authenticated() =
        assertEquals(
            HomeAuthState.SignedIn,
            HomeMapper.toHomeAuthState(AuthState.Authenticated(authedUser)),
        )

    // endregion
}
