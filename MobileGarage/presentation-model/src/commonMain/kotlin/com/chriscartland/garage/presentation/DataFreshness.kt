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

package com.chriscartland.garage.presentation

/**
 * How much the app currently trusts what is on the screen — and, crucially,
 * how loudly it is allowed to say so.
 *
 * The middle case is the point of this type. Opening the app is not the same
 * event as discovering a fault, but until now they rendered identically: a
 * warm start with a cached door event whose check-in had aged past the
 * threshold went straight to the full alarm presentation — a banner, a Retry
 * button — for the second or so it took the return fetch to land. The user saw
 * an error every time they opened the app, and it was gone before they could
 * act on it. The words were not wrong, they were just far too early.
 *
 * So the answer is three-valued, and the middle value is a *promise not to
 * speak yet*:
 *
 * - [FRESH] — say nothing, show the normal thing.
 * - [SETTLING] — we are not sure this is current, but the app only just came
 *   back on screen and has barely started looking. Show it desaturated and
 *   dimmed, and change **no text**. A colour is a hint the user can ignore; a
 *   sentence is a claim they have to read and decide about.
 * - [STALE] — we have now been looking for longer than
 *   `AppSettleWindow.SETTLE_WINDOW_MILLIS` and it is still not current. Now
 *   the words are earned.
 *
 * Indicators only ever ACCUMULATE along that sequence. [STALE] keeps the dim,
 * desaturated look of [SETTLING] and adds text to it — a state never gets
 * *more* colourful as the news gets worse, which is what a reader's eye
 * expects.
 *
 * Per ADR-035 this type states what is true and says nothing about wording:
 * each platform decides what "dimmed" means in its own rendering vocabulary
 * (a Compose alpha, a SwiftUI `.opacity` + `.saturation`) and which of its own
 * banners a [STALE] verdict unlocks.
 */
enum class DataFreshness {
    /** Confirmed current. Normal presentation. */
    FRESH,

    /**
     * Not confirmed current, but still inside the grace window. Desaturate
     * and dim; do not change a single word on the screen.
     */
    SETTLING,

    /** Not confirmed current and the grace window has expired. Say so. */
    STALE,

    ;

    /**
     * True for [SETTLING] and [STALE] — the two states that render the door
     * desaturated and dimmed.
     *
     * Sugar over [DataFreshnessMapper.isMuted], which is where the rule
     * actually lives. The indirection buys cross-platform honesty: SKIE
     * bridges a Kotlin `enum class` as a Swift `enum` carrying its CASES but
     * not its members, so SwiftUI cannot read this property and calls the
     * mapper function instead. Defining the rule twice — once here, once in a
     * Swift extension — is exactly the drift ADR-035 exists to prevent, and
     * "which states look muted" is precisely the kind of decision that must
     * not be allowed to differ between the phone and the phone in your
     * pocket.
     */
    val isMuted: Boolean get() = DataFreshnessMapper.isMuted(this)

    /**
     * True only for [STALE] — the single gate on every *worded* freshness
     * indicator: the stale banner, the fetch-error banner, and any Retry
     * affordance hanging off them. Sugar over
     * [DataFreshnessMapper.isSpoken]; see [isMuted] for why the rule lives
     * there.
     */
    val isSpoken: Boolean get() = DataFreshnessMapper.isSpoken(this)
}

/**
 * Decides a [DataFreshness] from what is known about the data plus whether the
 * app is still inside its grace window. Pure and named per ADR-009.
 */
object DataFreshnessMapper {
    /**
     * Whether [freshness] renders the hero desaturated and dimmed — true for
     * both [DataFreshness.SETTLING] and [DataFreshness.STALE], because
     * indicators only ever accumulate.
     *
     * A function, not just the [DataFreshness.isMuted] property, so SwiftUI
     * can call it: SKIE gives Swift the enum's cases but not its members.
     */
    fun isMuted(freshness: DataFreshness): Boolean = freshness != DataFreshness.FRESH

    /**
     * Whether [freshness] has earned WORDS — true only for
     * [DataFreshness.STALE]. Gates the stale banner, the fetch-error banner
     * and any Retry hanging off them. Same Swift-reachability reason as
     * [isMuted].
     */
    fun isSpoken(freshness: DataFreshness): Boolean = freshness == DataFreshness.STALE

    /**
     * @param hasData whether any door event exists at all. False on a cold
     *   start with an empty cache — the screen is showing a placeholder, not a
     *   reading, so it is by definition not current.
     * @param isCheckInStale the device's last check-in is older than
     *   `CheckInStalenessManager.CHECK_IN_STALE_THRESHOLD_SECONDS`.
     * @param isFetchError the most recent fetch failed. A `Loading` result is
     *   deliberately NOT a reason: stale-while-revalidate keeps the previous
     *   good value on screen, and that value is either current or already
     *   caught by [isCheckInStale].
     * @param isSettling the app is inside the grace window `AppSettleWindow`
     *   opens each time the app becomes visible.
     */
    fun freshness(
        hasData: Boolean,
        isCheckInStale: Boolean,
        isFetchError: Boolean,
        isSettling: Boolean,
    ): DataFreshness {
        val current = hasData && !isCheckInStale && !isFetchError
        return when {
            current -> DataFreshness.FRESH
            isSettling -> DataFreshness.SETTLING
            else -> DataFreshness.STALE
        }
    }
}
