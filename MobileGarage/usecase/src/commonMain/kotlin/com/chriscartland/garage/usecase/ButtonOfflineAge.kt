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
 * How long the remote button has been silent, as a typed bucket.
 *
 * This replaces a formatted `String`. The bucket boundaries are a shared
 * product decision — both platforms should agree on when "seconds" becomes
 * "minutes" — but the words are not: `"5 min ago"` cannot be translated, and
 * `"day" + if (days == 1) "" else "s"` is English pluralization compiled into
 * shared code, which no other language is obliged to follow.
 *
 * Each platform maps these arms to its own localized text (Android through
 * `strings.xml` / `plurals.xml`, iOS through its own catalog).
 *
 * Buckets are deliberately coarser than [com.chriscartland.garage.presentation.CheckInAge],
 * which carries leftover units ("1 min 30 sec ago") because a *recent*
 * heartbeat is the signal that pill conveys. This one answers "roughly how
 * long has the button been gone", so leftovers would be noise.
 */
sealed interface ButtonOfflineAge {
    /** No timestamp to measure from. Defensive; production should not hit this. */
    data object Unknown : ButtonOfflineAge

    /** Zero or negative elapsed time — clamps clock skew to the present. */
    data object JustNow : ButtonOfflineAge

    /** 1–59 seconds. */
    data class Seconds(
        val seconds: Int,
    ) : ButtonOfflineAge

    /** 1–59 minutes. Leftover seconds dropped. */
    data class Minutes(
        val minutes: Int,
    ) : ButtonOfflineAge

    /** 1–23 hours. Leftover minutes dropped. */
    data class Hours(
        val hours: Int,
    ) : ButtonOfflineAge

    /** 1+ whole days. Leftover hours dropped. */
    data class Days(
        val days: Int,
    ) : ButtonOfflineAge
}

/**
 * Which timestamp [ButtonOfflineAge] was measured from.
 *
 * The choice between them is a real shared decision (see
 * [ButtonHealthDisplayLogic]); the phrasing that distinguishes them is not.
 * Previously the shared layer prefixed the string with `"last seen "`, which
 * both hardcoded English and welded a sentence fragment to a value.
 */
enum class ButtonOfflineAgeSource {
    /** Measured from the device's last poll — when it actually went silent. */
    LAST_SEEN,

    /** Measured from when the server noticed. Fallback when no poll is recorded. */
    STATE_CHANGED,
}

/**
 * Buckets the time since a timestamp into a [ButtonOfflineAge].
 *
 * Boundaries are unchanged from the string formatter this replaced, so the
 * rendered result is identical wherever a platform words the arms the same way.
 */
object ButtonOfflineAgeMapper {
    fun forTimestamp(
        stateChangedAtSeconds: Long?,
        nowSeconds: Long,
    ): ButtonOfflineAge {
        if (stateChangedAtSeconds == null) return ButtonOfflineAge.Unknown
        val deltaSec = nowSeconds - stateChangedAtSeconds
        // Clock-skew clamp: a future timestamp reads as the present.
        if (deltaSec <= 0) return ButtonOfflineAge.JustNow
        if (deltaSec < SECONDS_PER_MINUTE) return ButtonOfflineAge.Seconds(deltaSec.toInt())
        val minutes = deltaSec / SECONDS_PER_MINUTE
        if (minutes < MINUTES_PER_HOUR) return ButtonOfflineAge.Minutes(minutes.toInt())
        val hours = minutes / MINUTES_PER_HOUR
        if (hours < HOURS_PER_DAY) return ButtonOfflineAge.Hours(hours.toInt())
        return ButtonOfflineAge.Days((hours / HOURS_PER_DAY).toInt())
    }

    private const val SECONDS_PER_MINUTE = 60L
    private const val MINUTES_PER_HOUR = 60L
    private const val HOURS_PER_DAY = 24L
}
