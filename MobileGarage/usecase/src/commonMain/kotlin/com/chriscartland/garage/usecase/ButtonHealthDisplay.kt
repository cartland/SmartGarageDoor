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

package com.chriscartland.garage.usecase

/**
 * Display state for the remote-button health indicator.
 *
 * Five arms (only `Offline` ever renders the pill), preserving the
 * *why* of "no pill" — useful for debugging and a future UX iteration
 * that wants to distinguish steady-state Online from in-flight Loading
 * from server-confirmed Unknown.
 *
 * Loading and Unknown are different concepts:
 *  - [Loading]: the suspend cold-start fetch is in flight, no result yet.
 *  - [Unknown]: the cold-start fetch returned UNKNOWN (server has no
 *    record yet for the device's buildTimestamp).
 *
 * Both render no pill, but the distinction matters for diagnostic
 * logs and for "stuck Loading > X sec" diagnostics.
 */
sealed interface ButtonHealthDisplay {
    /** User is signed out. */
    data object Unauthorized : ButtonHealthDisplay

    /** Cold-start fetch is in flight or repository is in an error state pending retry. */
    data object Loading : ButtonHealthDisplay

    /** Server returned UNKNOWN (no buttonHealthCurrent doc yet). */
    data object Unknown : ButtonHealthDisplay

    /** Server confirmed ONLINE. */
    data object Online : ButtonHealthDisplay

    /** Server confirmed OFFLINE. The ONLY arm that renders the pill. */
    data class Offline(
        val durationLabel: String,
    ) : ButtonHealthDisplay
}
