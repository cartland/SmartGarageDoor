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

/**
 * Pure mapper that decides which [HistoryAlert] banners the History tab shows
 * (ADR-031 shared presentation model), the sibling of [HomeAlertMapper]. Both
 * Jetpack Compose and SwiftUI route their History stale-banner decision through
 * this one function, so it can't diverge between platforms.
 *
 * Emits typed state only — no user-visible strings. Each UI resolves the typed
 * alerts to localized banner copy at render time.
 */
object HistoryAlertMapper {
    /**
     * Returns the banners to render above the History day list.
     *
     * @param isCheckInStale device telemetry is older than the staleness
     *   threshold (the shared `CheckInStalenessManager`) → [HistoryAlert.StaleCheckIn].
     */
    fun toHistoryAlerts(isCheckInStale: Boolean): List<HistoryAlert> =
        buildList {
            if (isCheckInStale) {
                add(HistoryAlert.StaleCheckIn)
            }
        }
}
