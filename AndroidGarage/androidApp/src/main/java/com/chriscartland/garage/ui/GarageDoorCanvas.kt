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

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect

// Original SVG viewport dimensions.
private const val VP_W = 284.8f
private const val VP_H = 216.8f

// Door panel layout in viewport coordinates.
private const val PANEL_X = 17.5f
private const val PANEL_WIDTH = 249f // 266.5 - 17.5
private const val PANEL_HEIGHT = 34f // 59 - 25 (inner fill height)
private const val PANEL_RADIUS = 8f
private val PANEL_Y_STARTS = floatArrayOf(17f, 64f, 111f, 158f)

// Handle on bottom panel.
private const val HANDLE_X = 131.5f
private const val HANDLE_Y = 190f
private const val HANDLE_W = 21f
private const val HANDLE_H = 4f
private const val HANDLE_RADIUS = 2f

// Frame stroke.
private const val FRAME_STROKE_WIDTH = 13.8f
private const val FRAME_CORNER_RADIUS = 18.4f

/**
 * Draws a garage door at a given vertical offset using pure Compose Canvas.
 *
 * Works in both production and screenshot test previews (no Android resources needed).
 *
 * @param doorOffset Vertical offset as a proportion of container height.
 *   0.0 = fully closed (door fills frame), negative = door sliding up (opening).
 *   Use position constants like [CLOSED_POSITION], [OPEN_POSITION].
 * @param color Tint color for the door and frame. A vertical gradient from [color]
 *   to a darker shade is applied.
 */
@Composable
fun GarageDoorCanvas(
    doorOffset: Float,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF3C5232),
) {
    val darkColor = blendColors(color, Color.Black, 0.5f)
    Canvas(modifier = modifier) {
        drawGarageDoor(doorOffset, color, darkColor)
    }
}

private fun DrawScope.drawGarageDoor(
    doorOffset: Float,
    color: Color,
    darkColor: Color,
) {
    val scaleX = size.width / VP_W
    val scaleY = size.height / VP_H

    val gradient = Brush.verticalGradient(
        0.3f to color,
        1f to darkColor,
    )

    // Clip door panels to the interior of the frame so they don't draw outside.
    val frameInsetX = (6.9f + FRAME_STROKE_WIDTH / 2f) * scaleX
    val frameInsetTop = (6.9f + FRAME_STROKE_WIDTH / 2f) * scaleY
    val frameBottom = size.height

    clipRect(
        left = frameInsetX,
        top = frameInsetTop,
        right = size.width - frameInsetX,
        bottom = frameBottom,
    ) {
        // Door panels — translated vertically by doorOffset.
        val panelOffsetPx = doorOffset * size.height
        for (panelY in PANEL_Y_STARTS) {
            val y = panelY * scaleY + panelOffsetPx
            val panelSize = Size(PANEL_WIDTH * scaleX, PANEL_HEIGHT * scaleY)
            drawRoundRect(
                brush = gradient,
                topLeft = Offset(PANEL_X * scaleX, y),
                size = panelSize,
                cornerRadius = CornerRadius(PANEL_RADIUS * scaleX, PANEL_RADIUS * scaleY),
            )
        }

        // Handle on bottom panel.
        val handleY = HANDLE_Y * scaleY + panelOffsetPx
        drawRoundRect(
            color = Color(0xFF111111),
            topLeft = Offset(HANDLE_X * scaleX, handleY),
            size = Size(HANDLE_W * scaleX, HANDLE_H * scaleY),
            cornerRadius = CornerRadius(HANDLE_RADIUS * scaleX, HANDLE_RADIUS * scaleY),
        )
    }

    // Frame — drawn on top as a stroke path (U-shape with rounded top corners).
    val framePath = Path().apply {
        val left = 6.9f * scaleX
        val right = (VP_W - 6.9f) * scaleX
        val top = 6.9f * scaleY
        val bottom = 209.9f * scaleY
        val cr = FRAME_CORNER_RADIUS * scaleX
        val crY = FRAME_CORNER_RADIUS * scaleY

        moveTo(left, bottom)
        lineTo(left, top + crY)
        // Top-left corner.
        arcTo(
            rect = Rect(left, top, left + cr * 2, top + crY * 2),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false,
        )
        lineTo(right - cr, top)
        // Top-right corner.
        arcTo(
            rect = Rect(right - cr * 2, top, right, top + crY * 2),
            startAngleDegrees = 270f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false,
        )
        lineTo(right, bottom)
    }

    drawPath(
        path = framePath,
        brush = gradient,
        style = Stroke(
            width = FRAME_STROKE_WIDTH * scaleX,
            cap = Stroke.DefaultCap,
        ),
    )
}
