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

package com.chriscartland.garage.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.chriscartland.garage.ui.theme.LocalDoorStatusColorScheme
import java.time.Duration

val DEFAULT_GARAGE_DOOR_ANIMATION_DURATION: Duration = Duration.ofSeconds(12)

// Door offset positions as proportion of viewport height (300×300 square).
// Negative = door slides up (opening). Panels at y=[22, 89, 156, 223], height 61.
// Clip inset at y=22 (frame + gap). At -0.75 (shift 225px):
//   Panel 4 bottom = 284 - 225 = 59, visible = 59 - 22 = 37px (≈60% of panel).
const val CLOSED_POSITION = 0.0f
const val CLOSING_STATIC_POSITION = -0.20f
const val MIDWAY_POSITION = -0.35f
const val OPENING_STATIC_POSITION = -0.65f
const val OPEN_POSITION = -0.75f

@Composable
private fun GarageDoorWithOverlay(
    doorOffset: Float,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF3C5232),
    animate: Boolean = false,
    animationTarget: Float = doorOffset,
    duration: Duration = DEFAULT_GARAGE_DOOR_ANIMATION_DURATION,
    overlay: @Composable (BoxScope.() -> Unit)? = null,
) {
    val currentOffset = if (animate) {
        val transition = rememberInfiniteTransition(label = "garageDoor")
        val animatedValue by transition.animateFloat(
            initialValue = doorOffset,
            targetValue = animationTarget,
            animationSpec = infiniteRepeatable(
                animation = tween(duration.toMillis().toInt(), easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "doorOffset",
        )
        animatedValue
    } else {
        doorOffset
    }

    Box(
        modifier = modifier.aspectRatio(GARAGE_DOOR_ASPECT_RATIO),
        contentAlignment = Alignment.Center,
    ) {
        GarageDoorCanvas(
            doorOffset = currentOffset,
            modifier = Modifier.fillMaxSize(),
            color = color,
        )
        if (overlay != null) {
            overlay()
        }
    }
}

@Composable
private fun DirectionOverlay(
    rotationDegrees: Float,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.3f)
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            tint = MaterialTheme.colorScheme.onBackground,
            contentDescription = contentDescription,
            modifier = Modifier
                .rotate(rotationDegrees)
                .fillMaxSize(0.9f),
        )
    }
}

@Composable
private fun WarningOverlay() {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.3f)
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            tint = MaterialTheme.colorScheme.onBackground,
            contentDescription = "Warning Symbol",
            modifier = Modifier.fillMaxSize(0.6f),
        )
    }
}

@Composable
fun Opening(
    modifier: Modifier = Modifier,
    color: Color = LocalDoorStatusColorScheme.current.openFresh,
    static: Boolean = false,
) {
    GarageDoorWithOverlay(
        doorOffset = if (static) OPENING_STATIC_POSITION else CLOSED_POSITION,
        modifier = modifier,
        color = color,
        animate = !static,
        animationTarget = OPEN_POSITION,
    ) {
        DirectionOverlay(-90f, "Up Arrow")
    }
}

@Composable
fun Closing(
    modifier: Modifier = Modifier,
    color: Color = LocalDoorStatusColorScheme.current.openFresh,
    static: Boolean = false,
) {
    GarageDoorWithOverlay(
        doorOffset = if (static) CLOSING_STATIC_POSITION else OPEN_POSITION,
        modifier = modifier,
        color = color,
        animate = !static,
        animationTarget = CLOSED_POSITION,
    ) {
        DirectionOverlay(90f, "Down Arrow")
    }
}

@Composable
fun Closed(
    modifier: Modifier = Modifier,
    color: Color = LocalDoorStatusColorScheme.current.closedFresh,
) {
    GarageDoorWithOverlay(
        doorOffset = CLOSED_POSITION,
        modifier = modifier,
        color = color,
    )
}

@Composable
fun Open(
    modifier: Modifier = Modifier,
    color: Color = LocalDoorStatusColorScheme.current.openFresh,
) {
    GarageDoorWithOverlay(
        doorOffset = OPEN_POSITION,
        modifier = modifier,
        color = color,
    )
}

@Composable
fun Midway(
    modifier: Modifier = Modifier,
    color: Color = LocalDoorStatusColorScheme.current.openFresh,
) {
    GarageDoorWithOverlay(
        doorOffset = MIDWAY_POSITION,
        modifier = modifier,
        color = color,
    ) {
        WarningOverlay()
    }
}

object GarageColors {
    fun blendColors(
        color1: Color,
        color2: Color,
        ratio: Float,
    ): Color {
        val color1Int = color1.toArgb()
        val color2Int = color2.toArgb()
        return Color(ColorUtils.blendARGB(color1Int, color2Int, ratio))
    }
}

// --- Previews ---

@Preview(showBackground = true)
@Composable
fun OpeningPreview() {
    Box(
        modifier = Modifier.size(400.dp),
        contentAlignment = Alignment.Center,
    ) {
        Opening(
            modifier = Modifier
                .wrapContentSize()
                .heightIn(max = 200.dp)
                .widthIn(max = 300.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ClosingPreview() {
    Box(
        modifier = Modifier.size(400.dp),
        contentAlignment = Alignment.Center,
    ) {
        Closing(
            modifier = Modifier
                .wrapContentSize()
                .heightIn(max = 200.dp)
                .widthIn(max = 300.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ClosedPreview() {
    Box(
        modifier = Modifier.size(400.dp),
        contentAlignment = Alignment.Center,
    ) {
        Closed(
            modifier = Modifier
                .wrapContentSize()
                .heightIn(max = 200.dp)
                .widthIn(max = 300.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun OpenPreview() {
    Box(
        modifier = Modifier.size(400.dp),
        contentAlignment = Alignment.Center,
    ) {
        Open(
            modifier = Modifier
                .wrapContentSize()
                .heightIn(max = 200.dp)
                .widthIn(max = 300.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MidwayPreview() {
    Box(
        modifier = Modifier.size(400.dp),
        contentAlignment = Alignment.Center,
    ) {
        Midway(
            modifier = Modifier
                .wrapContentSize()
                .heightIn(max = 200.dp)
                .widthIn(max = 300.dp),
        )
    }
}
