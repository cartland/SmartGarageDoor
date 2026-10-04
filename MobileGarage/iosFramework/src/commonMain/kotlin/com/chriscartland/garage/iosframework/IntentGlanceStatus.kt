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

package com.chriscartland.garage.iosframework

import com.chriscartland.garage.data.LocalDoorDataSource
import com.chriscartland.garage.domain.coroutines.AppClock
import com.chriscartland.garage.presentation.GlanceStatus
import com.chriscartland.garage.presentation.GlanceStatusMapper
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * The door for a caller with no screen: an App Intent answering Siri or the
 * Shortcuts app, in a process the system may have launched a moment ago
 * purely to answer.
 *
 * The iOS twin of Android's `WidgetGlanceStatus`, and deliberately the same
 * shape: the shared verdict (`GlanceStatusMapper.forGlance`), a read straight
 * from the local store so a cold process never says "No signal" over a
 * populated cache (`DoorRepository.currentDoorEvent` is seeded null and filled
 * by a collector, which has not run yet when the process is new), and a
 * refresh that never throws — its failure is carried into the verdict as a
 * reading we could not confirm, which is something true to say, where an
 * exception would leave Siri with nothing.
 *
 * READ-ONLY. It holds nothing that can press, and the intent that consumes
 * it is pinned to that by `DoorStatusIntentTests` on the Swift side.
 *
 * Holds no memory of its own. Whether the last fetch failed is the
 * repository's `currentDoorFetchFailed` — the process's one copy, which the
 * app's own poll writes too — so Siri describes the door exactly as the
 * screen would. The intent refreshes before it answers, so what it reads is
 * normally its own attempt.
 */
class IntentGlanceStatus(
    private val localDoorDataSource: LocalDoorDataSource,
    private val fetchCurrentDoorEvent: FetchCurrentDoorEventUseCase,
    private val clock: AppClock,
    private val fetchFailed: StateFlow<Boolean>,
) {
    /** The verdict to say right now, from what is already on disk. */
    suspend fun current(): GlanceStatus {
        val event = localDoorDataSource.currentDoorEvent.first()
        return GlanceStatusMapper.forGlance(
            doorPosition = event?.doorPosition,
            lastCheckInEpochSeconds = event?.lastCheckInTimeSeconds,
            lastChangeEpochSeconds = event?.lastChangeTimeSeconds,
            nowEpochSeconds = clock.nowEpochSeconds(),
            isFetchError = fetchFailed.value,
        )
    }

    /** Ask the server for a newer reading; the repository records the outcome that shapes the next [current]. */
    suspend fun refresh() {
        fetchCurrentDoorEvent()
    }
}
