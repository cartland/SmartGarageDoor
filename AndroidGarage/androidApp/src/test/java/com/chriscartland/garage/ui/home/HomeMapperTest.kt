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
import java.time.ZoneOffset

class HomeMapperTest {
    private val zone = ZoneOffset.UTC

    // 2026-04-29 12:00:00 UTC
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

    // region stateLabel

    @Test
    fun stateLabel_open() = assertEquals("Open", HomeMapper.stateLabel(DoorPosition.OPEN))

    @Test
    fun stateLabel_closed() = assertEquals("Closed", HomeMapper.stateLabel(DoorPosition.CLOSED))

    @Test
    fun stateLabel_unknown() = assertEquals("Unknown", HomeMapper.stateLabel(DoorPosition.UNKNOWN))

    @Test
    fun stateLabel_opening() = assertEquals("Opening", HomeMapper.stateLabel(DoorPosition.OPENING))

    @Test
    fun stateLabel_openingTooLong_collapsesTo_opening() = assertEquals("Opening", HomeMapper.stateLabel(DoorPosition.OPENING_TOO_LONG))

    @Test
    fun stateLabel_openMisaligned_collapsesTo_open() = assertEquals("Open", HomeMapper.stateLabel(DoorPosition.OPEN_MISALIGNED))

    @Test
    fun stateLabel_closing() = assertEquals("Closing", HomeMapper.stateLabel(DoorPosition.CLOSING))

    @Test
    fun stateLabel_closingTooLong_collapsesTo_closing() = assertEquals("Closing", HomeMapper.stateLabel(DoorPosition.CLOSING_TOO_LONG))

    @Test
    fun stateLabel_sensorConflict() = assertEquals("Sensor conflict", HomeMapper.stateLabel(DoorPosition.ERROR_SENSOR_CONFLICT))

    @Test
    fun stateLabel_coversAllDoorPositionVariants() {
        // Catch new DoorPosition variants that forget to update the mapper.
        DoorPosition.entries.forEach { p ->
            val label = HomeMapper.stateLabel(p)
            assertTrue("Empty label for $p", label.isNotBlank())
        }
    }

    // endregion

    // region warning

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
        assertEquals("Specific server text", w)
    }

    @Test
    fun warning_openingTooLong_falls_back_to_default_when_message_null() {
        val w = HomeMapper.warning(DoorEvent(doorPosition = DoorPosition.OPENING_TOO_LONG))
        assertEquals("Opening — taking longer than expected", w)
    }

    @Test
    fun warning_openingTooLong_falls_back_to_default_when_message_blank() {
        val w = HomeMapper.warning(
            DoorEvent(doorPosition = DoorPosition.OPENING_TOO_LONG, message = "   "),
        )
        assertEquals("Opening — taking longer than expected", w)
    }

    @Test
    fun warning_closingTooLong_default() {
        val w = HomeMapper.warning(DoorEvent(doorPosition = DoorPosition.CLOSING_TOO_LONG))
        assertEquals("Closing — taking longer than expected", w)
    }

    @Test
    fun warning_openMisaligned_default() {
        val w = HomeMapper.warning(DoorEvent(doorPosition = DoorPosition.OPEN_MISALIGNED))
        assertEquals("Door is open and misaligned", w)
    }

    @Test
    fun warning_sensorConflict_default() {
        val w = HomeMapper.warning(DoorEvent(doorPosition = DoorPosition.ERROR_SENSOR_CONFLICT))
        assertEquals("Sensor conflict — check the door", w)
    }

    @Test
    fun warning_unknown_uses_message_only_no_default() {
        // Unknown is too vague for a fixed default, so only the server's
        // own message is surfaced — and only when non-blank.
        assertNull(HomeMapper.warning(DoorEvent(doorPosition = DoorPosition.UNKNOWN)))
        assertEquals(
            "Server says X",
            HomeMapper.warning(DoorEvent(doorPosition = DoorPosition.UNKNOWN, message = "Server says X")),
        )
    }

    // endregion

    // region formatDuration

    @Test
    fun formatDuration_zero() = assertEquals("0 sec", HomeMapper.formatDuration(0))

    @Test
    fun formatDuration_negative_clamped() = assertEquals("0 sec", HomeMapper.formatDuration(-100))

    @Test
    fun formatDuration_seconds_under_minute() {
        assertEquals("1 sec", HomeMapper.formatDuration(1))
        assertEquals("38 sec", HomeMapper.formatDuration(38))
        assertEquals("59 sec", HomeMapper.formatDuration(59))
    }

    @Test
    fun formatDuration_minutes_under_hour() {
        assertEquals("1 min", HomeMapper.formatDuration(60))
        assertEquals("4 min", HomeMapper.formatDuration(60L * 4))
        assertEquals("38 min", HomeMapper.formatDuration(60L * 38 + 30)) // partial seconds dropped
        assertEquals("59 min", HomeMapper.formatDuration(60L * 59 + 59))
    }

    @Test
    fun formatDuration_hours_show_minutes_too() {
        assertEquals("1 hr 0 min", HomeMapper.formatDuration(3_600))
        assertEquals("2 hr 14 min", HomeMapper.formatDuration(2 * 3_600L + 14 * 60))
        assertEquals("23 hr 59 min", HomeMapper.formatDuration(23 * 3_600L + 59 * 60))
    }

    @Test
    fun formatDuration_one_day() {
        assertEquals("1 day", HomeMapper.formatDuration(86_400))
        assertEquals("1 day", HomeMapper.formatDuration(86_400 + 5 * 3_600))
    }

    @Test
    fun formatDuration_two_days_plural() {
        assertEquals("2 days", HomeMapper.formatDuration(2 * 86_400L))
        assertEquals("7 days", HomeMapper.formatDuration(7 * 86_400L))
    }

    // endregion

    // region formatTimeOrDate

    @Test
    fun formatTimeOrDate_sameDay_shows_only_time() {
        // 9:47 AM UTC on 2026-04-29.
        val instant = Instant.parse("2026-04-29T09:47:00Z")
        assertEquals("9:47 AM", HomeMapper.formatTimeOrDate(instant, now, zone))
    }

    @Test
    fun formatTimeOrDate_sameDay_pm() {
        val instant = Instant.parse("2026-04-29T11:22:00Z")
        assertEquals("11:22 AM", HomeMapper.formatTimeOrDate(instant, now, zone))
    }

    @Test
    fun formatTimeOrDate_differentDay_shows_month_day_and_time() {
        val instant = Instant.parse("2026-04-28T21:47:00Z")
        assertEquals("Apr 28, 9:47 PM", HomeMapper.formatTimeOrDate(instant, now, zone))
    }

    @Test
    fun formatTimeOrDate_differentMonth() {
        val instant = Instant.parse("2026-03-15T08:05:00Z")
        assertEquals("Mar 15, 8:05 AM", HomeMapper.formatTimeOrDate(instant, now, zone))
    }

    // endregion

    // region sinceLine

    @Test
    fun sinceLine_null_timestamp() {
        assertEquals("Last change time unknown", HomeMapper.sinceLine(null, now, zone))
    }

    @Test
    fun sinceLine_today_minutes_ago() {
        val timeSeconds = Instant.parse("2026-04-29T11:22:00Z").epochSecond
        assertEquals("Since 11:22 AM · 38 min", HomeMapper.sinceLine(timeSeconds, now, zone))
    }

    @Test
    fun sinceLine_today_hours_ago() {
        // 9:47 AM today, now is 12:00 PM → 2 hr 13 min
        val timeSeconds = Instant.parse("2026-04-29T09:47:00Z").epochSecond
        assertEquals("Since 9:47 AM · 2 hr 13 min", HomeMapper.sinceLine(timeSeconds, now, zone))
    }

    @Test
    fun sinceLine_yesterday() {
        // Apr 28 9:47 PM → 14 hr 13 min vs Apr 29 12:00 PM
        val timeSeconds = Instant.parse("2026-04-28T21:47:00Z").epochSecond
        assertEquals("Since Apr 28, 9:47 PM · 14 hr 13 min", HomeMapper.sinceLine(timeSeconds, now, zone))
    }

    @Test
    fun sinceLine_two_days_ago() {
        val timeSeconds = Instant.parse("2026-04-27T12:00:00Z").epochSecond
        assertEquals("Since Apr 27, 12:00 PM · 2 days", HomeMapper.sinceLine(timeSeconds, now, zone))
    }

    @Test
    fun sinceLine_negative_clock_skew_clamped_to_zero() {
        // Door event timestamped IN THE FUTURE relative to now (clock skew).
        // Should not produce "-3 sec".
        val timeSeconds = now.epochSecond + 3
        val result = HomeMapper.sinceLine(timeSeconds, now, zone)
        assertTrue("Got: $result", result.endsWith("0 sec"))
    }

    // endregion

    // region toHomeStatusDisplay

    @Test
    fun toHomeStatusDisplay_null_event_returns_unknown() {
        val display = HomeMapper.toHomeStatusDisplay(LoadingResult.Complete(null), now, zone)
        assertEquals(DoorPosition.UNKNOWN, display.doorPosition)
        assertEquals("Unknown", display.stateLabel)
        assertEquals("Last change time unknown", display.sinceLine)
        assertNull(display.warning)
    }

    @Test
    fun toHomeStatusDisplay_loading_with_cached_event() {
        // Loading variant should still surface its cached event data —
        // the redesigned UI already shows a pull-to-refresh spinner for
        // the loading affordance, so the status card should keep showing
        // the latest known state instead of blanking to "Unknown".
        val event = event(DoorPosition.OPEN, secondsAgo = 60 * 38)
        val display = HomeMapper.toHomeStatusDisplay(LoadingResult.Loading(event), now, zone)
        assertEquals(DoorPosition.OPEN, display.doorPosition)
        assertEquals("Open", display.stateLabel)
        assertTrue(display.sinceLine.startsWith("Since "))
    }

    @Test
    fun toHomeStatusDisplay_error_blanks_to_unknown() {
        // Error has no cached data; the alert banner surfaces the error.
        val display = HomeMapper.toHomeStatusDisplay(
            LoadingResult.Error(RuntimeException("boom")),
            now,
            zone,
        )
        assertEquals(DoorPosition.UNKNOWN, display.doorPosition)
    }

    @Test
    fun toHomeStatusDisplay_open_today() {
        val event = event(DoorPosition.OPEN, secondsAgo = 2 * 3_600 + 13 * 60)
        val display = HomeMapper.toHomeStatusDisplay(LoadingResult.Complete(event), now, zone)
        assertEquals(DoorPosition.OPEN, display.doorPosition)
        assertEquals("Open", display.stateLabel)
        assertTrue(display.sinceLine.contains("2 hr 13 min"))
        assertNull(display.warning)
    }

    @Test
    fun toHomeStatusDisplay_openingTooLong_surfaces_warning() {
        val event = event(DoorPosition.OPENING_TOO_LONG, secondsAgo = 4 * 60)
        val display = HomeMapper.toHomeStatusDisplay(LoadingResult.Complete(event), now, zone)
        assertEquals("Opening", display.stateLabel)
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
        assertTrue(alerts[0] is HomeAlert.Stale)
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
        assertTrue(alerts[0] is HomeAlert.PermissionMissing)
        // The justification text should not be empty.
        val pm = alerts[0] as HomeAlert.PermissionMissing
        assertTrue(pm.message.isNotBlank())
    }

    @Test
    fun toHomeAlerts_permission_message_grows_with_attempts() {
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
        assertTrue(manyAttempts.message.length > firstAttempt.message.length)
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
        assertTrue(a.message.contains("boom"))
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
        // Prefix + truncated tail, total bounded.
        assertTrue("Got len=${a.message.length}", a.message.length <= 600)
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
