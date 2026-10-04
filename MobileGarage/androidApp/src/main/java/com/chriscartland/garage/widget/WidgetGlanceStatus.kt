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

package com.chriscartland.garage.widget

import com.chriscartland.garage.data.LocalDoorDataSource
import com.chriscartland.garage.domain.coroutines.AppClock
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.presentation.GlanceStatus
import com.chriscartland.garage.presentation.GlanceStatusMapper
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/**
 * What the home-screen widget needs: the current verdict, every verdict after
 * it, and a way to ask the server for a newer one.
 *
 * The phone's counterpart to `WearGlanceStatus`, reaching the same
 * [GlanceStatusMapper] so the widget and the app cannot describe one door two
 * ways. Named for the surface because the phone has exactly ONE glance
 * surface; on the watch the same reader serves a tile and a complication, which
 * is why its name is the platform rather than the surface.
 *
 * ## Why this reads the data source and not the repository
 *
 * `DoorRepository.currentDoorEvent` is a `MutableStateFlow` **seeded null** and
 * filled by a collector the repository starts in its `init` — so on a cold
 * process its `.value` is `null` for a moment whether or not the database holds
 * a door. A widget is rendered by the system while the app is not running,
 * which is exactly that moment, so reading `.value` would show "No signal" over
 * a populated database: a wrong reading, on a surface the user did not ask to
 * refresh and may not look at again for an hour.
 *
 * Collecting Room's own flow has no such ambiguity — Room emits the query
 * result when collection begins, so the first value IS the stored one. This is
 * the same hazard Wear solved with an explicit hydration latch; the phone needs
 * no latch because Room's flow is already the answer. Do not "simplify" this to
 * `observeDoorEvents.current().value`.
 *
 * ## Why there is [observe] as well as [current]
 *
 * A widget session outlives its first frame by about 45 seconds, and asking
 * Glance to update does not restart a session that is still alive (see
 * [WidgetRepaintRequests]). A reader that only answered once therefore froze
 * the widget on the first thing it saw: a door that went Opening and then Open
 * a few seconds later — the ordinary case, two pushes inside one session —
 * stayed "Opening" on the home screen until the launcher's next half-hourly
 * update. [observe] is the same verdict as [current], kept current: it follows
 * the same row on disk that the app, a push and every fetch write to, so
 * whatever any of them learns is on the widget without anyone repainting it.
 */
class WidgetGlanceStatus(
    private val localDoorDataSource: LocalDoorDataSource,
    private val fetchCurrentDoorEvent: FetchCurrentDoorEventUseCase,
    private val clock: AppClock,
    private val repaintRequests: StateFlow<Long>,
) {
    /**
     * Whether the refresh this render attempted failed. Seeded false: a widget
     * that has not asked anything yet is un-asked, not failed. A flow so that
     * [observe] hears the outcome land.
     */
    private val lastRefreshFailed = MutableStateFlow(false)

    /**
     * The verdict to show right now, from what is already on disk. Read before
     * composing, so the first frame is the real door rather than a placeholder.
     */
    suspend fun current(): GlanceStatus =
        verdict(
            event = localDoorDataSource.currentDoorEvent.first(),
            refreshFailed = lastRefreshFailed.value,
        )

    /**
     * Every verdict from here on, for as long as it is collected.
     *
     * Judged again whenever any of the three things it depends on moves: the
     * door on disk, the outcome of [refresh], or a repaint request — the last
     * being how a change produced by the CLOCK gets in, since `now` is read
     * when the verdict is computed and nothing else about a garage going quiet
     * changes any stored value.
     *
     * Distinct, because Glance re-sends the whole widget for every value it is
     * handed, and most repaint requests leave the verdict exactly as it was.
     */
    fun observe(): Flow<GlanceStatus> =
        combine(
            localDoorDataSource.currentDoorEvent,
            lastRefreshFailed,
            repaintRequests,
        ) { event, refreshFailed, _ ->
            verdict(event = event, refreshFailed = refreshFailed)
        }.distinctUntilChanged()

    /**
     * Ask the server for a newer reading.
     *
     * Records the outcome so the verdict presents a value we could not confirm
     * as remembered rather than current. Never throws: a widget that cannot
     * reach the server still has something true to show.
     */
    suspend fun refresh() {
        lastRefreshFailed.value = fetchCurrentDoorEvent() is AppResult.Error
    }

    /** The one place the pieces become a verdict, so [current] and [observe] cannot disagree. */
    private fun verdict(
        event: DoorEvent?,
        refreshFailed: Boolean,
    ): GlanceStatus =
        GlanceStatusMapper.forGlance(
            doorPosition = event?.doorPosition,
            // Decides only whether the reading may be presented as current. It
            // is never shown: see GlanceStatus on why an age is the wrong
            // number for a surface the system redraws on its own schedule.
            lastCheckInEpochSeconds = event?.lastCheckInTimeSeconds,
            // When the DOOR changed. This is the one the widget renders, as an
            // absolute instant rather than a duration.
            lastChangeEpochSeconds = event?.lastChangeTimeSeconds,
            nowEpochSeconds = clock.nowEpochSeconds(),
            isFetchError = refreshFailed,
        )
}
