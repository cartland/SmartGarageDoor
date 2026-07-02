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

package com.chriscartland.garage.usecase

import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.repository.AppLoggerRepository
import com.chriscartland.garage.domain.repository.DiagnosticsCountersRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * One-shot recovery for users upgrading from a version where the
 * Diagnostics screen counts came from a Room aggregate query (anything
 * before 2.11.0). Without this seed, the lifetime DataStore counters —
 * a brand-new file in 2.11.0 — initialize to 0 on first launch after
 * upgrade, and the user sees their accumulated counts vanish.
 *
 * Reads existing Room rows and uses `max(counter, room_row_count)` as
 * the recovered floor for each key. Bounded above by the per-key cap
 * shipped in 2.10.4 (1000 rows max), so this restores at most 1000
 * per key — better than 0, not perfect, all that's recoverable from
 * disk after the row cap took effect.
 *
 * Idempotent at the repository layer (gated by an internal "already
 * seeded" Boolean flag in the same DataStore), so safe to fire on
 * every app start. After a user-initiated Clear, the flag is wiped
 * along with the counters and the next seed pass runs again — but
 * Room is also empty by then, so it's a no-op.
 */
class SeedDiagnosticsCountersFromRoomUseCase(
    private val appLoggerRepository: AppLoggerRepository,
    private val diagnosticsCounters: DiagnosticsCountersRepository,
) {
    suspend operator fun invoke(): Boolean =
        try {
            val countsByKey: Map<String, Long> = appLoggerRepository
                .getAll()
                .first()
                .groupingBy { it.eventKey }
                .eachCount()
                .mapValues { it.value.toLong() }
            diagnosticsCounters.seedFromCountsOnce(countsByKey)
        } catch (cancel: CancellationException) {
            // Cooperative cancellation — propagate so the launching scope
            // tears down cleanly. Not an error worth logging.
            throw cancel
        } catch (t: Throwable) {
            // Storage corruption / IO failure on either Room or DataStore.
            // Without this catch, viewModelScope's default handler would
            // silently swallow the throw — counters stay at 0, the user
            // sees nothing, and the seed flag stays unset so this fires
            // again every launch, throwing each time. Logging surfaces the
            // failure to anyone reading device logs while preserving the
            // automatic-retry behavior (next launch will try again).
            Logger.e(t) { "Diagnostics counters seed-from-Room failed; will retry on next launch" }
            false
        }
}
