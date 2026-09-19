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

package com.chriscartland.garage.presentation

import com.chriscartland.garage.domain.model.DoorPosition

/**
 * What the Status headline says — including the two answers that are not about
 * the door at all.
 *
 * Most of the time the headline names the door, and [DoorHeadline] already
 * decides which of six names that is. The gap this type closes is the state
 * where there is no door to name: a launch with an empty cache, which is every
 * launch on the watch (its local data source is in-memory) and a first launch
 * or cleared cache on the phone.
 *
 * That state used to be worded by each platform independently, and they
 * disagreed. The watch escalated `Connecting…` → `No signal` once the settle
 * window expired; Android and iOS said `Connecting…` **forever** and raised no
 * banner either, because `HomeAlertMapper` has an arm for a stale check-in and
 * an arm for a failed fetch but none for *never heard anything*. So a phone
 * that could not reach the garage sat claiming to be connecting, indefinitely,
 * with nothing else on screen — the settle window's "then show more
 * indicators" half simply did not happen there.
 *
 * Per ADR-035 this type states which of three answers applies and says nothing
 * about the words; each platform words it. The escalation is now decided once,
 * so the three cannot drift again.
 */
sealed interface StatusHeadline {
    /** Name the door. The usual case. */
    data class Door(
        val headline: DoorHeadline,
    ) : StatusHeadline

    /**
     * Nothing heard yet, and the app has only just started looking — the calm
     * presentation. Pairs with `DataFreshness.SETTLING`.
     */
    data object Connecting : StatusHeadline

    /**
     * Nothing heard, and we have been looking long enough to say so. Pairs
     * with `DataFreshness.STALE`.
     *
     * Deliberately reached only when there is NO door event. A watch or phone
     * that HAS a reading keeps naming the door, muted — replacing a known
     * position with "No signal" would throw away the last thing we actually
     * know at the moment it becomes most useful.
     */
    data object NoSignal : StatusHeadline
}

/** The single rule behind [StatusHeadline]. Named object per ADR-009. */
object StatusHeadlineMapper {
    /**
     * @param doorPosition the current reading, or null when the cache is empty.
     * @param freshness gates the escalation: only [DataFreshness.isSpoken]
     *   turns "Connecting…" into "No signal". Before that, `Connecting…` is
     *   still honest — the app really is connecting.
     */
    fun forDoor(
        doorPosition: DoorPosition?,
        freshness: DataFreshness,
    ): StatusHeadline =
        when {
            doorPosition != null -> StatusHeadline.Door(DoorHeadlineMapper.forPosition(doorPosition))
            freshness.isSpoken -> StatusHeadline.NoSignal
            else -> StatusHeadline.Connecting
        }
}
