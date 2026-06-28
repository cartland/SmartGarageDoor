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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.ui.theme.LocalDoorStatusColorScheme
import com.chriscartland.garage.ui.theme.PreviewComponentSurface
import java.time.Duration

val DEFAULT_GARAGE_DOOR_ANIMATION_DURATION: Duration = Duration.ofSeconds(12)

// The door-animation spec (offset constants + `DoorPosition → offset / overlay`
// mappings) is the shared `:domain` `DoorAnimation` (single source of truth,
// consumed identically by iOS `GarageDoorCanvas.swift`). Tier-1 brand surface
// (ADR-032) — do not re-declare local literals here. This file keeps only the
// Android-specific execution bits: the overlay Composables, the gradient color
// blend, and the previews.

@Composable
internal fun DirectionOverlay(
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
internal fun WarningOverlay() {
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

private const val PREVIEW_BOX_DP = 400
private const val PREVIEW_ICON_HEIGHT_DP = 200
private const val PREVIEW_ICON_WIDTH_DP = 300

@Composable
private fun PreviewBox(content: @Composable (Modifier) -> Unit) {
    PreviewComponentSurface {
        Box(
            modifier = Modifier.size(PREVIEW_BOX_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            content(
                Modifier
                    .wrapContentSize()
                    .heightIn(max = PREVIEW_ICON_HEIGHT_DP.dp)
                    .widthIn(max = PREVIEW_ICON_WIDTH_DP.dp),
            )
        }
    }
}

@Preview
@Composable
fun OpeningPreview() {
    PreviewBox { mod -> GarageIcon(DoorPosition.OPENING, modifier = mod, static = true) }
}

@Preview
@Composable
fun ClosingPreview() {
    PreviewBox { mod -> GarageIcon(DoorPosition.CLOSING, modifier = mod, static = true) }
}

@Preview
@Composable
fun ClosedPreview() {
    PreviewBox { mod ->
        GarageIcon(
            doorPosition = DoorPosition.CLOSED,
            modifier = mod,
            static = true,
            color = LocalDoorStatusColorScheme.current.closedFresh,
        )
    }
}

@Preview
@Composable
fun OpenPreview() {
    PreviewBox { mod -> GarageIcon(DoorPosition.OPEN, modifier = mod, static = true) }
}

@Preview
@Composable
fun MidwayPreview() {
    PreviewBox { mod -> GarageIcon(DoorPosition.UNKNOWN, modifier = mod, static = true) }
}

@Preview
@Composable
fun OpeningTooLongPreview() {
    PreviewBox { mod -> GarageIcon(DoorPosition.OPENING_TOO_LONG, modifier = mod, static = true) }
}

@Preview
@Composable
fun ClosingTooLongPreview() {
    PreviewBox { mod -> GarageIcon(DoorPosition.CLOSING_TOO_LONG, modifier = mod, static = true) }
}

@Preview
@Composable
fun OpenMisalignedPreview() {
    PreviewBox { mod -> GarageIcon(DoorPosition.OPEN_MISALIGNED, modifier = mod, static = true) }
}

@Preview
@Composable
fun ErrorSensorConflictPreview() {
    PreviewBox { mod -> GarageIcon(DoorPosition.ERROR_SENSOR_CONFLICT, modifier = mod, static = true) }
}
