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

package com.chriscartland.garage.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * Named spacing tokens for screen-level padding, item spacing, and tight
 * groupings. Names describe role, not value — `Spacing.Screen` instead
 * of `16.dp`.
 *
 * Full proposal and migration plan: `AndroidGarage/docs/SPACING_PLAN.md`.
 */
object Spacing {
    /**
     * Horizontal padding between screen content and the device edges.
     *
     * **Applied ONCE** at `Main.kt`'s `NavDisplay` route wrapper. Child
     * Composables must not re-apply it. PR #589 doubled the Settings
     * card to 32dp by adding a second 16dp wrapper inside the screen's
     * content; #593 was the fix. The comment at `Main.kt:173-178`
     * codifies this rule.
     */
    val Screen = 16.dp

    /**
     * Vertical `contentPadding` on every screen-level `LazyColumn`.
     * Provides breathing room above the first item and below the last.
     */
    val ListVertical = 16.dp

    /**
     * `Arrangement.spacedBy(...)` between items in a screen-level list.
     */
    val BetweenItems = 8.dp

    /**
     * Tight grouping inside a single visual unit: icon ↔ text inside
     * a pill, supporting text ↔ headline, label ↔ control.
     */
    val Tight = 4.dp

    /**
     * Section header padding values. Applied to the header's enclosing
     * Row / Text. Used identically across `HomeSection`, `SettingsSection`,
     * and `HistoryDaySection` — this is the most disciplined spacing
     * pattern in the app.
     *
     * Total gap from header bottom to first list item is
     * `SectionHeaderBottom + LazyColumn.spacedBy(BetweenItems)` = 16dp.
     */
    val SectionHeaderStart = 16.dp
    val SectionHeaderTop = 8.dp
    val SectionHeaderBottom = 8.dp

    /**
     * Standard `contentPadding` for screen-level `LazyColumn`s whose
     * bottom edge sits directly above the bottom NavigationBar.
     *
     * Top = [ListVertical] (16dp) — breathing room before the first item.
     * Bottom = 24dp — clearance between the last item and the tab bar.
     * Asymmetric on purpose: the bar is opaque chrome the eye reads as a
     * hard boundary, so a flush-to-bar last item feels cramped. The 24dp
     * absorbs the previous "16dp contentPadding + 8dp tail-spacer item"
     * pattern into a single token — parent owns the gap, no per-screen
     * tail spacer needed.
     *
     * Apply via `LazyColumn(contentPadding = Spacing.ListContentPadding, ...)`.
     * Use raw [ListVertical] when the LazyColumn does NOT end at the tab
     * bar (e.g. `DiagnosticsContent` has action buttons below its
     * LazyColumn, so the chrome clearance is owned by the wrapper Column).
     */
    val ListContentPadding = PaddingValues(top = ListVertical, bottom = 24.dp)
}

/**
 * Interior padding for card bodies. Three role-named variants reflect
 * three legitimate use cases observed in production. Adding a fourth
 * requires ≥2 real call sites.
 */
object CardPadding {
    /**
     * Default uniform card body. Use for cards with normal-density
     * content (lists, controls, forms).
     */
    val Standard = PaddingValues(16.dp)

    /**
     * Card with hero content (large icon, illustration, status display)
     * that benefits from extra vertical breathing room.
     */
    val Tall = PaddingValues(vertical = 24.dp, horizontal = 16.dp)

    /**
     * Banner-style alert card. Slimmer vertical padding keeps the
     * banner from dominating the screen.
     */
    val Compact = PaddingValues(vertical = 12.dp, horizontal = 16.dp)
}

/**
 * `start` padding on `HorizontalDivider` between list rows. Inset
 * values align the divider with the leading text of the row above /
 * below, not with the leading icon.
 */
object DividerInset {
    /**
     * Material `ListItem` with the standard 24dp leading icon.
     * 56dp = 16dp leading padding + 24dp icon + 16dp icon-to-text gap.
     */
    val ListItem = 56.dp

    /**
     * `ListItem` with a 40dp leading icon (e.g. `HistoryContent`'s
     * `GarageIcon`). Larger leading slot pushes the divider further.
     */
    val LargeLeading = 72.dp

    /**
     * Row with no leading icon — divider spans the full
     * card-content width, inset only by the card's interior padding.
     */
    val FullWidth = 16.dp
}

/**
 * Spacing inside and between buttons.
 */
object ButtonSpacing {
    /** Between leading icon and text label inside a single Button. */
    val IconText = 8.dp

    /** Between vertically-stacked action buttons (e.g. Diagnostics' Export → Clear). */
    val Stacked = 16.dp

    /**
     * Between an inline message and an adjacent action button
     * (e.g. `HomeAlertCard`'s message ↔ button row).
     */
    val Inline = 12.dp
}

/**
 * Spacing around prose blocks. The app does not yet have a centralized
 * paragraph-style typography system; these tokens are the closest thing
 * to "vertical breathing room around text."
 */
object ParagraphSpacing {
    /** Between a section title and the body text immediately below it. */
    val TitleToBody = 4.dp

    /**
     * Between an icon and the prose it labels (empty states, alert
     * cards, inline warnings).
     */
    val IconToText = 12.dp

    /**
     * Vertical padding around a standalone warning / info paragraph
     * not inside a card.
     */
    val Block = 8.dp
}

/**
 * Maximum content widths for the screen-level route wrapper. Caps
 * the width of each screen's content on tablets, foldables, and
 * landscape phones; on phones below the cap value, `widthIn(max = ...)`
 * is a no-op.
 *
 * Applied at the same `Main.kt` route wrapper that owns `Spacing.Screen`.
 *
 * Forward-compatible with two-pane layout: when two-pane mode lands
 * (behind a runtime toggle), it consumes the same `ContentWidth`
 * tokens — the list pane in two-pane mode reuses these widths rather
 * than introducing a new set.
 */
object ContentWidth {
    /**
     * Default cap for screen content. Roughly Material 3's medium
     * `WindowSizeClass` upper bound. Phones unaffected; tablets,
     * foldables, and landscape get centered content with margins.
     */
    val Standard = 640.dp
}
