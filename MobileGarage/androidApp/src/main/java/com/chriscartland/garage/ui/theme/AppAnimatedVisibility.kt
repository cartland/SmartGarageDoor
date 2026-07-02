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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Project-default visibility transition for sections / rows that change
 * height but not width.
 *
 * Compose's stock `AnimatedVisibility(visible = ...)` defaults to
 * `fadeIn() + expandIn()` and `shrinkOut() + fadeOut()` — both 2D
 * (animates width AND height). For sections that fill the parent's
 * width and only their height changes (the common case in this app),
 * the horizontal expand/shrink reads as an unintentional zoom. 2.13.5
 * fixed exactly that bug in the Developer section.
 *
 * This wrapper bakes in `expandVertically() + fadeIn()` /
 * `shrinkVertically() + fadeOut()` so future call sites get the right
 * default without re-discovering the trap. The `label` parameter is
 * required (Compose tooling friendlier with labeled animations).
 *
 * If a future case genuinely needs 2D animation (a card growing in
 * place, a popover, etc.), use the upstream `AnimatedVisibility`
 * directly with explicit specs — that's a deliberate departure, not
 * the default.
 */
@Composable
fun AppAnimatedVisibility(
    visible: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        label = label,
        content = { content() },
    )
}
