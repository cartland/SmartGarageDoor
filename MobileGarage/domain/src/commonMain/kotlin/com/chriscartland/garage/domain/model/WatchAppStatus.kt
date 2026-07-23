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

package com.chriscartland.garage.domain.model

/**
 * Whether the paired watch has the Wear OS app installed.
 *
 * Detection is capability-based: the watch app declares
 * `WearCompanionRepository.WATCH_APP_CAPABILITY` in its Wear resources, and
 * the phone queries the Wearable Data Layer for nodes advertising it.
 */
sealed interface WatchAppStatus {
    /** Initial state before the first Data Layer query resolves. */
    data object Unknown : WatchAppStatus

    /**
     * This platform cannot query watches at all (iOS, or a device without
     * the Play services Wearable module). Terminal — no watch UI shown.
     */
    data object Unavailable : WatchAppStatus

    /** The Wearable API works but no watch is currently connected. */
    data object NoWatch : WatchAppStatus

    /** A watch is connected but does not have the app installed. */
    data object WatchNeedsApp : WatchAppStatus

    /** At least one paired watch has the app installed. */
    data object InstalledOnWatch : WatchAppStatus
}

/**
 * Result of asking the platform to open the app's Play Store listing on
 * the paired watch (remote install flow).
 */
sealed interface WatchInstallResult {
    /** The Play Store listing was launched on at least one watch. */
    data object OpenedOnWatch : WatchInstallResult

    /** No connected watch to target. */
    data object NoWatchReachable : WatchInstallResult

    /** The remote launch was attempted but failed. */
    data object Failed : WatchInstallResult
}

/**
 * UI-facing state for the install-on-watch action, owned by the Settings
 * screen ViewModel. Mirrors the [SnoozeAction] transient-action pattern:
 * `Idle -> Sending -> (OpenedOnWatch | Failed) -> Idle` (auto-reset).
 */
sealed interface WatchInstallAction {
    data object Idle : WatchInstallAction

    data object Sending : WatchInstallAction

    data object OpenedOnWatch : WatchInstallAction

    /** Remote launch failed or no watch was reachable. */
    data object Failed : WatchInstallAction
}
