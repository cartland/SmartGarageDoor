/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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

package com.chriscartland.garage.wear.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import com.chriscartland.garage.domain.model.DoorAnimation
import com.chriscartland.garage.domain.model.DoorAnimationMemory
import com.chriscartland.garage.domain.model.DoorMotionKey
import com.chriscartland.garage.domain.model.DoorOverlayKind
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.GarageDoorGeometry
import com.chriscartland.garage.wear.R

/**
 * Animated garage door icon for Wear OS — the port of the phone's
 * `GarageIcon` (androidApp/.../ui/GarageIcon.kt), driven by the same shared
 * `:domain` `DoorAnimation` spec:
 * - Target offset is a pure function of `doorPosition`.
 * - Motion states (OPENING/CLOSING) tween linearly over the shared 12s duration.
 * - Terminal/error states settle via a slow, no-bounce spring.
 * - A motion state seen for the first time replays the full slide from the
 *   start; re-entry of the same event snaps to the target ([DoorAnimationMemory]).
 *
 * Renders statically at `staticPositionFor` under inspection mode so previews
 * are deterministic.
 *
 * @param suppressWarningOverlay set `true` only for the no-data cold-start
 *   presentation (mirrors the phone's `GarageIcon`): the door shape still
 *   renders (gray, midway) but the ⚠ badge is withheld — nothing is wrong,
 *   the watch just hasn't heard from the server yet. Real UNKNOWN events
 *   (with data) keep the badge.
 */
@Composable
fun WearGarageIcon(
    doorPosition: DoorPosition,
    animationMemory: DoorAnimationMemory,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF3C5232),
    lastChangeTimeSeconds: Long? = null,
    suppressWarningOverlay: Boolean = false,
) {
    if (LocalInspectionMode.current) {
        DoorIconBox(
            doorOffset = DoorAnimation.staticPositionFor(doorPosition),
            doorPosition = doorPosition,
            modifier = modifier,
            color = color,
            suppressWarningOverlay = suppressWarningOverlay,
        )
    } else {
        AnimatedDoorIcon(
            doorPosition = doorPosition,
            lastChangeTimeSeconds = lastChangeTimeSeconds,
            animationMemory = animationMemory,
            modifier = modifier,
            color = color,
            suppressWarningOverlay = suppressWarningOverlay,
        )
    }
}

@Composable
private fun AnimatedDoorIcon(
    doorPosition: DoorPosition,
    lastChangeTimeSeconds: Long?,
    animationMemory: DoorAnimationMemory,
    modifier: Modifier,
    color: Color,
    suppressWarningOverlay: Boolean,
) {
    // Same replay-once-per-motion-event logic as the phone icon (ADR-025).
    val key = DoorMotionKey(doorPosition, lastChangeTimeSeconds)
    val seedFromStart = remember(key) {
        !DoorAnimation.useSpringFor(doorPosition) && animationMemory.consumeAnimateFromStart(key)
    }
    val position = remember {
        Animatable(
            if (seedFromStart) {
                DoorAnimation.fromPositionFor(doorPosition)
            } else {
                DoorAnimation.targetPositionFor(doorPosition)
            },
        )
    }
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
                    durationMillis = DoorAnimation.ANIMATION_DURATION_SECONDS * MILLIS_PER_SECOND,
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
        suppressWarningOverlay = suppressWarningOverlay,
    )
}

@Composable
private fun DoorIconBox(
    doorOffset: Float,
    doorPosition: DoorPosition,
    modifier: Modifier,
    color: Color,
    suppressWarningOverlay: Boolean = false,
) {
    Box(
        modifier = modifier.aspectRatio(GarageDoorGeometry.ASPECT_RATIO),
        contentAlignment = Alignment.Center,
    ) {
        GarageDoorCanvas(
            doorOffset = doorOffset,
            modifier = Modifier.fillMaxSize(),
            color = color,
        )
        when (DoorAnimation.overlayFor(doorPosition)) {
            DoorOverlayKind.NONE -> Unit
            DoorOverlayKind.ARROW_UP -> OverlayBadge(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(R.string.cd_arrow_up),
            )
            DoorOverlayKind.ARROW_DOWN -> OverlayBadge(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.cd_arrow_down),
            )
            DoorOverlayKind.WARNING -> if (!suppressWarningOverlay) {
                OverlayBadge(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = stringResource(R.string.cd_warning),
                )
            }
        }
    }
}

@Composable
private fun OverlayBadge(
    imageVector: ImageVector,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val MILLIS_PER_SECOND = 1_000
