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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.ui.theme.LocalDoorStatusColorScheme
import com.chriscartland.garage.ui.theme.PreviewSurface
import java.time.Duration

/**
 * Renders the garage door icon for a [DoorPosition].
 *
 * Animation contract: see `AndroidGarage/docs/DOOR_ANIMATION.md`. In short:
 * - Target offset is a pure function of `doorPosition`
 *   ([targetPositionFor]) — no dependency on the current animation value.
 * - Motion states (OPENING/CLOSING) tween linearly over [duration].
 * - Terminal/error states settle via a slow, no-bounce spring.
 *
 * @param static when `true`, render at [staticPositionFor] (no animation,
 *   no Animatable). Used by past-event snapshots in the recent events list.
 */
@Composable
fun GarageIcon(
    doorPosition: DoorPosition,
    modifier: Modifier = Modifier,
    static: Boolean = false,
    color: Color = LocalDoorStatusColorScheme.current.openFresh,
    duration: Duration = DEFAULT_GARAGE_DOOR_ANIMATION_DURATION,
) {
    if (static) {
        DoorIconBox(
            doorOffset = DoorAnimation.staticPositionFor(doorPosition),
            doorPosition = doorPosition,
            modifier = modifier,
            color = color,
        )
    } else {
        AnimatedDoorIcon(
            doorPosition = doorPosition,
            modifier = modifier,
            color = color,
            duration = duration,
        )
    }
}

@Composable
private fun AnimatedDoorIcon(
    doorPosition: DoorPosition,
    modifier: Modifier,
    color: Color,
    duration: Duration,
) {
    // Hoisted Animatable: one position state for this icon instance.
    // LaunchedEffect is keyed on the enum so same-value re-emits do not
    // restart the animation. The pure mappings (target/initial/spec) are
    // defined in AnimatableGarageDoor.kt.
    val position = remember { Animatable(DoorAnimation.initialPositionFor(doorPosition)) }
    LaunchedEffect(doorPosition) {
        val target = DoorAnimation.targetPositionFor(doorPosition)
        if (DoorAnimation.useSpringFor(doorPosition)) {
            position.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessVeryLow,
                ),
                initialVelocity = 0f,
            )
        } else {
            position.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = duration.toMillis().toInt(),
                    easing = LinearEasing,
                ),
            )
        }
    }
    DoorIconBox(
        doorOffset = position.value,
        doorPosition = doorPosition,
        modifier = modifier,
        color = color,
    )
}

@Composable
private fun DoorIconBox(
    doorOffset: Float,
    doorPosition: DoorPosition,
    modifier: Modifier,
    color: Color,
) {
    Box(
        modifier = modifier.aspectRatio(GARAGE_DOOR_ASPECT_RATIO),
        contentAlignment = Alignment.Center,
    ) {
        GarageDoorCanvas(
            doorOffset = doorOffset,
            modifier = Modifier.fillMaxSize(),
            color = color,
        )
        when (DoorAnimation.overlayFor(doorPosition)) {
            OverlayKind.NONE -> Unit
            OverlayKind.ARROW_UP -> DirectionOverlay(-90f, "Up Arrow")
            OverlayKind.ARROW_DOWN -> DirectionOverlay(90f, "Down Arrow")
            OverlayKind.WARNING -> WarningOverlay()
        }
    }
}

@Preview
@Composable
fun GarageIconPreview() {
    PreviewSurface {
        GarageIcon(
            doorPosition = DoorPosition.OPENING,
            static = true,
        )
    }
}
