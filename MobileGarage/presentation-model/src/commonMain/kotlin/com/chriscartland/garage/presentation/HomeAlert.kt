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
 * Typed display state for the Home tab's alert-banner stack (ADR-031 shared
 * presentation model). Moved out of `androidApp/.../ui/home/` so both Jetpack
 * Compose and SwiftUI render the same banners from one source of truth.
 *
 * Everything here is typed, never a user-visible string: the permission alert
 * carries only the raw attempt count, the fetch-error alert carries only the
 * raw (length-bounded) exception text. Each UI assembles the localized banner
 * message + action label at render time (Compose stringResource; SwiftUI
 * literals). The decision of WHICH banners to show — and in what order — lives
 * in [HomeAlertMapper].
 */

/**
 * One banner in the Home tab's alert stack, rendered above the Status card.
 *
 * Display order (decided by [HomeAlertMapper.toHomeAlerts]): [Stale] first,
 * then [PermissionMissing], then [FetchError].
 */
sealed interface HomeAlert {
    /**
     * Door telemetry hasn't arrived recently — the server may be unreachable.
     * Each UI resolves the message + action label to a localized string. The
     * action retries (Android re-registers FCM + refetches; iOS refetches).
     */
    data object Stale : HomeAlert

    /**
     * Notification permission is denied / never asked. [attemptCount] is the
     * number of times the user has tapped the banner's action this session; it
     * drives the escalation lines (3+, 4+, 5+) each UI appends to the base
     * message at render time. The permission *detection* stays per-UI (Android
     * runtime permission vs iOS `UNUserNotificationCenter`); the shared layer
     * is driven by a "permission granted" boolean.
     */
    data class PermissionMissing(
        val attemptCount: Int,
    ) : HomeAlert

    /**
     * A door-event fetch failed. [truncatedException] carries the raw,
     * length-bounded exception text — each UI interpolates it into its
     * localized "Error fetching ..." string.
     */
    data class FetchError(
        val truncatedException: String,
    ) : HomeAlert
}
