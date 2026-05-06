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
import com.chriscartland.garage.ui.theme.PreviewScreenSurface
import com.chriscartland.garage.ui.theme.networkFailed
import com.chriscartland.garage.ui.theme.networkNotStarted
import com.chriscartland.garage.ui.theme.networkSucceeded

/** Status of a node (phone, server, door) in the diagram. */
enum class NodeStatus { Idle, Active, Succeeded, Failed }

/** Status of an edge (connection line) between two nodes. */
enum class EdgeStatus { NotStarted, InProgress, Succeeded, Failed }

/**
 * State for a network progress diagram with N nodes and N-1 edges.
 */
data class NetworkDiagramState(
    val nodes: List<NodeStatus>,
    val edges: List<EdgeStatus>,
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
    status: NodeStatus,
    size: Dp,
) {
    val tint = when (status) {
        NodeStatus.Idle -> networkNotStarted
        NodeStatus.Active -> MaterialTheme.colorScheme.primary
        NodeStatus.Succeeded -> networkSucceeded
        NodeStatus.Failed -> networkFailed
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
    status: EdgeStatus,
    animationPhase: Float,
    modifier: Modifier = Modifier,
    thickness: Float = 4f,
) {
    val color = when (status) {
        EdgeStatus.NotStarted -> networkNotStarted
        EdgeStatus.InProgress -> MaterialTheme.colorScheme.primary
        EdgeStatus.Succeeded -> networkSucceeded
        EdgeStatus.Failed -> networkFailed
    }

    Box(modifier = modifier.height(thickness.dp)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val y = size.height / 2
            val startX = 0f
            val endX = size.width

            when (status) {
                EdgeStatus.NotStarted -> {
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
                EdgeStatus.InProgress -> {
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
                EdgeStatus.Succeeded -> {
                    // Solid green line.
                    drawLine(
                        color = color,
                        start = Offset(startX, y),
                        end = Offset(endX, y),
                        strokeWidth = thickness,
                    )
                }
                EdgeStatus.Failed -> {
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
    PreviewScreenSurface {
        NetworkProgressDiagram(
            state = NetworkDiagramState(
                nodes = listOf(NodeStatus.Idle, NodeStatus.Idle, NodeStatus.Idle),
                edges = listOf(EdgeStatus.NotStarted, EdgeStatus.NotStarted),
            ),
            icons = DIAGRAM_ICONS,
        )
    }
}

@Preview
@Composable
fun NetworkDiagramSendingToServerPreview() {
    PreviewScreenSurface {
        NetworkProgressDiagram(
            state = NetworkDiagramState(
                nodes = listOf(NodeStatus.Active, NodeStatus.Idle, NodeStatus.Idle),
                edges = listOf(EdgeStatus.InProgress, EdgeStatus.NotStarted),
            ),
            icons = DIAGRAM_ICONS,
        )
    }
}

@Preview
@Composable
fun NetworkDiagramSendingToDoorPreview() {
    PreviewScreenSurface {
        NetworkProgressDiagram(
            state = NetworkDiagramState(
                nodes = listOf(NodeStatus.Succeeded, NodeStatus.Active, NodeStatus.Idle),
                edges = listOf(EdgeStatus.Succeeded, EdgeStatus.InProgress),
            ),
            icons = DIAGRAM_ICONS,
        )
    }
}

@Preview
@Composable
fun NetworkDiagramSucceededPreview() {
    PreviewScreenSurface {
        NetworkProgressDiagram(
            state = NetworkDiagramState(
                nodes = listOf(NodeStatus.Succeeded, NodeStatus.Succeeded, NodeStatus.Succeeded),
                edges = listOf(EdgeStatus.Succeeded, EdgeStatus.Succeeded),
            ),
            icons = DIAGRAM_ICONS,
        )
    }
}

@Preview
@Composable
fun NetworkDiagramServerFailedPreview() {
    PreviewScreenSurface {
        NetworkProgressDiagram(
            state = NetworkDiagramState(
                nodes = listOf(NodeStatus.Failed, NodeStatus.Idle, NodeStatus.Idle),
                edges = listOf(EdgeStatus.Failed, EdgeStatus.NotStarted),
            ),
            icons = DIAGRAM_ICONS,
        )
    }
}

@Preview
@Composable
fun NetworkDiagramDoorFailedPreview() {
    PreviewScreenSurface {
        NetworkProgressDiagram(
            state = NetworkDiagramState(
                nodes = listOf(NodeStatus.Succeeded, NodeStatus.Failed, NodeStatus.Idle),
                edges = listOf(EdgeStatus.Succeeded, EdgeStatus.Failed),
            ),
            icons = DIAGRAM_ICONS,
        )
    }
}

// endregion
