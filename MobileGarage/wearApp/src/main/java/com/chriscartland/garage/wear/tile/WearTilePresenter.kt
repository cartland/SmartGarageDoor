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

package com.chriscartland.garage.wear.tile

import com.chriscartland.garage.domain.coroutines.AppClock
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.presentation.GlanceStatus
import com.chriscartland.garage.presentation.GlanceStatusMapper
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import com.chriscartland.garage.usecase.ObserveDoorEventsUseCase
import com.chriscartland.garage.wear.data.DoorSnapshotHydration

/**
 * Everything the garage tile decides, with no ProtoLayout in sight.
 *
 * **Why a presenter and not a ViewModel.** Every other surface on the watch
 * gets a `ViewModel` (ADR-026), but a `TileService` has no `ViewModelStore`
 * and no lifecycle to scope one to — it is constructed to answer one question
 * and torn down. This is the same separation for the same reason: the tile's
 * DECISIONS live somewhere a JVM test can reach them, and the service is left
 * holding only the parts that genuinely need a platform (building a layout,
 * and asking the system to re-render).
 *
 * It is a `@WearSingleton` because it remembers two things across requests
 * ([lastRefreshFailed] and [lastRenderedEvent]), and the system creates a
 * fresh service instance per request — fields on the service would read their
 * initial values forever, so the tile could never report a failed refresh nor
 * notice that the door had moved.
 */
class WearTilePresenter(
    private val observeDoorEvents: ObserveDoorEventsUseCase,
    private val fetchCurrentDoorEvent: FetchCurrentDoorEventUseCase,
    private val hydration: DoorSnapshotHydration,
    private val clock: AppClock,
) {
    /**
     * Whether the last refresh this process attempted failed. Seeded false:
     * a tile that has not asked anything yet is un-asked, not failed, and the
     * age line is what tells the reader how much the value is worth.
     */
    private var lastRefreshFailed: Boolean = false

    /** The door event most recently handed to a render. */
    private var lastRenderedEvent: DoorEvent? = null

    private var hasRendered: Boolean = false

    /**
     * What to draw right now, from what is already known.
     *
     * Waits for the disk read and nothing else. Network-free on purpose:
     * `onTileRequest` must answer promptly and the watch's path is the
     * slowest in the system, so the tile renders the cache and lets
     * [refreshAndReportChange] catch up afterwards.
     *
     * The one thing it DOES wait for is hydration, because the alternative is
     * the failure this whole surface exists to avoid. The system may start
     * the process purely to answer this request, so without the wait the tile
     * can answer "No signal" about a door it has on disk, purely because a
     * file read had not landed yet — a wrong reading, shown once, on a
     * surface that gets one chance to be right. See
     * [DoorSnapshotHydration.awaitHydration] for why the cost is tolerable.
     */
    suspend fun status(): GlanceStatus {
        hydration.awaitHydration()
        val event = observeDoorEvents.current().value
        lastRenderedEvent = event
        hasRendered = true
        return GlanceStatusMapper.forGlance(
            doorPosition = event?.doorPosition,
            lastCheckInEpochSeconds = event?.lastCheckInTimeSeconds,
            nowEpochSeconds = clock.nowEpochSeconds(),
            isFetchError = lastRefreshFailed,
        )
    }

    /**
     * Refresh, and report whether the answer already on screen is now wrong.
     *
     * Returns true only when the door actually turned out to be somewhere
     * else than what was last rendered — which is what the caller uses to
     * decide whether to spend a re-render. **Gating on a real change is what
     * stops the tile looping**: a re-render calls [status] and then this
     * again, so "always true" would be an endless cycle of renders and
     * fetches. Returning false when nothing moved terminates it after one
     * extra pass.
     *
     * A failed fetch is recorded and reported as no-change. The failure still
     * reaches the user, on the next render, as the muted treatment — spending
     * a re-render on it immediately would mean a tile that redraws itself
     * every time the watch is out of range.
     */
    suspend fun refreshAndReportChange(): Boolean {
        lastRefreshFailed = fetchCurrentDoorEvent() is AppResult.Error
        if (lastRefreshFailed) return false
        val latest = observeDoorEvents.current().value
        // Nothing has been rendered yet (a refresh raced ahead of the first
        // request): there is no stale answer on screen to correct.
        if (!hasRendered) return false
        return latest != lastRenderedEvent
    }
}
