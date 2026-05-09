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

import com.chriscartland.garage.domain.model.AppLoggerLimits

/**
 * Runs the per-launch Diagnostics maintenance pass: first seed the
 * lifetime counters from Room (one-shot recovery for pre-2.11.0 installs),
 * then prune Room rows past [perKeyLimit].
 *
 * **Sequential ordering is load-bearing.** The seed must read un-pruned
 * Room counts; if seed and prune ran concurrently, prune could delete
 * rows the seed wanted to count, permanently locking in a lower lifetime
 * counter for users upgrading from a pre-cap (pre-2.10.4) version.
 * Calling code MUST suspend through both — never `launch { seed() }` and
 * `launch { prune() }` on a shared dispatcher; that's the bug this UseCase
 * exists to prevent.
 */
class RunStartupDiagnosticsMaintenanceUseCase(
    private val seed: SeedDiagnosticsCountersFromRoomUseCase,
    private val prune: PruneDiagnosticsLogUseCase,
) {
    suspend operator fun invoke(perKeyLimit: Int = AppLoggerLimits.DEFAULT_PER_KEY_LIMIT) {
        seed()
        prune(perKeyLimit)
    }
}
