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

import com.chriscartland.garage.domain.repository.AppLoggerRepository
import com.chriscartland.garage.domain.repository.DiagnosticsCountersRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Logs an app event by key. Two side effects:
 *
 *  - Append a row to the Room app-event log (capped at 1000 per key —
 *    this is the rolling buffer for CSV export).
 *  - Increment the lifetime counter in the Diagnostics DataStore
 *    (monotonic — the value the user sees on the Diagnostics screen).
 *
 * The two stores are intentionally independent. The Room buffer is
 * trimmed by the per-write cap (and by user "Clear all diagnostics");
 * the lifetime counter only resets when the user explicitly clears.
 *
 * Both writes run inside a [NonCancellable] block so an Activity
 * tear-down (e.g. rotation) between the two suspends cannot leave the
 * Room row committed but the lifetime counter un-incremented.
 * Process-kill drift is still possible (no fix without WAL) but
 * cancel-mid-write drift — the much more common case — is eliminated.
 */
class LogAppEventUseCase(
    private val appLoggerRepository: AppLoggerRepository,
    private val diagnosticsCounters: DiagnosticsCountersRepository,
) {
    suspend operator fun invoke(key: String) {
        withContext(NonCancellable) {
            appLoggerRepository.log(key)
            diagnosticsCounters.increment(key)
        }
    }
}
