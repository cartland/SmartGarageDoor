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

package com.chriscartland.garage.wear.glance

import com.chriscartland.garage.domain.coroutines.AppClock
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.presentation.GlanceStatus
import com.chriscartland.garage.presentation.GlanceStatusMapper
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import com.chriscartland.garage.usecase.ObserveDoorEventsUseCase
import com.chriscartland.garage.wear.data.DoorSnapshotHydration

/**
 * One reading of the door, and the event it was derived from.
 *
 * The two travel together so a caller that needs both cannot take them a
 * moment apart and end up tracking an event that is not the one it rendered.
 */
data class GlanceReading(
    val status: GlanceStatus,
    val event: DoorEvent?,
)

/** The outcome of a refresh, with whatever is newest afterwards. */
data class GlanceRefresh(
    val succeeded: Boolean,
    val event: DoorEvent?,
)

/**
 * What every glance surface on the watch needs: the current verdict, and a
 * way to go and ask the server for a newer one.
 *
 * Shared by the tile and the watch-face complication, because they answer the
 * same question and must never answer it differently. **What is deliberately
 * NOT here is per-surface memory.** The tile tracks the event it last drew so
 * it can decide whether spending a re-render is worthwhile; if that lived on
 * this shared object the two surfaces would clobber each other's idea of what
 * was on screen, and whichever refreshed second would conclude nothing had
 * changed. Surface-specific state belongs to the surface.
 *
 * [lastRefreshFailed] IS shared, and correctly so: "we could not reach the
 * server" is a fact about the process, not about one surface, and both should
 * present a remembered value the same way.
 */
class WearGlanceStatus(
    private val observeDoorEvents: ObserveDoorEventsUseCase,
    private val fetchCurrentDoorEvent: FetchCurrentDoorEventUseCase,
    private val hydration: DoorSnapshotHydration,
    private val clock: AppClock,
) {
    /**
     * Whether the last refresh this process attempted failed. Seeded false: a
     * surface that has not asked anything yet is un-asked, not failed, and
     * the age is what tells the reader how much the value is worth.
     */
    private var lastRefreshFailed: Boolean = false

    /**
     * The verdict to show right now, from what is already known.
     *
     * Waits for the disk read and nothing else. The system may start this
     * process purely to answer one request, so without that wait a surface
     * can say "No signal" about a door it has on disk — a wrong reading,
     * shown once, on a surface that gets one chance to be right. See
     * [DoorSnapshotHydration.awaitHydration].
     */
    suspend fun current(): GlanceReading {
        hydration.awaitHydration()
        val event = observeDoorEvents.current().value
        return GlanceReading(
            status = GlanceStatusMapper.forGlance(
                doorPosition = event?.doorPosition,
                lastCheckInEpochSeconds = event?.lastCheckInTimeSeconds,
                // When the DOOR changed, which is the number a glance shows.
                // Distinct from the check-in above, which only decides whether
                // we may present it as current.
                lastChangeEpochSeconds = event?.lastChangeTimeSeconds,
                nowEpochSeconds = clock.nowEpochSeconds(),
                isFetchError = lastRefreshFailed,
            ),
            event = event,
        )
    }

    /**
     * Ask the server for a newer reading.
     *
     * Records the outcome so the next [current] presents a value we could not
     * confirm as remembered rather than current. Never throws for a failed
     * fetch — a surface that cannot reach the server still has something true
     * to show, and saying how old it is remains the honest answer.
     */
    suspend fun refresh(): GlanceRefresh {
        val failed = fetchCurrentDoorEvent() is AppResult.Error
        lastRefreshFailed = failed
        return GlanceRefresh(
            succeeded = !failed,
            event = observeDoorEvents.current().value,
        )
    }
}
