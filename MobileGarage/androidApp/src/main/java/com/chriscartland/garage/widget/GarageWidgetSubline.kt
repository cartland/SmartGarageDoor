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

package com.chriscartland.garage.widget

import com.chriscartland.garage.presentation.GlanceStatus
import com.chriscartland.garage.presentation.Liveness

/**
 * The second line of the widget: when the door entered its state, or why we
 * cannot say.
 */
sealed interface WidgetSubline {
    /**
     * The door has been this way since this instant. Rendered as an ABSOLUTE
     * clock time ("since 3:42 PM"), never as an elapsed duration.
     */
    data class Since(
        val epochSeconds: Long,
    ) : WidgetSubline

    /** We cannot vouch for the reading, so there is no span to claim. */
    data object Stale : WidgetSubline

    /** Nothing to add — no door known at all, so the headline says it. */
    data object Silent : WidgetSubline
}

/**
 * Picks the widget's second line.
 *
 * ## Why an instant and not a duration
 *
 * [GlanceStatus] states the rule: **a surface that can render a self-updating
 * duration should, and one that cannot must show no number rather than a frozen
 * one.** Both Wear surfaces can — a complication through
 * TimeDifferenceComplicationText, a tile through ProtoLayout's platform clock —
 * so both show "2m" and it is the RENDERER counting, not us.
 *
 * A home-screen widget can do neither. Its only self-updating text primitive is
 * the RemoteViews Chronometer, whose format is a stopwatch (74:13:52) — the
 * shape the maintainer rejected on the watch when "3h" came out as "3H",
 * unreadable as a measurement. And a widget's own update floor is 30 minutes,
 * so a duration WE compute is the "8 min" bug in a worse form than the one that
 * prompted removing it.
 *
 * So the widget shows the INSTANT instead, and that is strictly better than the
 * fallback: "since 3:42 PM" is a statement about a moment that has already
 * happened, so **no update schedule can make it wrong.** It needs no renderer
 * support, cannot drift, and still answers the question the duration was there
 * to answer. The reader does one subtraction; nothing lies to them while they
 * do it.
 *
 * ## Why STALE drops the line entirely
 *
 * A "since" is a claim that the door has been that way CONTINUOUSLY. A door we
 * have lost contact with may have moved twice since we last heard, so the claim
 * is not ours to make — [GlanceStatus] has already nulled
 * `stateSinceEpochSeconds` for exactly that reason, and this reads the liveness
 * word in its place. Same inversion the complication makes, for the same
 * reason.
 */
object GarageWidgetSubline {
    fun forStatus(status: GlanceStatus): WidgetSubline {
        val since = status.stateSinceEpochSeconds
        return when {
            // Trust the mapper's withholding: a non-null instant here has
            // already been judged presentable. Re-checking liveness would
            // duplicate that rule and invite the two copies to drift.
            since != null -> WidgetSubline.Since(since)
            status.liveness == Liveness.STALE -> WidgetSubline.Stale
            else -> WidgetSubline.Silent
        }
    }
}
