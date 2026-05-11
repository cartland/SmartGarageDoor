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

import java.time.LocalDate

/**
 * Typed day label for [HistoryDay]. Replaces the previous `label: String`
 * field.
 *
 * Phase 2E of the string-resource migration plan — the mapper emits a
 * typed value, the Composable resolves to a localized string at render
 * time. [Today] / [Yesterday] map to `R.string.history_day_*` resources;
 * [Date] carries the [LocalDate] and the Composable formats it via the
 * locale-aware `DateTimeFormatter`.
 */
sealed interface DayLabel {
    /** Today, in the user's local time zone. */
    data object Today : DayLabel

    /** One day before [Today]. */
    data object Yesterday : DayLabel

    /** Two or more days ago — render the full date (e.g. "Mon, Apr 27"). */
    data class Date(
        val date: LocalDate,
    ) : DayLabel
}
