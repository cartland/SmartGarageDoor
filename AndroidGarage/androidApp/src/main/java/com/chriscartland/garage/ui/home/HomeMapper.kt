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
import com.chriscartland.garage.permissions.NotificationJustification

/**
 * Pure-function mapper that converts the Home tab's domain inputs into the
 * stateless display data consumed by [HomeContent].
 *
 * Per the string-resource migration plan
 * (`AndroidGarage/docs/PENDING_FOLLOWUPS.md` item #1, Phases 2A/2B/2C),
 * this mapper does NOT produce user-visible text. It emits typed states
 * ([HomeStatusDisplay], [DoorWarning], [HomeAlert]) and raw data (epoch
 * seconds, exception text). The Composable layer resolves typed values to
 * localized strings via `stringResource` / `pluralStringResource` at render
 * time.
 *
 * The "Since X · Y" status line is now built by the production HomeContent
 * wrapper (Composable scope) using [HomeStatusFormatter] + plurals, then
 * passed into the stateless `HomeContent` Composable as a separate
 * `sinceLine: String` parameter.
 *
 * Mirrors the `HistoryMapper` pattern from PR #598.
 */
object HomeMapper {
    /**
     * @param currentDoorEvent latest door event flow value (Loading/Complete/Error).
     *   Loading and Complete may carry a cached event; Error has no data.
     * @param isCheckInStale picks the muted door-color variant when the
     *   device hasn't checked in recently.
     */
    fun toHomeStatusDisplay(
        currentDoorEvent: LoadingResult<DoorEvent?>,
        isCheckInStale: Boolean = false,
    ): HomeStatusDisplay {
        val event = currentDoorEvent.data
        val doorPosition = event?.doorPosition ?: DoorPosition.UNKNOWN
        return HomeStatusDisplay(
            doorPosition = doorPosition,
            lastChangeTimeSeconds = event?.lastChangeTimeSeconds,
            warning = warning(event),
            isStale = isCheckInStale,
        )
    }

    /**
     * Returns the alerts to render in the banner stack above the Status card,
     * in display order: stale first, then permission, then fetch error.
     *
     * Phase 2D — `HomeAlert.Stale` and `HomeAlert.FetchError` no longer carry
     * default user-visible strings. The Composable resolves the alert TYPE
     * to a localized resource. `HomeAlert.FetchError.truncatedException`
     * carries only the raw exception text (data, not a label) for the
     * Composable to interpolate via `formatArgs`.
     *
     * `HomeAlert.PermissionMissing.justification` (Phase 2F) carries the
     * typed [NotificationJustification] shape; the Composable layer
     * assembles the multi-line localized message from
     * [NotificationJustification.attemptCount] at render time.
     */
    fun toHomeAlerts(
        currentDoorEvent: LoadingResult<DoorEvent?>,
        isCheckInStale: Boolean,
        notificationPermissionGranted: Boolean,
        notificationRequestCount: Int,
    ): List<HomeAlert> =
        buildList {
            if (isCheckInStale) {
                add(HomeAlert.Stale)
            }
            if (!notificationPermissionGranted) {
                add(
                    HomeAlert.PermissionMissing(
                        justification = NotificationJustification(notificationRequestCount),
                    ),
                )
            }
            if (currentDoorEvent is LoadingResult.Error) {
                add(
                    HomeAlert.FetchError(
                        truncatedException = currentDoorEvent.exception
                            .toString()
                            .take(MAX_ERROR_MESSAGE_LEN),
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

    /**
     * Returns the typed warning to surface inside the Status card for stuck
     * or anomalous states. Prefers the server-supplied message
     * ([DoorWarning.ServerMessage]); falls back to a typed enum case per
     * [DoorPosition] so the Composable can render a localized string from
     * `strings.xml` when the server sends nothing.
     */
    internal fun warning(event: DoorEvent?): DoorWarning? {
        if (event == null) return null
        val message = event.message?.takeIf { it.isNotBlank() }
        return when (event.doorPosition) {
            DoorPosition.OPENING_TOO_LONG ->
                message?.let(DoorWarning::ServerMessage) ?: DoorWarning.OpeningTooLong
            DoorPosition.CLOSING_TOO_LONG ->
                message?.let(DoorWarning::ServerMessage) ?: DoorWarning.ClosingTooLong
            DoorPosition.OPEN_MISALIGNED ->
                message?.let(DoorWarning::ServerMessage) ?: DoorWarning.OpenMisaligned
            DoorPosition.ERROR_SENSOR_CONFLICT ->
                message?.let(DoorWarning::ServerMessage) ?: DoorWarning.SensorConflict
            DoorPosition.UNKNOWN -> message?.let(DoorWarning::ServerMessage)
            else -> null
        }
    }

    private const val MAX_ERROR_MESSAGE_LEN = 500
}
