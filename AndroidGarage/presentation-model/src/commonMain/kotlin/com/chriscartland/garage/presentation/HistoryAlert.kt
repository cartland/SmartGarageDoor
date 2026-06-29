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

/*
 * Typed display state for the History tab's alert-banner stack (ADR-031 shared
 * presentation model), the sibling of [HomeAlert]. Both Jetpack Compose and
 * SwiftUI render the same banners from one source of truth, so the show/hide
 * decision (in [HistoryAlertMapper]) can't diverge between platforms.
 *
 * Everything here is typed, never a user-visible string: each UI assembles the
 * localized banner message + action label at render time (Compose
 * stringResource; SwiftUI literals).
 */

/**
 * One banner in the History tab's alert stack, rendered above the day list.
 *
 * Mirrors [HomeAlert.Stale] but scoped to History, where the recovery action
 * resets the FCM subscription (deregister, so it re-subscribes fresh) and
 * refetches — the "reset FCM" recovery.
 */
sealed interface HistoryAlert {
    /**
     * Device telemetry hasn't arrived recently — the door history may be
     * outdated. Each UI resolves the message + action label to a localized
     * string; the action deregisters FCM and refetches recent events.
     */
    data object StaleCheckIn : HistoryAlert
}
