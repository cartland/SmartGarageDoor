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
