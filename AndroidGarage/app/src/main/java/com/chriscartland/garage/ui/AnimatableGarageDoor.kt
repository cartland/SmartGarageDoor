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

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.R
import com.chriscartland.garage.ui.theme.LocalDoorStatusColorScheme
import java.time.Duration

val DEFAULT_GARAGE_DOOR_ANIMATION_DURATION = Duration.ofSeconds(12)

const val CLOSED_POSITION = 0.0f
const val CLOSING_STATIC_POSITION = -0.2f
const val MIDWAY_POSITION = -0.5f
const val OPENING_STATIC_POSITION = -0.6f
const val OPEN_POSITION = -0.65f

@Composable
fun Opening(
    modifier: Modifier = Modifier,
    color: Color = LocalDoorStatusColorScheme.current.openContainerFresh,
    static: Boolean = false,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        GarageDoorAnimation(
            yInitialOffset = if (static) OPENING_STATIC_POSITION else CLOSED_POSITION,
            yTargetOffset = if (static) OPENING_STATIC_POSITION else OPEN_POSITION,
            contentDescription = "Garage Door Opening",
            modifier = Modifier
                .fillMaxSize(1f)
                .aspectRatio(1f),
            color = color,
        )
        Box(
            modifier = Modifier
                .fillMaxSize(0.3f)
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowForward,
                tint = MaterialTheme.colorScheme.onBackground,
                contentDescription = "Garage Door Opening",
                modifier = Modifier
                    .rotate(-90f)
                    .fillMaxSize(0.9f)
            )
        }
    }
}

@PreviewScreenSizes
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

@Composable
fun Closing(
    modifier: Modifier = Modifier,
    color: Color = LocalDoorStatusColorScheme.current.closedContainerFresh,
    static: Boolean = false,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        GarageDoorAnimation(
            yInitialOffset = if (static) CLOSING_STATIC_POSITION else OPEN_POSITION,
            yTargetOffset = if (static) CLOSING_STATIC_POSITION else CLOSED_POSITION,
            contentDescription = "Garage Door Closing",
            modifier = modifier,
            color = color,
        )
        Box(
            modifier = Modifier
                .fillMaxSize(0.3f)
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowForward,
                tint = MaterialTheme.colorScheme.onBackground,
                contentDescription = "Garage Door Opening",
                modifier = Modifier
                    .rotate(90f)
                    .fillMaxSize(0.9f)
            )
        }
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

@Composable
fun Closed(
    modifier: Modifier = Modifier,
    color: Color = LocalDoorStatusColorScheme.current.closedContainerFresh,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        GarageDoorAnimation(
            yInitialOffset = CLOSED_POSITION,
            yTargetOffset = CLOSED_POSITION,
            contentDescription = "Garage Door Closing",
            modifier = modifier,
            color = color,
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

@Composable
fun Open(
    modifier: Modifier = Modifier,
    color: Color = LocalDoorStatusColorScheme.current.openContainerFresh,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        GarageDoorAnimation(
            yInitialOffset = OPEN_POSITION,
            yTargetOffset = OPEN_POSITION,
            contentDescription = "Garage Door Open",
            modifier = modifier,
            color = color,
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

@Composable
fun Midway(
    modifier: Modifier = Modifier,
    color: Color = LocalDoorStatusColorScheme.current.openContainerFresh,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        GarageDoorAnimation(
            yInitialOffset = MIDWAY_POSITION,
            yTargetOffset = MIDWAY_POSITION,
            contentDescription = "Garage Door Unknown",
            modifier = modifier,
            color = color,
        )
        Box(
            modifier = Modifier
                .fillMaxSize(0.3f)
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                tint = MaterialTheme.colorScheme.onBackground,
                contentDescription = "Garage Door Opening",
                modifier = Modifier
                    .fillMaxSize(0.6f)
            )
        }
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

@Composable
fun GarageDoorAnimation(
    yInitialOffset: Float,
    yTargetOffset: Float,
    contentDescription: String?,
    color: Color?,
    modifier: Modifier = Modifier,
    duration: Duration = DEFAULT_GARAGE_DOOR_ANIMATION_DURATION,
    topDrawable: Int = R.drawable.garage_frame,
    bottomDrawable: Int = R.drawable.garage_door_only,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val animatingValue by infiniteTransition.animateFloat(
        initialValue = yInitialOffset,
        targetValue = yTargetOffset,
        animationSpec = infiniteRepeatable(
            animation = tween(duration.toMillis().toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ), label = ""
    )

    TopWithBottomOffset(
        topDrawable = topDrawable,
        bottomDrawable = bottomDrawable,
        contentDescription = contentDescription,
        modifier = modifier,
        offsetProportion = Offset(0f, animatingValue),
        color = color,
    )
}

@Composable
fun TopWithBottomOffset(
    topDrawable: Int,
    bottomDrawable: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    offsetProportion: Offset = Offset.Zero,
    color: Color?,
) {
    val topPainter = painterResource(topDrawable)
    val bottomPainter = painterResource(bottomDrawable)

    TopWithBottomOffset(
        topPainter = topPainter,
        bottomPainter = bottomPainter,
        contentDescription = contentDescription,
        modifier = modifier,
        offsetProportion = offsetProportion,
        color = color,
    )
}

@Composable
fun TopWithBottomOffset(
    topPainter: Painter,
    bottomPainter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    offsetProportion: Offset = Offset.Zero,
    color: Color?,
) {
    val colorFilter = color?.let {
        ColorFilter.tint(
            color = it,
        )
    }
    var topSize by remember { mutableStateOf(IntSize.Zero) }
    var bottomOffset = Offset(
        offsetProportion.x * topSize.width,
        offsetProportion.y * topSize.height,
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = bottomPainter,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .graphicsLayer {
                    translationX = bottomOffset.x
                    translationY = bottomOffset.y
                }
                .clip(OffsetRect(-bottomOffset)),
            colorFilter = colorFilter,
        )
        Image(
            painter = topPainter,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .onSizeChanged {
                    topSize = it
                },
            colorFilter = colorFilter,
        )
    }
}


/**
 * Create Shape for clipping a layout after translation.
 *
 * When you want to translate a Compose drawing at the graphics layer,
 * it will draw outside of the original layout.
 *
 * To make sure the graphics are only drawn inside the original bounds,
 * it can be helpful to create a clip(Shape) of the original bounds.
 *
 * When you move the graphics down and to the right, this can generate the
 * opposite shape up and to the left. See `Usage` based on an Offset.
 *
 * Usage:
 *
 * modifier = Modifier
 *     .graphicsLayer {
 *         translationX = offset.x
 *         translationY = offset.y
 *     }
 *     .clip(OffsetRect(-offset))
 */
class OffsetRect(val offset: Offset = Offset.Zero) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path().apply {
            moveTo(offset.x, offset.y)
            lineTo(size.width + offset.x, offset.y)
            lineTo(size.width + offset.x, size.height + offset.y)
            lineTo(offset.x, size.height + offset.y)
            close()
        }
        return Outline.Generic(path = path)
    }
}
