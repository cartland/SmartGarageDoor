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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.WatchAppStatus
import com.chriscartland.garage.domain.repository.WearCompanionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Observes whether the paired watch has the Wear OS app installed.
 *
 * Returns [StateFlow] so a freshly-constructed screen ViewModel can read
 * `.value` synchronously on first composition (ADR-022 pass-through).
 *
 * **Why the cache exists.** The repository flow is cold — it polls Play
 * Services only while collected. Without this `stateIn`, every fresh
 * `NavBackStackEntry` built a new `DefaultProfileViewModel` that started
 * at [WatchAppStatus.Unknown] and received the first poll result one
 * frame later. That flipped the Settings "Watch" section's
 * `AppAnimatedVisibility` from hidden to visible, replaying the
 * expand-in animation on EVERY navigation to the tab. Caching the last
 * known status process-wide means the section is already visible on
 * entry, so it animates only on a genuine status change (a watch
 * connecting, or the install completing).
 *
 * **Why `WhileSubscribed`, not the `Eagerly` house default** used by
 * [ComputeButtonHealthDisplayUseCase]: that UseCase combines cheap
 * in-memory `StateFlow`s, so running it for the whole process costs
 * nothing. This upstream is a 15s Play Services polling loop, and
 * `Eagerly` would keep it running even while the user never opens
 * Settings. `WhileSubscribed` stops the poll shortly after the last
 * collector leaves while still RETAINING the last value:
 * `replayExpirationMillis` defaults to `Long.MAX_VALUE`, so the cache is
 * never reset back to [initialValue]. That retention is the entire
 * point of this class and is pinned by
 * `ObserveWatchAppStatusUseCaseTest.retainsLastStatusForALaterCollector`
 * — if a future edit passes a finite `replayExpiration`, the flicker
 * returns and that test fails.
 *
 * The provider must be singleton-scoped in **both** DI components, or
 * each screen entry would build its own `stateIn` cache and the flicker
 * would come straight back. Identity is pinned by `ComponentGraphTest`
 * (Android) and `NativeComponentTest` (iOS).
 */
class ObserveWatchAppStatusUseCase(
    wearCompanionRepository: WearCompanionRepository,
    applicationScope: CoroutineScope,
) {
    private val state: StateFlow<WatchAppStatus> =
        wearCompanionRepository
            .observeWatchAppStatus()
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = WatchAppStatus.Unknown,
            )

    operator fun invoke(): StateFlow<WatchAppStatus> = state

    private companion object {
        // Grace period so a configuration change or a quick tab
        // round-trip does not tear down and restart the Play Services
        // poll. Only the poll pauses — the cached value is retained
        // indefinitely regardless of this timeout.
        const val STOP_TIMEOUT_MILLIS: Long = 5_000L
    }
}
