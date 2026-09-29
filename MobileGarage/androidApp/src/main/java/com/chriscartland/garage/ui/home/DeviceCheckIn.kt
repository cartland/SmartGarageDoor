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

package com.chriscartland.garage.ui.home

import com.chriscartland.garage.presentation.CheckInStatus
import com.chriscartland.garage.presentation.CheckInStatusMapper
import com.chriscartland.garage.presentation.DataFreshness

/**
 * What the device check-in pill shows: the shared, typed verdict about the
 * garage's last heartbeat, plus whether the pill may ALARM about it.
 *
 * @property status the shared [CheckInStatus] — `NoData` before the first
 *   heartbeat (the pill hides its text), `Reported(age, isStale)` after. The
 *   words for [CheckInStatus.Reported.age] are [DeviceCheckInWords]', resolved
 *   from string resources in the Composable; nothing here is a sentence.
 * @property isStale whether the pill shows its alarm (red, `SensorsOff`).
 *   This is the SPOKEN verdict — the raw `status.isStale` gated by the settle
 *   window (`DataFreshness.isSpoken`) — so a warm start on an aged heartbeat
 *   keeps its words and holds its alarm for five seconds (CLAUDE.md § "The
 *   settle window").
 *
 * Until strategy 4.6 this carried a `durationLabel: String` and a
 * `NO_DATA_LABEL` sentinel the pill compared against to decide whether to
 * show text — a Kotlin literal the Compose literal lint could not see, and
 * a comparison on words. The typed status is the decision; the words are
 * the platform's.
 */
data class DeviceCheckInDisplay(
    val status: CheckInStatus,
    val isStale: Boolean,
)

object DeviceCheckIn {
    fun format(
        lastCheckInSeconds: Long?,
        nowSeconds: Long,
        // Defaults to STALE — "the settle window has already passed" — so
        // every existing caller keeps the behaviour it had before the gate
        // existed. The default errs toward SHOWING the alarm, never toward
        // suppressing it, which is the safe direction for a default to err in
        // when the thing being gated is a warning.
        freshness: DataFreshness = DataFreshness.STALE,
        staleThresholdSeconds: Long = CheckInStatusMapper.STALE_THRESHOLD_SECONDS,
    ): DeviceCheckInDisplay {
        val status =
            CheckInStatusMapper.forCheckIn(
                lastCheckInEpochSeconds = lastCheckInSeconds,
                nowEpochSeconds = nowSeconds,
                staleThresholdSeconds = staleThresholdSeconds,
            )
        val isStale =
            when (status) {
                CheckInStatus.NoData -> false
                is CheckInStatus.Reported -> status.isStale && freshness.isSpoken
            }
        return DeviceCheckInDisplay(status = status, isStale = isStale)
    }
}
