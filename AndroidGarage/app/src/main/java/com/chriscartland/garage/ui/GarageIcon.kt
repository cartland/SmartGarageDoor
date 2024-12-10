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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.tooling.preview.Preview
import com.chriscartland.garage.door.DoorPosition

@Composable
private fun GarageIcon(
    doorPosition: DoorPosition,
    modifier: Modifier = Modifier.Companion,
    static: Boolean = false,
    color: Color = Color.Companion.Blue,
) {
    val iconModifier = Modifier.Companion.aspectRatio(1f)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Companion.Center,
    ) {
        when (doorPosition) {
            DoorPosition.UNKNOWN -> Midway(
                modifier = iconModifier,
                color = color,
            )

            DoorPosition.CLOSED -> Closed(
                modifier = iconModifier,
                color = color,
            )

            DoorPosition.OPENING -> Opening(
                modifier = iconModifier,
                static = static,
                color = color,
            )

            DoorPosition.OPENING_TOO_LONG -> Midway(
                modifier = iconModifier,
                color = color,
            )

            DoorPosition.OPEN -> Open(
                modifier = iconModifier,
                color = color,
            )

            DoorPosition.OPEN_MISALIGNED -> Open(
                modifier = iconModifier,
                color = color,
            )

            DoorPosition.CLOSING -> Closing(
                modifier = iconModifier,
                static = static,
                color = color,
            )

            DoorPosition.CLOSING_TOO_LONG -> Midway(
                modifier = iconModifier,
                color = color,
            )

            DoorPosition.ERROR_SENSOR_CONFLICT -> Midway(
                modifier = iconModifier,
                color = color,
            )
        }
    }
}

@Composable
fun FadedGarageIcon(
    doorPosition: DoorPosition,
    modifier: Modifier = Modifier,
    static: Boolean = false,
    color: Color = Color.Blue,
    fadeColor: Color = Color.White,
) {
    Box(
        modifier = modifier,
    ) {
        GarageIcon(
            doorPosition = doorPosition,
            modifier = Modifier
                .fadeBottom(fraction = .7f, color = fadeColor),
            static = static,
            color = color,
        )
    }
}

fun Modifier.fadeBottom(fraction: Float, color: Color) = this.then(
    Modifier.drawWithContent {
        drawContent()
        drawFade(fraction, color)
    }
)

private fun DrawScope.drawFade(fraction: Float, color: Color) {
    val height = size.height
    val startY = height * (1 - fraction) // Start the fade at 90% of the height

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, color), // Fade to transparent
            startY = startY,
            endY = height
        )
    )
}

@Preview(showBackground = true)
@Composable
fun GarageIconPreview() {
    GarageIcon(
        doorPosition = DoorPosition.OPENING,
    )
}

@Preview(showBackground = true)
@Composable
fun FadedGarageIconPreview() {
    FadedGarageIcon(
        doorPosition = DoorPosition.OPENING,
    )
}