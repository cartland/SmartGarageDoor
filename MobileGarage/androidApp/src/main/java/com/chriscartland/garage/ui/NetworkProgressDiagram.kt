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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.presentation.DiagramEdgeStatus
import com.chriscartland.garage.presentation.DiagramNodeStatus
import com.chriscartland.garage.ui.theme.PreviewComponentSurface
import com.chriscartland.garage.ui.theme.networkFailed
import com.chriscartland.garage.ui.theme.networkNotStarted
import com.chriscartland.garage.ui.theme.networkSucceeded

// Node/edge statuses come from `:presentation-model`. They used to be declared
// here and mapped from RemoteButtonState by a nine-arm table in
// RemoteButtonDiagramMapping.kt, with iOS keeping its own parallel version —
// see RemoteButtonDiagramMapper for why that decision is now shared.

/**
 * State for a network progress diagram with N nodes and N-1 edges.
 */
data class NetworkDiagramState(
    val nodes: List<DiagramNodeStatus>,
    val edges: List<DiagramEdgeStatus>,
) {
    init {
        require(edges.size == nodes.size - 1) {
            "Expected ${nodes.size - 1} edges for ${nodes.size} nodes, got ${edges.size}"
        }
    }
}

private const val ANIMATION_DURATION_MS = 1000

/**
 * Generic network progress diagram: N nodes connected by N-1 edges.
 *
 * Each node renders as an icon. Each edge renders as a line between adjacent nodes.
 * The line style depends on the edge status: gray dashed (not started),
 * animated forward-moving dots (in progress), solid green (succeeded),
 * solid red (failed).
 */
@Composable
fun NetworkProgressDiagram(
    state: NetworkDiagramState,
    icons: List<ImageVector>,
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
    lineThickness: Float = 4f,
) {
    require(icons.size == state.nodes.size) {
        "Expected ${state.nodes.size} icons, got ${icons.size}"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "edge-animation")
    val animationPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(ANIMATION_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dot-phase",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(iconSize + 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in state.nodes.indices) {
            if (i > 0) {
                // Draw edge between node i-1 and node i.
                val edgeStatus = state.edges[i - 1]
                EdgeLine(
                    status = edgeStatus,
                    animationPhase = animationPhase,
                    modifier = Modifier.weight(1f),
                    thickness = lineThickness,
                )
            }
            NodeIcon(
                icon = icons[i],
                status = state.nodes[i],
                size = iconSize,
            )
        }
    }
}

@Composable
private fun NodeIcon(
    icon: ImageVector,
    status: DiagramNodeStatus,
    size: Dp,
) {
    val tint = when (status) {
        DiagramNodeStatus.IDLE -> networkNotStarted
        DiagramNodeStatus.ACTIVE -> MaterialTheme.colorScheme.primary
        DiagramNodeStatus.SUCCEEDED -> networkSucceeded
        DiagramNodeStatus.FAILED -> networkFailed
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size),
    )
}

@Composable
private fun EdgeLine(
    status: DiagramEdgeStatus,
    animationPhase: Float,
    modifier: Modifier = Modifier,
    thickness: Float = 4f,
) {
    val color = when (status) {
        DiagramEdgeStatus.NOT_STARTED -> networkNotStarted
        DiagramEdgeStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
        DiagramEdgeStatus.SUCCEEDED -> networkSucceeded
        DiagramEdgeStatus.FAILED -> networkFailed
    }

    Box(modifier = modifier.height(thickness.dp)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val y = size.height / 2
            val startX = 0f
            val endX = size.width

            when (status) {
                DiagramEdgeStatus.NOT_STARTED -> {
                    // Gray dashed line.
                    drawLine(
                        color = color,
                        start = Offset(startX, y),
                        end = Offset(endX, y),
                        strokeWidth = thickness,
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(12f, 8f),
                            phase = 0f,
                        ),
                    )
                }
                DiagramEdgeStatus.IN_PROGRESS -> {
                    // Animated dots moving left to right.
                    val dashLength = 12f
                    val gapLength = 8f
                    val totalPattern = dashLength + gapLength
                    // Phase shifts the dash pattern to create forward movement.
                    val phaseOffset = animationPhase * totalPattern
                    drawLine(
                        color = color,
                        start = Offset(startX, y),
                        end = Offset(endX, y),
                        strokeWidth = thickness,
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(dashLength, gapLength),
                            phase = -phaseOffset,
                        ),
                    )
                }
                DiagramEdgeStatus.SUCCEEDED -> {
                    // Solid green line.
                    drawLine(
                        color = color,
                        start = Offset(startX, y),
                        end = Offset(endX, y),
                        strokeWidth = thickness,
                    )
                }
                DiagramEdgeStatus.FAILED -> {
                    // Solid red line.
                    drawLine(
                        color = color,
                        start = Offset(startX, y),
                        end = Offset(endX, y),
                        strokeWidth = thickness,
                    )
                }
            }
        }
    }
}

// region Previews

private val DIAGRAM_ICONS = listOf(
    Icons.Filled.PhoneAndroid,
    Icons.Filled.Cloud,
    Icons.Filled.Home,
)

@Preview
@Composable
fun NetworkDiagramIdlePreview() {
    PreviewComponentSurface {
        NetworkProgressDiagram(
            state = NetworkDiagramState(
                nodes = listOf(DiagramNodeStatus.IDLE, DiagramNodeStatus.IDLE, DiagramNodeStatus.IDLE),
                edges = listOf(DiagramEdgeStatus.NOT_STARTED, DiagramEdgeStatus.NOT_STARTED),
            ),
            icons = DIAGRAM_ICONS,
        )
    }
}

@Preview
@Composable
fun NetworkDiagramSendingToServerPreview() {
    PreviewComponentSurface {
        NetworkProgressDiagram(
            state = NetworkDiagramState(
                nodes = listOf(DiagramNodeStatus.ACTIVE, DiagramNodeStatus.IDLE, DiagramNodeStatus.IDLE),
                edges = listOf(DiagramEdgeStatus.IN_PROGRESS, DiagramEdgeStatus.NOT_STARTED),
            ),
            icons = DIAGRAM_ICONS,
        )
    }
}

@Preview
@Composable
fun NetworkDiagramSendingToDoorPreview() {
    PreviewComponentSurface {
        NetworkProgressDiagram(
            state = NetworkDiagramState(
                nodes = listOf(DiagramNodeStatus.SUCCEEDED, DiagramNodeStatus.ACTIVE, DiagramNodeStatus.IDLE),
                edges = listOf(DiagramEdgeStatus.SUCCEEDED, DiagramEdgeStatus.IN_PROGRESS),
            ),
            icons = DIAGRAM_ICONS,
        )
    }
}

@Preview
@Composable
fun NetworkDiagramSucceededPreview() {
    PreviewComponentSurface {
        NetworkProgressDiagram(
            state = NetworkDiagramState(
                nodes = listOf(DiagramNodeStatus.SUCCEEDED, DiagramNodeStatus.SUCCEEDED, DiagramNodeStatus.SUCCEEDED),
                edges = listOf(DiagramEdgeStatus.SUCCEEDED, DiagramEdgeStatus.SUCCEEDED),
            ),
            icons = DIAGRAM_ICONS,
        )
    }
}

@Preview
@Composable
fun NetworkDiagramServerFailedPreview() {
    PreviewComponentSurface {
        NetworkProgressDiagram(
            state = NetworkDiagramState(
                nodes = listOf(DiagramNodeStatus.FAILED, DiagramNodeStatus.IDLE, DiagramNodeStatus.IDLE),
                edges = listOf(DiagramEdgeStatus.FAILED, DiagramEdgeStatus.NOT_STARTED),
            ),
            icons = DIAGRAM_ICONS,
        )
    }
}

@Preview
@Composable
fun NetworkDiagramDoorFailedPreview() {
    PreviewComponentSurface {
        NetworkProgressDiagram(
            state = NetworkDiagramState(
                nodes = listOf(DiagramNodeStatus.SUCCEEDED, DiagramNodeStatus.FAILED, DiagramNodeStatus.IDLE),
                edges = listOf(DiagramEdgeStatus.SUCCEEDED, DiagramEdgeStatus.FAILED),
            ),
            icons = DIAGRAM_ICONS,
        )
    }
}

// endregion
