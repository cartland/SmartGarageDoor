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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember

// Create the DoorStatusTheme composable
@Composable
fun DoorStatusTheme(
    doorStatusColorScheme: DoorStatusColorScheme,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember { doorStatusColorScheme }
    CompositionLocalProvider(LocalDoorStatusColorScheme provides colorScheme) {
        content()
    }
}

// Define the DoorStatusTheme object
object DoorStatusTheme {
    val colorScheme: DoorStatusColorScheme
        @Composable
        get() = LocalDoorStatusColorScheme.current
}

// Define the CompositionLocal to hold the DoorStatusColorScheme
val LocalDoorStatusColorScheme = compositionLocalOf {
    doorStatusLightScheme
}
