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

// Design viewport — all coordinates below are in this unit space.
// The Canvas scales uniformly to fit the available size.
private const val VP = 300f

/** Aspect ratio of the garage door design (1:1 square). */
const val GARAGE_DOOR_ASPECT_RATIO = 1f

// Frame layout.
private const val FRAME_INSET = 10f
private const val FRAME_STROKE_WIDTH = 12f
private const val FRAME_CORNER_RADIUS = 16f
private const val FRAME_BOTTOM = 290f
private const val INTERIOR_TOP = FRAME_INSET + FRAME_STROKE_WIDTH / 2f // 16

// Door panel layout — 4 panels, evenly spaced (gap = pad = 10).
private const val PANEL_X = 20f
private const val PANEL_WIDTH = 260f
private const val PANEL_HEIGHT = 56f
private const val PANEL_RADIUS = 8f
private val PANEL_Y_STARTS = floatArrayOf(26f, 92f, 158f, 224f)

// Handle on bottom panel.
private const val HANDLE_X = 139f
private const val HANDLE_Y = 274f
private const val HANDLE_W = 22f
private const val HANDLE_H = 4f
private const val HANDLE_RADIUS = 2f

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
    // Uniform scale — fit within canvas without stretching.
    val scale = minOf(size.width / VP, size.height / VP)
    val drawSize = VP * scale
    // Center the drawing within the canvas.
    val offsetX = (size.width - drawSize) / 2f
    val offsetY = (size.height - drawSize) / 2f

    fun x(vp: Float) = vp * scale + offsetX

    fun y(vp: Float) = vp * scale + offsetY

    fun s(vp: Float) = vp * scale

    val gradient = Brush.verticalGradient(
        0.3f to color,
        1f to darkColor,
        startY = offsetY,
        endY = offsetY + drawSize,
    )

    // Clip door panels to the interior of the frame so they don't draw outside.
    val clipInset = FRAME_INSET + FRAME_STROKE_WIDTH / 2f

    clipRect(
        left = x(clipInset),
        top = y(clipInset),
        right = x(VP - clipInset),
        bottom = y(VP),
    ) {
        // Door panels — translated vertically by doorOffset.
        val panelOffsetPx = doorOffset * drawSize
        for (panelY in PANEL_Y_STARTS) {
            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x(PANEL_X), y(panelY) + panelOffsetPx),
                size = Size(s(PANEL_WIDTH), s(PANEL_HEIGHT)),
                cornerRadius = CornerRadius(s(PANEL_RADIUS)),
            )
        }

        // Handle on bottom panel.
        drawRoundRect(
            color = Color(0xFF111111),
            topLeft = Offset(x(HANDLE_X), y(HANDLE_Y) + panelOffsetPx),
            size = Size(s(HANDLE_W), s(HANDLE_H)),
            cornerRadius = CornerRadius(s(HANDLE_RADIUS)),
        )
    }

    // Frame — drawn on top as a stroke path (U-shape with rounded top corners).
    val cr = s(FRAME_CORNER_RADIUS)
    val framePath = Path().apply {
        val left = x(FRAME_INSET)
        val right = x(VP - FRAME_INSET)
        val top = y(FRAME_INSET)
        val bottom = y(FRAME_BOTTOM)

        moveTo(left, bottom)
        lineTo(left, top + cr)
        arcTo(
            rect = Rect(left, top, left + cr * 2, top + cr * 2),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false,
        )
        lineTo(right - cr, top)
        arcTo(
            rect = Rect(right - cr * 2, top, right, top + cr * 2),
            startAngleDegrees = 270f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false,
        )
        lineTo(right, bottom)
    }

    drawPath(
        path = framePath,
        brush = gradient,
        style = Stroke(width = s(FRAME_STROKE_WIDTH)),
    )
}
