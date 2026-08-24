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
 * **This enum is a vocabulary, not a framework.** Each constant states a
 * POLICY — what an implementation must promise — and deliberately says
 * nothing about how to achieve it. Implement it wherever it fits: an
 * app-scoped manager, a ViewModel, a platform lifecycle callback, or
 * nothing at all when the platform gets the behavior for free. Two
 * implementations of the same constant are not duplication to be cleaned
 * up; they are two hosts with different constraints agreeing on what they
 * promise.
 *
 * That is already the situation. `PUSH` on the phone is implemented by
 * the platform's FCM handler plus a strategy object that waits;
 * `POLL` on iOS is implemented by `DoorUpdateManager` +
 * `PollDoorUpdateStrategy`, app-scoped, and by `WearHomeViewModel` on
 * the watch, screen-scoped with its own cadence, because the cadence
 * depends on ViewModel state an app-scoped manager cannot read. Both
 * honestly declare `POLL`.
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
     * **Promise:** door events arrive because the server sends them. The
     * client starts no timer and makes no request of its own.
     *
     * Android's behavior since the beginning, and correct there — data
     * FCM wakes the process reliably, even in Doze. Unavailable on any
     * platform with no push registration (the watch has none).
     *
     * This is also the "nothing happens on a timer" value tests want: an
     * implementation does no work, so a test that wants a quiet app
     * selects it.
     */
    PUSH,

    /**
     * **Promise:** while the user can see the door, its state is refetched
     * on an interval, and once immediately on becoming visible. While they
     * cannot see it, nothing runs. No push involvement at all.
     *
     * The interval is deliberately NOT part of the promise. iOS polls
     * every 15s; the watch polls every 10s and tightens to 2s while a
     * press is waiting on the door, because it can see that and iOS
     * cannot. An implementation that varies its cadence with what the user
     * is doing is honoring this constant, not bending it.
     *
     * Nor is "visible" defined here — it means whatever the smallest unit
     * the host can observe is. On iOS that is the app (`scenePhase`); on
     * the watch it is the one hero screen.
     *
     * iOS ships this today — not because polling is better, but because
     * iOS receives no door pushes at all right now, and a poll needs
     * nothing from Apple, from the server, or from a release cycle. The
     * watch ships it because push was never available to it.
     */
    POLL,

    /**
     * **Promise:** the server pushes, AND the state is refetched once
     * every time the user can see the door again. No interval timer.
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
