package com.chriscartland.garage.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.safeGesturesPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun SafePaddingDemo() {
    // Cycle through 3 safe_*_Padding() modifiers
    var cyclingValue by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            cyclingValue = when (cyclingValue) {
                1 -> 2
                2 -> 3
                else -> 1 // If it's 3, reset to 1
            }
        }
    }
    when (cyclingValue) {
        1 -> Box(
            modifier = Modifier
                .fillMaxSize()
                .safeGesturesPadding()
                .border(8.dp, Color.Red.copy(alpha = 0.5f))
        ) {
            Text(text = "safeGesturesPadding()", modifier = Modifier.align(Alignment.Center))
        }
        2 -> Box(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .border(8.dp, Color.Blue.copy(alpha = 0.5f))
        ) {
            Text(text = "safeContentPadding()", modifier = Modifier.align(Alignment.Center))
        }
        else -> Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .border(8.dp, Color.Green.copy(alpha = 0.5f))
        ) {
            Text(text = "safeDrawingPadding()", modifier = Modifier.align(Alignment.Center))
        }
    }
}