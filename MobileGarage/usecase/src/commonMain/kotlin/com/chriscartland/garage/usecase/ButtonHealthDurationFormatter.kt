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
 * Pure formatter for the duration shown alongside "Remote offline".
 *
 * Mirrors the buckets of [DeviceCheckIn.format] (the existing pill)
 * so both indicators speak the same grammar.
 */
object ButtonHealthDurationFormatter {
    /**
     * Format the duration since [stateChangedAtSeconds] as a human
     * label like `"5 min ago"`. Null input → `"unknown"` (defensive;
     * production callers should not pass null).
     */
    fun formatAgo(
        stateChangedAtSeconds: Long?,
        nowSeconds: Long,
    ): String {
        if (stateChangedAtSeconds == null) return "unknown"
        val deltaSec = nowSeconds - stateChangedAtSeconds
        // Clock-skew clamp: future timestamps reported as just-now.
        if (deltaSec <= 0) return "just now"
        if (deltaSec < 60) return "$deltaSec sec ago"
        val minutes = deltaSec / 60
        if (minutes < 60) return "$minutes min ago"
        val hours = minutes / 60
        if (hours < 24) return "$hours hr ago"
        val days = hours / 24
        return "$days day${if (days == 1L) "" else "s"} ago"
    }
}
