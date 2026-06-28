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

package com.chriscartland.garage.presentation

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.LoadingResult

/**
 * Pure mapper that decides which [HomeAlert] banners the Home tab shows
 * (ADR-031 shared presentation model). Moved out of `androidApp/`'s `HomeMapper`
 * so Jetpack Compose and SwiftUI share the banner-selection logic.
 *
 * Emits typed state only — no user-visible strings. Each UI resolves the typed
 * alerts to localized banner copy at render time.
 */
object HomeAlertMapper {
    /**
     * Returns the banners to render above the Status card, in display order:
     * stale first, then permission, then fetch error.
     *
     * @param currentDoorEvent latest door-event flow value; an
     *   [LoadingResult.Error] surfaces a [HomeAlert.FetchError]. A `Loading`
     *   value never emits a fetch error (the previous good value is still shown).
     * @param isCheckInStale device telemetry is older than the staleness
     *   threshold → [HomeAlert.Stale].
     * @param notificationPermissionGranted resolved per-UI (Android runtime
     *   permission / iOS `UNUserNotificationCenter`); `false` →
     *   [HomeAlert.PermissionMissing].
     * @param notificationRequestCount how many times the user tapped the
     *   permission banner's action this session; passed through verbatim into
     *   [HomeAlert.PermissionMissing.attemptCount] for the escalation copy.
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
                add(HomeAlert.PermissionMissing(attemptCount = notificationRequestCount))
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

    private const val MAX_ERROR_MESSAGE_LEN = 500
}
