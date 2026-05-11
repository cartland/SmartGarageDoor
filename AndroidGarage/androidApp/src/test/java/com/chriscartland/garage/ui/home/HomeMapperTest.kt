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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // region warning
    // Phase 2A of the string-resource migration: warning() returns a typed
    // DoorWarning?, not a String?. Tests assert on type so a copy revision
    // doesn't break them.

    @Test
    fun warning_null_event_returns_null() = assertNull(HomeMapper.warning(null))

    @Test
    fun warning_open_no_warning() = assertNull(HomeMapper.warning(DoorEvent(doorPosition = DoorPosition.OPEN, message = "anything")))

    @Test
    fun warning_closed_no_warning() = assertNull(HomeMapper.warning(DoorEvent(doorPosition = DoorPosition.CLOSED, message = "anything")))

    @Test
    fun warning_opening_no_warning() = assertNull(HomeMapper.warning(DoorEvent(doorPosition = DoorPosition.OPENING)))

    @Test
    fun warning_closing_no_warning() = assertNull(HomeMapper.warning(DoorEvent(doorPosition = DoorPosition.CLOSING)))

    @Test
    fun warning_openingTooLong_uses_server_message_when_present() {
        val w = HomeMapper.warning(
            DoorEvent(doorPosition = DoorPosition.OPENING_TOO_LONG, message = "Specific server text"),
        )
        assertEquals(DoorWarning.ServerMessage("Specific server text"), w)
    }

    @Test
    fun warning_openingTooLong_falls_back_to_typed_default_when_message_null() {
        val w = HomeMapper.warning(DoorEvent(doorPosition = DoorPosition.OPENING_TOO_LONG))
        assertEquals(DoorWarning.OpeningTooLong, w)
    }

    @Test
    fun warning_openingTooLong_falls_back_to_typed_default_when_message_blank() {
        val w = HomeMapper.warning(
            DoorEvent(doorPosition = DoorPosition.OPENING_TOO_LONG, message = "   "),
        )
        assertEquals(DoorWarning.OpeningTooLong, w)
    }

    @Test
    fun warning_closingTooLong_default() {
        val w = HomeMapper.warning(DoorEvent(doorPosition = DoorPosition.CLOSING_TOO_LONG))
        assertEquals(DoorWarning.ClosingTooLong, w)
    }

    @Test
    fun warning_openMisaligned_default() {
        val w = HomeMapper.warning(DoorEvent(doorPosition = DoorPosition.OPEN_MISALIGNED))
        assertEquals(DoorWarning.OpenMisaligned, w)
    }

    @Test
    fun warning_sensorConflict_default() {
        val w = HomeMapper.warning(DoorEvent(doorPosition = DoorPosition.ERROR_SENSOR_CONFLICT))
        assertEquals(DoorWarning.SensorConflict, w)
    }

    @Test
    fun warning_unknown_uses_server_message_only_no_default() {
        // Unknown is too vague for a fixed default, so only the server's
        // own message is surfaced — and only when non-blank.
        assertNull(HomeMapper.warning(DoorEvent(doorPosition = DoorPosition.UNKNOWN)))
        assertEquals(
            DoorWarning.ServerMessage("Server says X"),
            HomeMapper.warning(DoorEvent(doorPosition = DoorPosition.UNKNOWN, message = "Server says X")),
        )
    }

    // endregion

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
        assertNull(display.warning)
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
        assertNull(display.warning)
    }

    @Test
    fun toHomeStatusDisplay_openingTooLong_surfaces_warning() {
        val event = event(DoorPosition.OPENING_TOO_LONG, secondsAgo = 4 * 60)
        val display = HomeMapper.toHomeStatusDisplay(LoadingResult.Complete(event))
        assertEquals(DoorPosition.OPENING_TOO_LONG, display.doorPosition)
        assertNotNull(display.warning)
    }

    // endregion

    // region toHomeAlerts

    @Test
    fun toHomeAlerts_clean_state_empty() {
        val alerts = HomeMapper.toHomeAlerts(
            currentDoorEvent = LoadingResult.Complete(event(DoorPosition.OPEN)),
            isCheckInStale = false,
            notificationPermissionGranted = true,
            notificationRequestCount = 0,
        )
        assertEquals(emptyList<HomeAlert>(), alerts)
    }

    @Test
    fun toHomeAlerts_stale_only() {
        val alerts = HomeMapper.toHomeAlerts(
            currentDoorEvent = LoadingResult.Complete(event(DoorPosition.OPEN)),
            isCheckInStale = true,
            notificationPermissionGranted = true,
            notificationRequestCount = 0,
        )
        assertEquals(1, alerts.size)
        // Phase 2D: HomeAlert.Stale is now a `data object`, no message field.
        assertEquals(HomeAlert.Stale, alerts[0])
    }

    @Test
    fun toHomeAlerts_permission_only() {
        val alerts = HomeMapper.toHomeAlerts(
            currentDoorEvent = LoadingResult.Complete(event(DoorPosition.OPEN)),
            isCheckInStale = false,
            notificationPermissionGranted = false,
            notificationRequestCount = 0,
        )
        assertEquals(1, alerts.size)
        // Phase 2F: PermissionMissing carries a typed NotificationJustification
        // instead of a String message. The Composable layer renders the
        // multi-line localized message at the call site.
        val pm = alerts[0] as HomeAlert.PermissionMissing
        assertEquals(0, pm.justification.attemptCount)
    }

    @Test
    fun toHomeAlerts_permission_attemptCount_passes_through() {
        val firstAttempt = HomeMapper
            .toHomeAlerts(
                currentDoorEvent = LoadingResult.Complete(event(DoorPosition.OPEN)),
                isCheckInStale = false,
                notificationPermissionGranted = false,
                notificationRequestCount = 0,
            ).first() as HomeAlert.PermissionMissing
        val manyAttempts = HomeMapper
            .toHomeAlerts(
                currentDoorEvent = LoadingResult.Complete(event(DoorPosition.OPEN)),
                isCheckInStale = false,
                notificationPermissionGranted = false,
                notificationRequestCount = 5,
            ).first() as HomeAlert.PermissionMissing
        // Mapper passes the count through verbatim; the Composable's
        // notificationJustificationText resolver appends escalation lines
        // at counts 3+, 4+, 5+.
        assertEquals(0, firstAttempt.justification.attemptCount)
        assertEquals(5, manyAttempts.justification.attemptCount)
    }

    @Test
    fun toHomeAlerts_fetch_error_only() {
        val alerts = HomeMapper.toHomeAlerts(
            currentDoorEvent = LoadingResult.Error(RuntimeException("boom")),
            isCheckInStale = false,
            notificationPermissionGranted = true,
            notificationRequestCount = 0,
        )
        assertEquals(1, alerts.size)
        val a = alerts[0] as HomeAlert.FetchError
        // Phase 2D: FetchError now carries `truncatedException` (raw exception
        // text only); the Composable interpolates it into the localized
        // "Error fetching ..." string at render time.
        assertTrue(a.truncatedException.contains("boom"))
    }

    @Test
    fun toHomeAlerts_error_message_truncated_to_500() {
        val long = "x".repeat(2_000)
        val alerts = HomeMapper.toHomeAlerts(
            currentDoorEvent = LoadingResult.Error(RuntimeException(long)),
            isCheckInStale = false,
            notificationPermissionGranted = true,
            notificationRequestCount = 0,
        )
        val a = alerts[0] as HomeAlert.FetchError
        // truncatedException is bounded to 500 chars; the localized prefix
        // is added by the Composable, not stored in the typed alert.
        assertTrue("Got len=${a.truncatedException.length}", a.truncatedException.length <= 500)
    }

    @Test
    fun toHomeAlerts_all_three_in_documented_order() {
        val alerts = HomeMapper.toHomeAlerts(
            currentDoorEvent = LoadingResult.Error(RuntimeException("boom")),
            isCheckInStale = true,
            notificationPermissionGranted = false,
            notificationRequestCount = 0,
        )
        assertEquals(3, alerts.size)
        assertTrue("[0] should be Stale", alerts[0] is HomeAlert.Stale)
        assertTrue("[1] should be PermissionMissing", alerts[1] is HomeAlert.PermissionMissing)
        assertTrue("[2] should be FetchError", alerts[2] is HomeAlert.FetchError)
    }

    @Test
    fun toHomeAlerts_loading_state_does_not_emit_fetch_error() {
        val alerts = HomeMapper.toHomeAlerts(
            currentDoorEvent = LoadingResult.Loading(null),
            isCheckInStale = false,
            notificationPermissionGranted = true,
            notificationRequestCount = 0,
        )
        assertEquals(emptyList<HomeAlert>(), alerts)
    }

    // endregion

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
