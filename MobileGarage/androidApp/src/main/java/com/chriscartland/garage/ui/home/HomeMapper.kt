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

/**
 * Pure-function mapper that converts the Home tab's domain inputs into the
 * stateless display data consumed by [HomeContent].
 *
 * Per the string-resource migration plan
 * (`MobileGarage/docs/PENDING_FOLLOWUPS.md` item #1, Phases 2A/2B/2C),
 * this mapper does NOT produce user-visible text. It emits typed states
 * ([HomeStatusDisplay]) and raw data (epoch seconds). The Composable layer
 * resolves typed values to localized strings via `stringResource` /
 * `pluralStringResource` at render time.
 *
 * The typed door warning moved to the shared `presentation-model`
 * (`DoorWarning` / `DoorWarningMapper`, ADR-031) and is now exposed by
 * `DefaultHomeViewModel.warning`; the Composable reads it from the VM rather
 * than from this mapper.
 *
 * The alert-banner stack (`HomeAlert` + selection logic) also moved to the
 * shared `presentation-model` (`HomeAlert` / `HomeAlertMapper`, ADR-031
 * Phase 4); the route wrapper calls `HomeAlertMapper.toHomeAlerts` directly.
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
            isStale = isCheckInStale,
        )
    }

    fun toHomeAuthState(authState: AuthState): HomeAuthState =
        when (authState) {
            AuthState.Unknown -> HomeAuthState.Unknown
            AuthState.Unauthenticated -> HomeAuthState.SignedOut
            is AuthState.Authenticated -> HomeAuthState.SignedIn
        }
}
