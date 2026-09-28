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
import com.chriscartland.garage.presentation.GlanceStatus
import com.chriscartland.garage.presentation.GlanceStatusMapper
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import kotlinx.coroutines.flow.first

/**
 * What the home-screen widget needs: the current verdict, and a way to ask the
 * server for a newer one.
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
 * Collecting Room's own flow with [first] has no such ambiguity — Room emits
 * the query result when collection begins, so the first value IS the stored
 * one. This is the same hazard Wear solved with an explicit hydration latch;
 * the phone needs no latch because Room's flow is already the answer. Do not
 * "simplify" this to `observeDoorEvents.current().value`.
 */
class WidgetGlanceStatus(
    private val localDoorDataSource: LocalDoorDataSource,
    private val fetchCurrentDoorEvent: FetchCurrentDoorEventUseCase,
    private val clock: AppClock,
) {
    /**
     * Whether the refresh this render attempted failed. Seeded false: a widget
     * that has not asked anything yet is un-asked, not failed.
     */
    private var lastRefreshFailed: Boolean = false

    /** The verdict to show right now, from what is already on disk. */
    suspend fun current(): GlanceStatus {
        val event = localDoorDataSource.currentDoorEvent.first()
        return GlanceStatusMapper.forGlance(
            doorPosition = event?.doorPosition,
            // Decides only whether the reading may be presented as current. It
            // is never shown: see GlanceStatus on why an age is the wrong
            // number for a surface the system redraws on its own schedule.
            lastCheckInEpochSeconds = event?.lastCheckInTimeSeconds,
            // When the DOOR changed. This is the one the widget renders, as an
            // absolute instant rather than a duration.
            lastChangeEpochSeconds = event?.lastChangeTimeSeconds,
            nowEpochSeconds = clock.nowEpochSeconds(),
            isFetchError = lastRefreshFailed,
        )
    }

    /**
     * Ask the server for a newer reading.
     *
     * Records the outcome so the next [current] presents a value we could not
     * confirm as remembered rather than current. Never throws: a widget that
     * cannot reach the server still has something true to show.
     */
    suspend fun refresh() {
        lastRefreshFailed = fetchCurrentDoorEvent() is AppResult.Error
    }
}
