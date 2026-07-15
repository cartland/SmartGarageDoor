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

package com.chriscartland.garage.data.statuscache

import kotlinx.serialization.Serializable

/**
 * Persisted shape of the snooze status (STATUS_CACHE_PLAN.md D3).
 *
 * Deliberately the raw server [endTimeSeconds], NEVER a serialized
 * `SnoozeState`: hydration recomputes the state against the current
 * clock (`snoozeStateFromEndTime`), so a snooze that expired while the
 * app was dead hydrates as NotSnoozing instead of a stale
 * "Snoozing until [past]". `endTimeSeconds <= 0` = no active snooze —
 * persisted anyway so a cold start renders NotSnoozing instantly and
 * the fetch-TTL can skip the redundant init fetch.
 */
@Serializable
data class SnoozeSnapshotDto(
    val endTimeSeconds: Long,
)

/** Cache identity + versioning for the snooze snapshot. */
object SnoozeSnapshot {
    val KEY = StatusCacheKey("snooze")

    /** Bump to deliberately invalidate all persisted snooze entries. */
    const val SCHEMA_VERSION: Int = 1
}
