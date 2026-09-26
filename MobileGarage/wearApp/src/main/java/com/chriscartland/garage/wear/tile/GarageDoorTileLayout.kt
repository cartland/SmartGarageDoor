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

package com.chriscartland.garage.wear.tile

import android.content.ComponentName
import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.protolayout.material3.CardColors
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textDataCard
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.contentDescription
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.asLayoutConstraint
import androidx.wear.protolayout.types.asLayoutString
import androidx.wear.protolayout.types.layoutString
import com.chriscartland.garage.presentation.GlanceStatus
import com.chriscartland.garage.presentation.Liveness
import com.chriscartland.garage.wear.MainActivity
import com.chriscartland.garage.wear.R
import java.time.Instant

/**
 * Draws a [GlanceStatus] as a tile.
 *
 * Three things, in the order a glance reads them: whose door this is, what it
 * is doing, and how old that is.
 *
 * **The door is a filled shape, not coloured text.** The shared palette's
 * fills are deep enough that a word drawn in one would be hard to read on the
 * tile's dark ground — and carrying the colour at all is what gives the muted
 * treatment something to drain. A filled block behind a near-white word keeps
 * both: the colour is the door, and the word stays legible in every state
 * including the greyed one.
 *
 * **The age line is not decoration.** It is the whole reliability claim. A
 * tile is rendered by the system on the system's schedule, so a door position
 * shown without saying how old it is is asking to be believed on nothing. It
 * is omitted only when there is genuinely no age to state — see
 * [GarageTileWords.ageLine].
 */
internal object GarageDoorTileLayout {
    fun build(
        scope: MaterialScope,
        context: Context,
        status: GlanceStatus,
    ): LayoutElement =
        with(scope) {
            primaryLayout(
                titleSlot = {
                    text(
                        context.getString(R.string.tile_title).layoutString,
                        typography = Typography.TITLE_SMALL,
                    )
                },
                mainSlot = { doorBlock(context, status) },
                bottomSlot = durationSlot(context, status),
                // The whole tile opens the app, and that is the ONLY action on
                // it — see GarageDoorTileService for why a tile is the wrong
                // surface to put the garage button on. The card carries the
                // same action, so a tap on the door itself does not fall
                // through to nothing.
                onClick = openTheApp(context),
            )
        }

    /**
     * The door itself: the state word on a filled block in the door's colour.
     *
     * Built with the library's own `textDataCard` rather than a hand-rolled
     * `box` + `text`. The hand-rolled version RENDERED THE FILL BUT NOT THE
     * WORD — the block came out empty on the emulator while the title and age
     * lines beside it were fine, so the fault was specific to nesting text in
     * a container built here. Rather than keep guessing at ProtoLayout's
     * sizing rules across three-minute emulator cycles, this uses the
     * composition the library already tests, and passes the door's colours in
     * through [CardColors] instead of through modifiers.
     *
     * This is the emulator stage earning its keep: nothing else in the build
     * could have caught it. Every JVM test still passed, because every
     * decision the tile makes was correct — it was only the drawing that was
     * wrong, and only a render can see that.
     */
    private fun MaterialScope.doorBlock(
        context: Context,
        status: GlanceStatus,
    ): LayoutElement =
        textDataCard(
            onClick = openTheApp(context),
            title = {
                text(
                    context.getString(GarageTileWords.headline(status.headline)).layoutString,
                    typography = DOOR_WORD_TYPOGRAPHY,
                )
            },
            modifier = LayoutModifier.contentDescription(context.getString(R.string.cd_garage_door)),
            width = expand(),
            height = expand(),
            colors = CardColors(
                backgroundColor = GarageTileColors.doorFill(status.colorState, status.freshness).argb,
                titleColor = GarageTileColors.onDoorFill(status.freshness).argb,
                contentColor = GarageTileColors.onDoorFill(status.freshness).argb,
            ),
        )

    /**
     * The line under the door: how long it has been that way, or why we
     * cannot say.
     *
     * **When we can vouch for the reading, the number is produced by the
     * RENDERER, not by us.** `DynamicInstant.platformTimeWithSecondsPrecision()`
     * gives the renderer's own clock, and the difference from the instant the
     * door changed keeps counting between tile refreshes. A tile is redrawn on
     * the system's schedule, so a duration we formatted and froze would be
     * silently wrong by however long it had been sitting there — which is the
     * whole reason the old "checked 8 min ago" line had to go.
     *
     * **When we cannot, there is no duration at all**, by design: the shared
     * mapper withholds the instant, because a duration asserts the door has
     * been this way continuously and a door we have lost contact with may have
     * moved twice since. The line says [R.string.tile_not_confirmed] instead —
     * a word, not a measurement, so it cannot drift.
     *
     * Returning null rather than an empty string matters: `primaryLayout`
     * gives the main slot the bottom slot's space when there is no bottom
     * slot, so the door block grows into it instead of leaving a gap.
     */
    private fun durationSlot(
        context: Context,
        status: GlanceStatus,
    ): (MaterialScope.() -> LayoutElement)? {
        if (status.liveness == Liveness.STALE) {
            return {
                text(
                    context.getString(R.string.tile_not_confirmed).layoutString,
                    typography = Typography.BODY_SMALL,
                )
            }
        }
        val since = status.stateSinceEpochSeconds ?: return null
        val running = durationInState(context, since)
        return { text(running, typography = Typography.BODY_SMALL) }
    }

    /**
     * "8 min" / "3 hr" / "2 days", counted by the renderer from [since].
     *
     * The unit is chosen with a dynamic condition rather than at build time,
     * so a tile left on screen across the hour boundary switches from minutes
     * to hours by itself instead of showing "60 min", "61 min"...
     *
     * The static fallback passed alongside is what a renderer too old for
     * dynamic values shows. It is the value at BUILD time — frozen, and
     * therefore exactly the thing this method exists to avoid — so it is
     * deliberately the coarsest honest form rather than a precise-looking one.
     */
    private fun durationInState(
        context: Context,
        since: Long,
    ): androidx.wear.protolayout.types.LayoutString {
        val elapsed = DynamicInstant
            .withSecondsPrecision(Instant.ofEpochSecond(since))
            .durationUntil(DynamicInstant.platformTimeWithSecondsPrecision())

        val minutes = DynamicString.constant(context.getString(R.string.tile_duration_minutes, ""))
        val hours = DynamicString.constant(context.getString(R.string.tile_duration_hours, ""))
        val days = DynamicString.constant(context.getString(R.string.tile_duration_days, ""))

        val dynamic = DynamicString
            .onCondition(elapsed.toIntHours().lt(1))
            .use(elapsed.toIntMinutes().format().concat(minutes))
            .elseUse(
                DynamicString
                    .onCondition(elapsed.toIntDays().lt(1))
                    .use(elapsed.toIntHours().format().concat(hours))
                    .elseUse(elapsed.toIntDays().format().concat(days)),
            )

        return dynamic.asLayoutString(
            staticValue = context.getString(R.string.tile_not_confirmed),
            layoutConstraint = DURATION_WIDTH_CONSTRAINT,
        )
    }

    /**
     * The door word's size.
     *
     * `TITLE_LARGE`, not one of the `DISPLAY_*` tokens: on the emulator a
     * `DISPLAY_SMALL` text inside this card rendered as NOTHING — no TextView
     * in the hierarchy, no renderer warning — while `TITLE_SMALL` and
     * `BODY_SMALL` text in the surrounding slots rendered fine. The door word
     * is the one thing on this tile that must never be missing, so it uses a
     * token proven to draw here rather than the one the type scale would
     * nominate for a headline.
     */
    private const val DOOR_WORD_TYPOGRAPHY = Typography.TITLE_LARGE

    /**
     * Widest string the duration line can become, for layout sizing.
     *
     * A dynamic value has no width until it is evaluated, so the renderer
     * needs to be told what to reserve, and anything wider is TRUNCATED. The
     * first version reserved "88 days" and a door that had not moved in two
     * years rendered as "778 d…".
     *
     * A door can legitimately sit closed for years while the garage keeps
     * reporting in — liveness is about the check-in, not the door — so the
     * day count is genuinely unbounded in a way minutes and hours are not.
     * Four digits covers any plausible installation.
     */
    private val DURATION_WIDTH_CONSTRAINT = "8888 days".asLayoutConstraint()

    /** Opens the app on the door screen. The tile's only action. */
    private fun openTheApp(context: Context) =
        clickable(
            action = ActionBuilders.launchAction(
                ComponentName(context.packageName, MainActivity::class.java.name),
            ),
        )
}
