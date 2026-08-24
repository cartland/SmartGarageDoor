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
 * How this app keeps its door state fresh while it is running.
 *
 * The constants name a BEHAVIOR, never a transport. Push on Android is
 * FCM; push on iOS is FCM→APNs — same declared behavior, different
 * plumbing, and the plumbing is a platform fact the shared layer has no
 * business asserting. This is the naming rule from `AppBuildFact`
 * (CLAUDE.md § "Shared decides, platform words it"): a constant called
 * `FCM` would tell an iOS reader the question is settled when it isn't.
 * Each platform's user-facing wording lives in its own strings.
 *
 * The value in force at runtime is
 * [com.chriscartland.garage.domain.repository.AppSettingsRepository.doorUpdateStrategy]
 * resolved against [AppConfig.defaultDoorUpdateStrategy], so a build ships
 * a default and a developer can override it live. See
 * `docs/DOOR_UPDATE_STRATEGY.md`.
 */
enum class DoorUpdateStrategyId {
    /**
     * The server pushes every door event; the client runs no timer of its
     * own. Android's behavior since the beginning, and correct there —
     * data FCM wakes the process reliably, even in Doze.
     *
     * This is also the "nothing happens on a timer" value tests want: the
     * strategy does no work, so a test that wants a quiet app selects it.
     */
    PUSH,

    /**
     * Fetch the current door event on a fixed interval while the app is
     * visible, and once immediately whenever it becomes visible. No push
     * involvement at all.
     *
     * iOS ships this today — not because polling is better, but because
     * iOS receives no door pushes at all right now, and a poll needs
     * nothing from Apple, from the server, or from a release cycle.
     */
    POLL,

    /**
     * The server pushes, AND the app refreshes once every time it becomes
     * visible. No interval timer.
     *
     * The intended iOS destination once push delivery works there. The
     * foreground refresh is not belt-and-braces: iOS budgets and throttles
     * silent pushes and drops them entirely in Low Power Mode, so "the app
     * was just opened" is the one moment worth spending a request on
     * regardless of what push did or didn't deliver.
     */
    PUSH_WITH_FOREGROUND_REFRESH,
}

/**
 * The persisted override for [DoorUpdateStrategyId] — what a developer
 * picked, which is a different question from what the app is running.
 *
 * [PLATFORM_DEFAULT] exists so that "no opinion" is representable. Storing
 * the resolved id instead would freeze whatever the default happened to be
 * on the day the user first opened the picker: iOS moving from
 * [DoorUpdateStrategyId.POLL] to
 * [DoorUpdateStrategyId.PUSH_WITH_FOREGROUND_REFRESH] in a later release
 * would then silently skip every device that had ever looked at the setting.
 */
enum class DoorUpdateStrategyOverride {
    PLATFORM_DEFAULT,
    PUSH,
    POLL,
    PUSH_WITH_FOREGROUND_REFRESH,
    ;

    /** The strategy to actually run, given this build's [platformDefault]. */
    fun resolve(platformDefault: DoorUpdateStrategyId): DoorUpdateStrategyId =
        when (this) {
            PLATFORM_DEFAULT -> platformDefault
            PUSH -> DoorUpdateStrategyId.PUSH
            POLL -> DoorUpdateStrategyId.POLL
            PUSH_WITH_FOREGROUND_REFRESH -> DoorUpdateStrategyId.PUSH_WITH_FOREGROUND_REFRESH
        }
}
