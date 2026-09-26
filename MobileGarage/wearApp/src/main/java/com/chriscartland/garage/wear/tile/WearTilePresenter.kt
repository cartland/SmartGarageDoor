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

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.presentation.GlanceStatus
import com.chriscartland.garage.wear.glance.WearGlanceStatus

/**
 * The tile's decisions, which is all of them that are not drawing.
 *
 * **Why a presenter and not a ViewModel.** Every other surface on the watch
 * gets a `ViewModel` (ADR-026), but a `TileService` has no `ViewModelStore`
 * and no lifecycle to scope one to — it is constructed to answer one question
 * and torn down. This is the same separation for the same reason: the tile's
 * DECISIONS live somewhere a JVM test can reach them, and the service is left
 * holding only the parts that genuinely need a platform (building a layout,
 * and asking the system to re-render).
 *
 * What it owns beyond [WearGlanceStatus] is exactly one thing: **the event it
 * last drew**, so it can tell whether spending a re-render is worthwhile.
 * That memory is deliberately NOT on the shared object — the complication
 * reads the same door, and if the two shared one idea of "what is on screen"
 * whichever refreshed second would conclude nothing had changed.
 *
 * It is a `@WearSingleton` because that memory has to survive across
 * requests, and the system creates a fresh `TileService` for every one.
 */
class WearTilePresenter(
    private val glance: WearGlanceStatus,
) {
    /** The door event most recently handed to a render. */
    private var lastRenderedEvent: DoorEvent? = null

    private var hasRendered: Boolean = false

    /**
     * What to draw right now.
     *
     * Network-free on purpose: `onTileRequest` must answer promptly and the
     * watch's path is the slowest in the system, so the tile renders what is
     * known and lets [refreshAndReportChange] catch up afterwards. The one
     * thing it waits for is the disk read — see [WearGlanceStatus.current].
     */
    suspend fun status(): GlanceStatus {
        val reading = glance.current()
        lastRenderedEvent = reading.event
        hasRendered = true
        return reading.status
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
        val refresh = glance.refresh()
        if (!refresh.succeeded) return false
        // Nothing has been rendered yet (a refresh raced ahead of the first
        // request): there is no stale answer on screen to correct.
        if (!hasRendered) return false
        return refresh.event != lastRenderedEvent
    }
}
