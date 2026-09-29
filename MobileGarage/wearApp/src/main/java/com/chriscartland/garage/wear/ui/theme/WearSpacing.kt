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

package com.chriscartland.garage.wear.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing tokens for the watch, by ROLE, mirroring the phone's `Spacing.kt`
 * where the watch has the same role.
 *
 * The watch leans on Wear Material 3's own list, button and round-screen
 * paddings for nearly everything (a round screen's margins are the library's
 * business — CLAUDE.md § "Read the library's own KDoc"), so this holds only
 * the gaps the watch draws itself. That is why it is short; it is not a
 * copy of the phone's file waiting to be filled in.
 *
 * Pinned to the phone and to iOS by `MobileGarage/spacing-parity.json` +
 * `.github/scripts/spacing-parity.test.mjs` (strategy 4.5): a value that
 * drifts on one platform fails every PR.
 */
object WearSpacing {
    /** Tight grouping inside a single visual unit (a hint under its door). */
    val Tight = 4.dp
}
