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

package com.chriscartland.garage.usecase

import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.model.AppLoggerKeys
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * App-scoped one-shot fetch of door state at process start (ADR-015).
 *
 * Why this exists: `DefaultHomeViewModel` and `DefaultDoorHistoryViewModel`
 * used to call `fetchCurrentDoorEvent()` / `fetchRecentDoorEvents()` from
 * their `init {}` blocks, which fired on every fresh `NavBackStackEntry` —
 * so every History tab tap triggered a redundant network round-trip even
 * though FCM had already pushed any new event into Room. While the app is
 * open and FCM is connected, the Room flow + FCM-fed insert path is the
 * source of truth for door state — manual fetches should be (a) cold-start,
 * (b) user-initiated (pull-to-refresh, alert action). [start] covers (a).
 *
 * [start] is idempotent — calling twice is a no-op. Combined with
 * `@Singleton` provisioning, this guarantees the cold-start fetch fires
 * exactly once per process even when `MainActivity.onCreate` fires
 * multiple times (rotation, app resume after Activity destroy).
 *
 * Errors are intentionally swallowed (logged via the UseCases' own
 * logging). Cold-start failure is recoverable: pull-to-refresh, FCM, or
 * the app's stale-data alert path will all surface the issue and let the
 * user retry.
 */
class InitialDoorFetchManager(
    private val fetchCurrentDoorEvent: FetchCurrentDoorEventUseCase,
    private val fetchRecentDoorEvents: FetchRecentDoorEventsUseCase,
    private val logAppEvent: LogAppEventUseCase,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    private var job: Job? = null

    /**
     * Fetch current + recent door events once. Idempotent — subsequent
     * calls are no-ops while the first attempt is in flight or after it
     * has completed.
     */
    fun start() {
        if (job != null) {
            Logger.d { "InitialDoorFetchManager: already started" }
            return
        }
        job = scope.launch(dispatcher) {
            Logger.d { "InitialDoorFetchManager: fetching current + recent door events" }
            logAppEvent(AppLoggerKeys.INIT_CURRENT_DOOR)
            fetchCurrentDoorEvent()
            logAppEvent(AppLoggerKeys.INIT_RECENT_DOOR)
            fetchRecentDoorEvents()
        }
    }
}
