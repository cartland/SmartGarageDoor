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
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        when (doorPosition) {
            DoorPosition.UNKNOWN -> Midway(
                color = color,
            )

            DoorPosition.CLOSED -> Closed(
                color = color,
            )

            DoorPosition.OPENING -> Opening(
                static = static,
                color = color,
            )

            DoorPosition.OPENING_TOO_LONG -> Midway(
                color = color,
            )

            DoorPosition.OPEN -> Open(
                color = color,
            )

            DoorPosition.OPEN_MISALIGNED -> Open(
                color = color,
            )

            DoorPosition.CLOSING -> Closing(
                static = static,
                color = color,
            )

            DoorPosition.CLOSING_TOO_LONG -> Midway(
                color = color,
            )

            DoorPosition.ERROR_SENSOR_CONFLICT -> Midway(
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
                .fadeBottom(fractionFaded = .5f, color = fadeColor, finalOpacity = 0.5f),
            static = static,
            color = color,
        )
    }
}

fun Modifier.fadeBottom(fractionFaded: Float, color: Color, finalOpacity: Float) = this.then(
    Modifier.drawWithContent {
        drawContent()
        drawFade(fractionFaded, color, finalOpacity)
    }
)

private fun DrawScope.drawFade(fractionFaded: Float, color: Color, finalOpacity: Float) {
    if (finalOpacity >= 1f || finalOpacity < 0f) {
        return
    }
    val height = size.height
    val startY = height * (1 - fractionFaded)
    val endY = height / (1 - finalOpacity)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, color),
            startY = startY,
            endY = endY,
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