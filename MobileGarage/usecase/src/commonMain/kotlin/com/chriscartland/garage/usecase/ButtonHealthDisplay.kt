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
 * [Hidden] is the no-verdict arm and MUST render nothing on both
 * platforms (no text, no icon): "Checking…" was a prominent UI element
 * for a state the user can't act on. With the persisted status cache
 * (STATUS_CACHE_PLAN.md D2) a hydrated last-known verdict shows
 * instantly, so [Hidden] only appears on true first-run, after a
 * Forbidden cache-clear, or past the display-TTL.
 *
 * Hidden and Unknown are different concepts:
 *  - [Hidden]: no verdict to show — fetch in flight with nothing
 *    cached, or repository in an error state pending retry.
 *  - [Unknown]: a server VERDICT — the fetch returned UNKNOWN (no
 *    record yet for the device's buildTimestamp).
 */
sealed interface ButtonHealthDisplay {
    /** User is signed out. */
    data object Unauthorized : ButtonHealthDisplay

    /** No verdict to show. Renders NOTHING on both platforms. */
    data object Hidden : ButtonHealthDisplay

    /** Server returned UNKNOWN (no buttonHealthCurrent doc yet). */
    data object Unknown : ButtonHealthDisplay

    /** Server confirmed ONLINE. */
    data object Online : ButtonHealthDisplay

    /** Server confirmed OFFLINE. The ONLY arm that renders the pill. */
    data class Offline(
        val durationLabel: String,
    ) : ButtonHealthDisplay
}
