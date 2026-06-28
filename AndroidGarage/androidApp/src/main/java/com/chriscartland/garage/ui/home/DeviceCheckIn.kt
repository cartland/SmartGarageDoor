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

package com.chriscartland.garage.ui.home

import com.chriscartland.garage.presentation.CheckInAge
import com.chriscartland.garage.presentation.CheckInStatus
import com.chriscartland.garage.presentation.CheckInStatusMapper

/**
 * Display state for the device check-in indicator. Carries pre-formatted
 * strings so the renderer (currently [com.chriscartland.garage.ui.DeviceCheckInPill])
 * stays stateless and unit tests cover the formatting directly.
 *
 * @param durationLabel e.g. "Just now", "30 sec ago", "1 min 30 sec ago".
 *   Equals [NO_DATA_LABEL] when the heartbeat hasn't been observed.
 * @param isStale true once the heartbeat is older than the staleness
 *   threshold (`CheckInStatusMapper.STALE_THRESHOLD_SECONDS`, 11 min). Drives
 *   the icon flip and color change.
 */
data class DeviceCheckInDisplay(
    val durationLabel: String,
    val isStale: Boolean,
)

/**
 * Android adapter from the shared typed [CheckInStatus] to the rendered
 * [DeviceCheckInDisplay]. The bucketing + staleness *decision* moved to the
 * shared `presentation-model` ([CheckInStatusMapper], ADR-031); this object keeps
 * only the Android-side "… ago" string formatting (the per-UI step iOS mirrors in
 * Swift). Driven by [com.chriscartland.garage.usecase.LiveClock]'s tick via the
 * caller's `nowSeconds` — `MutableStateFlow` equality-dedup makes per-tick calls
 * free for unchanged formatted strings.
 *
 * @param lastCheckInSeconds epoch-seconds of the most recent device heartbeat
 *   (`DoorEvent.lastCheckInTimeSeconds`). Null when no event has been received.
 * @param nowSeconds epoch-seconds of the current wall clock — typically
 *   `LiveClock.nowEpochSeconds.value`.
 * @param staleThresholdSeconds heartbeat age past which the indicator flips to
 *   stale. Defaults to the shared 11-minute threshold.
 */
object DeviceCheckIn {
    fun format(
        lastCheckInSeconds: Long?,
        nowSeconds: Long,
        staleThresholdSeconds: Long = CheckInStatusMapper.STALE_THRESHOLD_SECONDS,
    ): DeviceCheckInDisplay =
        when (
            val status =
                CheckInStatusMapper.forCheckIn(
                    lastCheckInEpochSeconds = lastCheckInSeconds,
                    nowEpochSeconds = nowSeconds,
                    staleThresholdSeconds = staleThresholdSeconds,
                )
        ) {
            CheckInStatus.NoData -> DeviceCheckInDisplay(durationLabel = NO_DATA_LABEL, isStale = false)
            is CheckInStatus.Reported ->
                DeviceCheckInDisplay(
                    durationLabel = label(status.age),
                    isStale = status.isStale,
                )
        }

    private fun label(age: CheckInAge): String =
        when (age) {
            CheckInAge.JustNow -> "Just now"
            is CheckInAge.Seconds -> "${age.seconds} sec ago"
            is CheckInAge.Minutes ->
                if (age.seconds == 0) {
                    "${age.minutes} min ago"
                } else {
                    "${age.minutes} min ${age.seconds} sec ago"
                }
            is CheckInAge.Hours ->
                if (age.minutes == 0) {
                    "${age.hours} hr ago"
                } else {
                    "${age.hours} hr ${age.minutes} min ago"
                }
            is CheckInAge.Days -> if (age.days == 1) "1 day ago" else "${age.days} days ago"
        }

    /** Sentinel duration label for "no heartbeat yet"; the pill hides its text for this value. */
    const val NO_DATA_LABEL = "No data yet"
}
