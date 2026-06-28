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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.GarageDoorGeometry
import com.chriscartland.garage.ui.theme.LocalDoorStatusColorScheme
import com.chriscartland.garage.ui.theme.PreviewComponentSurface
import java.time.Duration

/**
 * Renders the garage door icon for a [DoorPosition].
 *
 * Animation contract: see `AndroidGarage/docs/DOOR_ANIMATION.md`. In short:
 * - Target offset is a pure function of `doorPosition`
 *   ([targetPositionFor]) — no dependency on the current animation value.
 * - Motion states (OPENING/CLOSING) tween linearly over [duration].
 * - Terminal/error states settle via a slow, no-bounce spring.
 * - A motion state seen for the first time (cold-open / first view of this
 *   event) replays the full slide from the start; re-entry of the same event
 *   snaps to the target. See [LocalDoorAnimationMemory].
 *
 * @param lastChangeTimeSeconds server timestamp of the door's last position
 *   change. Combined with [doorPosition] it identifies one motion event so
 *   the slide replays once per event (not on every tab-switch / back-nav).
 *   Pass the live event's value for the animated icon; `null` is fine for
 *   one-off renders (it just animates every fresh composition).
 * @param static when `true`, render at [staticPositionFor] (no animation,
 *   no Animatable). Used by past-event snapshots in the recent events list.
 *   Always treated as `true` under [LocalInspectionMode] (Studio previews +
 *   screenshot tests) so reference PNGs are deterministic — Layoutlib can't
 *   advance `LaunchedEffect`, so an animated `OPENING` would otherwise
 *   render at the start frame and look identical to `CLOSED`.
 */
@Composable
fun GarageIcon(
    doorPosition: DoorPosition,
    modifier: Modifier = Modifier,
    static: Boolean = false,
    color: Color = LocalDoorStatusColorScheme.current.openFresh,
    duration: Duration = DEFAULT_GARAGE_DOOR_ANIMATION_DURATION,
    lastChangeTimeSeconds: Long? = null,
) {
    if (static || LocalInspectionMode.current) {
        DoorIconBox(
            doorOffset = DoorAnimation.staticPositionFor(doorPosition),
            doorPosition = doorPosition,
            modifier = modifier,
            color = color,
        )
    } else {
        AnimatedDoorIcon(
            doorPosition = doorPosition,
            lastChangeTimeSeconds = lastChangeTimeSeconds,
            modifier = modifier,
            color = color,
            duration = duration,
        )
    }
}

@Composable
private fun AnimatedDoorIcon(
    doorPosition: DoorPosition,
    lastChangeTimeSeconds: Long?,
    modifier: Modifier,
    color: Color,
    duration: Duration,
) {
    // Decide ONCE per fresh composition whether this icon should replay the
    // full slide from the "from" end. True only for a motion state whose event
    // this memory hasn't animated yet — i.e. cold-open / first view. Re-entry
    // of an already-animated event seeds at the target (snap, no replay).
    // Consuming inside remember(key) keys the decision to this composition;
    // live state changes are driven by the LaunchedEffect below from the
    // current value (so a mid-slide direction flip reverses smoothly), not by
    // re-seeding here. See AnimatableGarageDoor.fromPositionFor + ADR-025.
    val memory = LocalDoorAnimationMemory.current
    val key = DoorMotionKey(doorPosition, lastChangeTimeSeconds)
    val seedFromStart = remember(key) {
        !DoorAnimation.useSpringFor(doorPosition) && memory.consumeAnimateFromStart(key)
    }
    // Hoisted Animatable: one position state for this icon instance. Seeded at
    // the "from" end when replaying, else at the target. LaunchedEffect is
    // keyed on the enum so same-value re-emits do not restart the animation.
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

/**
 * Test-support semantics: publishes the live door offset so the
 * instrumented animation audit ([GarageDoorAnimationBehaviorTest]) can read
 * the actual `Animatable` value the real wiring produces frame-by-frame.
 * The trajectory is otherwise unobservable — screenshot tests render
 * `static = true` and can't advance a `LaunchedEffect`. Production cost is
 * one Float in the semantics tree per icon; nothing reads it at runtime.
 */
val DoorOffsetSemanticsKey = SemanticsPropertyKey<Float>("DoorOffset")
private var SemanticsPropertyReceiver.doorOffsetSemantics by DoorOffsetSemanticsKey

@Composable
private fun DoorIconBox(
    doorOffset: Float,
    doorPosition: DoorPosition,
    modifier: Modifier,
    color: Color,
) {
    Box(
        modifier = modifier
            .aspectRatio(GarageDoorGeometry.ASPECT_RATIO)
            .semantics { doorOffsetSemantics = doorOffset },
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
    PreviewComponentSurface {
        GarageIcon(
            doorPosition = DoorPosition.OPENING,
            static = true,
        )
    }
}
