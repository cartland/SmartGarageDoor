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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Page-shaped preview wrapper for SCREEN-level Composables (HomeContent,
 * Settings, etc.) that fill the device viewport in production. Applies
 * [AppTheme] and paints the entire canvas with [MaterialTheme.colorScheme.background]
 * so dark-mode previews show the dark page filling the device, matching reality.
 *
 * Use instead of `@Preview(showBackground = true)` — that flag hardcodes a white
 * background that ignores the active color scheme.
 *
 * Idempotent if a caller already wraps in [AppTheme] (e.g. screenshot tests):
 * the inner [AppTheme] reads `isSystemInDarkTheme()` from the same configuration.
 *
 * For tiny components (pills, icons, buttons), prefer [PreviewComponentSurface] —
 * it themes only the area behind the component instead of the whole canvas.
 */
@Composable
fun PreviewScreenSurface(content: @Composable () -> Unit) {
    AppTheme {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
        ) { content() }
    }
}

/**
 * Component-shaped preview wrapper for tiny Composables (pills, icons,
 * buttons) that don't fill the device in production. Applies [AppTheme] and
 * paints the theme background only behind the component (`wrapContentSize`)
 * so the surrounding canvas stays at `@Preview(showBackground)`'s default
 * white — accurate to the component's actual scope.
 *
 * For full-screen Composables, prefer [PreviewScreenSurface].
 */
@Composable
fun PreviewComponentSurface(content: @Composable () -> Unit) {
    AppTheme {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.wrapContentSize(),
        ) { content() }
    }
}
