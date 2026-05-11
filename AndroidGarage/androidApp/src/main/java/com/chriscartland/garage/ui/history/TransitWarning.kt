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

package com.chriscartland.garage.ui.history

/**
 * Typed "transit took longer than expected" tag for [HistoryEntry].
 *
 * Replaces the previous `transitWarning: String?` field. The mapper
 * carries the raw seconds; the Composable formats the duration and
 * assembles the localized "Took X to open/close, longer than expected"
 * string at render time.
 */
sealed interface TransitWarning {
    val transitSeconds: Long

    /** `OPENING_TOO_LONG` carried into a successful `Opened` row. */
    data class ToOpen(
        override val transitSeconds: Long,
    ) : TransitWarning

    /** `CLOSING_TOO_LONG` carried into a successful `Closed` row. */
    data class ToClose(
        override val transitSeconds: Long,
    ) : TransitWarning
}
