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
 */

package com.chriscartland.garage.viewmodel

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.presentation.DoorWarning
import com.chriscartland.garage.presentation.DoorWarningMapper
import com.chriscartland.garage.presentation.SinceStatus
import com.chriscartland.garage.presentation.SinceStatusMapper
import com.chriscartland.garage.usecase.VoiceDoorState
import com.chriscartland.garage.usecase.VoiceDoorStateMapper

/**
 * Everything the Home door-status surface derives from the current door
 * event, computed as ONE node (docs/DATA_GRAPH_PLAN.md, rule G7).
 *
 * Until 2.23.x these were three independent `stateIn`s over the same
 * root (`warning`, `sinceStatus`, and the voice gate's door
 * projection), so the status card and the voice gate could disagree
 * for a frame, and their logical agreement was promised only by a
 * comment. One `combine` + one pure transform makes the promise
 * structural: the warning chip, the "Since …" line, the stale pill,
 * and the voice gate's view of the door are fields of the same value,
 * produced by the same function from the same snapshot.
 *
 * Lives in `:viewmodel` (not `presentation-model`) because it composes
 * types from two modules that don't see each other: [DoorWarning] /
 * [SinceStatus] from `presentation-model` and [VoiceDoorState] from
 * `:usecase`. The per-field mapping stays in the modules that own it;
 * this is composition, not new logic.
 */
data class HomeDoorState(
    /** Typed warning for stuck / anomalous door states; null = no warning (ADR-031). */
    val warning: DoorWarning?,
    /** Typed data for the "Since … · duration" line; null when the change time is unknown. */
    val sinceStatus: SinceStatus?,
    /** True when the device's last check-in is older than the staleness threshold. */
    val isCheckInStale: Boolean,
    /**
     * The door as the voice gate must see it: anomalies and stale
     * check-ins project to states that refuse a spoken command.
     */
    val voice: VoiceDoorState,
)

/**
 * The single pure transform behind [HomeDoorState]. Named object per
 * ADR-009. Delegates each field to the mapper that owns it — the value
 * of this object is that all four fields are computed from the SAME
 * `(event, isCheckInStale, now)` snapshot, never from independently
 * collected copies.
 */
object HomeDoorStateMapper {
    fun compute(
        event: DoorEvent?,
        isCheckInStale: Boolean,
        nowEpochSeconds: Long,
    ): HomeDoorState =
        HomeDoorState(
            warning = DoorWarningMapper.forEvent(event),
            sinceStatus = SinceStatusMapper.forEvent(event?.lastChangeTimeSeconds, nowEpochSeconds),
            isCheckInStale = isCheckInStale,
            voice = VoiceDoorStateMapper.project(event?.doorPosition, isCheckInStale),
        )
}
