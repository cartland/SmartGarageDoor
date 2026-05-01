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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Standard preview wrapper that applies [AppTheme] and a theme-aware page background.
 *
 * Use instead of `@Preview(showBackground = true)` — that flag hardcodes a white
 * background that ignores the active color scheme, so dark-mode screenshot tests
 * render dark UI on a light page. This wrapper paints the page in
 * [MaterialTheme.colorScheme.background], so light/dark previews look like the
 * real app.
 *
 * Idempotent if a caller already wraps in [AppTheme] (e.g. screenshot tests):
 * the inner [AppTheme] reads `isSystemInDarkTheme()` from the same configuration.
 */
@Composable
fun PreviewSurface(content: @Composable () -> Unit) {
    AppTheme {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
        ) { content() }
    }
}
