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
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.permissions.NotificationPermissionCopy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pure-function mapper that converts the Home tab's domain inputs into the
 * stateless display data consumed by [HomeContent]. All time/duration
 * formatting, alert assembly, and friendly-name decisions live here so the
 * Composable carries no logic and the entire pipeline is unit-testable.
 *
 * Mirrors the `HistoryMapper` pattern from PR #598.
 */
object HomeMapper {
    /**
     * @param currentDoorEvent latest door event flow value (Loading/Complete/Error).
     *   Loading and Complete may carry a cached event; Error has no data.
     * @param now used for "Since X · Y" duration computation.
     * @param zone used to compute the local-day boundary for whether to show
     *   "9:47 AM" vs. "Apr 28, 9:47 PM".
     */
    fun toHomeStatusDisplay(
        currentDoorEvent: LoadingResult<DoorEvent?>,
        now: Instant,
        zone: ZoneId,
    ): HomeStatusDisplay {
        val event = currentDoorEvent.data
        val doorPosition = event?.doorPosition ?: DoorPosition.UNKNOWN
        return HomeStatusDisplay(
            doorPosition = doorPosition,
            stateLabel = stateLabel(doorPosition),
            sinceLine = sinceLine(event?.lastChangeTimeSeconds, now, zone),
            warning = warning(event),
        )
    }

    /**
     * Returns the alerts to render in the banner stack above the Status card,
     * in display order: stale first, then permission, then fetch error.
     *
     * @param notificationRequestCount drives the [NotificationPermissionCopy]
     *   justification text variants — same source the legacy ErrorCard used.
     */
    fun toHomeAlerts(
        currentDoorEvent: LoadingResult<DoorEvent?>,
        isCheckInStale: Boolean,
        notificationPermissionGranted: Boolean,
        notificationRequestCount: Int,
    ): List<HomeAlert> =
        buildList {
            if (isCheckInStale) {
                add(HomeAlert.Stale())
            }
            if (!notificationPermissionGranted) {
                add(
                    HomeAlert.PermissionMissing(
                        message = NotificationPermissionCopy.justificationText(notificationRequestCount),
                    ),
                )
            }
            if (currentDoorEvent is LoadingResult.Error) {
                add(
                    HomeAlert.FetchError(
                        message = "Error fetching current door event: " +
                            currentDoorEvent.exception.toString().take(MAX_ERROR_MESSAGE_LEN),
                    ),
                )
            }
        }

    fun toHomeAuthState(authState: AuthState): HomeAuthState =
        when (authState) {
            AuthState.Unknown -> HomeAuthState.Unknown
            AuthState.Unauthenticated -> HomeAuthState.SignedOut
            is AuthState.Authenticated -> HomeAuthState.SignedIn
        }

    internal fun stateLabel(doorPosition: DoorPosition): String =
        when (doorPosition) {
            DoorPosition.OPEN -> "Open"
            DoorPosition.CLOSED -> "Closed"
            DoorPosition.UNKNOWN -> "Unknown"
            DoorPosition.OPENING -> "Opening"
            DoorPosition.OPENING_TOO_LONG -> "Opening"
            DoorPosition.OPEN_MISALIGNED -> "Open"
            DoorPosition.CLOSING -> "Closing"
            DoorPosition.CLOSING_TOO_LONG -> "Closing"
            DoorPosition.ERROR_SENSOR_CONFLICT -> "Sensor conflict"
        }

    /**
     * Returns the warning string to surface inside the Status card for stuck
     * or anomalous states. Prefers the server-supplied message; falls back
     * to a fixed string per [DoorPosition] so the chip always says something
     * useful when shown.
     */
    internal fun warning(event: DoorEvent?): String? {
        if (event == null) return null
        val message = event.message?.takeIf { it.isNotBlank() }
        return when (event.doorPosition) {
            DoorPosition.OPENING_TOO_LONG ->
                message ?: "Opening — taking longer than expected"
            DoorPosition.CLOSING_TOO_LONG ->
                message ?: "Closing — taking longer than expected"
            DoorPosition.OPEN_MISALIGNED ->
                message ?: "Door is open and misaligned"
            DoorPosition.ERROR_SENSOR_CONFLICT ->
                message ?: "Sensor conflict — check the door"
            DoorPosition.UNKNOWN -> message
            else -> null
        }
    }

    internal fun sinceLine(
        timeSeconds: Long?,
        now: Instant,
        zone: ZoneId,
    ): String {
        if (timeSeconds == null) return "Last change time unknown"
        val instant = Instant.ofEpochSecond(timeSeconds)
        val timeText = formatTimeOrDate(instant, now, zone)
        val durationText = formatDuration(now.epochSecond - timeSeconds)
        return "Since $timeText · $durationText"
    }

    /**
     * Same-day → "9:47 AM"; different day → "Apr 28, 9:47 PM".
     */
    internal fun formatTimeOrDate(
        instant: Instant,
        now: Instant,
        zone: ZoneId,
    ): String {
        val zonedTime = instant.atZone(zone)
        val zonedNow = now.atZone(zone)
        val sameDay = zonedTime.toLocalDate() == zonedNow.toLocalDate()
        return zonedTime.format(if (sameDay) TIME_ONLY else DATE_AND_TIME)
    }

    /**
     * Duration between the door's last change and `now`, formatted compactly
     * for the "Since X · Y" line. Negative inputs are clamped to zero so
     * clock skew can't produce "-3 sec".
     */
    internal fun formatDuration(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0L)
        val days = s / SECONDS_PER_DAY
        val hours = (s % SECONDS_PER_DAY) / SECONDS_PER_HOUR
        val minutes = (s % SECONDS_PER_HOUR) / SECONDS_PER_MIN
        val seconds = s % SECONDS_PER_MIN
        return when {
            days >= 2 -> "$days days"
            days == 1L -> "1 day"
            hours >= 1 -> "$hours hr $minutes min"
            minutes >= 1 -> "$minutes min"
            else -> "$seconds sec"
        }
    }

    private val TIME_ONLY = DateTimeFormatter.ofPattern("h:mm a")
    private val DATE_AND_TIME = DateTimeFormatter.ofPattern("MMM d, h:mm a")
    private const val SECONDS_PER_MIN = 60L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val SECONDS_PER_DAY = 86_400L
    private const val MAX_ERROR_MESSAGE_LEN = 500
}
