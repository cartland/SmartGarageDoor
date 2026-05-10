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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.ui.theme.LocalDoorStatusColorScheme
import com.chriscartland.garage.ui.theme.PreviewComponentSurface
import java.time.Duration

val DEFAULT_GARAGE_DOOR_ANIMATION_DURATION: Duration = Duration.ofSeconds(12)

// Door offset positions as proportion of viewport height (300×300 square).
// Negative = door slides up (opening). Panels at y=[22, 89, 156, 223], height 61.
// Clip inset at y=22 (frame + gap). At -0.75 (shift 225px):
//   Panel 4 bottom = 284 - 225 = 59, visible = 59 - 22 = 37px (≈60% of panel).
const val CLOSED_POSITION = 0.0f
const val CLOSING_STATIC_POSITION = -0.20f
const val MIDWAY_POSITION = -0.35f
const val OPENING_STATIC_POSITION = -0.65f
const val OPEN_POSITION = -0.75f

internal enum class OverlayKind { NONE, ARROW_UP, ARROW_DOWN, WARNING }

/**
 * Pure mappings from [DoorPosition] to the visual primitives used by
 * [GarageIcon]. Same state always produces the same outputs — animation
 * trajectories depend on current value, but the *targets* don't. See
 * `AndroidGarage/docs/DOOR_ANIMATION.md` for the contract.
 *
 * Each `when` is exhaustive (no `else`) so adding a [DoorPosition] value
 * forces a decision at compile time.
 */
object DoorAnimation {
    /** Target offset to animate toward for the given state. */
    fun targetPositionFor(doorPosition: DoorPosition): Float =
        when (doorPosition) {
            DoorPosition.UNKNOWN -> MIDWAY_POSITION
            DoorPosition.CLOSED -> CLOSED_POSITION
            DoorPosition.OPENING -> OPEN_POSITION
            DoorPosition.OPENING_TOO_LONG -> MIDWAY_POSITION
            DoorPosition.OPEN -> OPEN_POSITION
            DoorPosition.OPEN_MISALIGNED -> OPEN_POSITION
            DoorPosition.CLOSING -> CLOSED_POSITION
            DoorPosition.CLOSING_TOO_LONG -> MIDWAY_POSITION
            DoorPosition.ERROR_SENSOR_CONFLICT -> MIDWAY_POSITION
        }

    /**
     * Initial Animatable seed for a given state.
     *
     * Always equal to [targetPositionFor] — a freshly composed icon
     * renders at the target position with no animation. The motion
     * animation (`OPENING` / `CLOSING`) only fires when [doorPosition]
     * **changes** during the icon's lifetime (state CLOSED→OPENING
     * triggers `LaunchedEffect` to animate from CLOSED to OPEN), not on
     * every fresh composition.
     *
     * Pre-2.16.4 behavior was: `OPENING → CLOSED_POSITION` and
     * `CLOSING → OPEN_POSITION` so the icon animated the full motion on
     * first compose (including screen return mid-motion). That re-ran
     * the open/close animation every time the user navigated back to
     * Home while the server still reported a transient OPENING/CLOSING
     * state — visible flicker that didn't represent reality (the
     * animation timing didn't sync with the physical door). The
     * [overlayFor] arrows (ARROW_UP / ARROW_DOWN) preserve the
     * "in motion" visual cue without re-animating.
     */
    fun initialPositionFor(doorPosition: DoorPosition): Float = targetPositionFor(doorPosition)

    /**
     * Which animation spec family applies.
     *
     * - `false` → tween (linear) for OPENING/CLOSING — matches a real garage
     *   door's roughly constant-speed motion.
     * - `true` → spring (slow, no overshoot) for terminal/error/unknown —
     *   states "settle" to their target.
     */
    fun useSpringFor(doorPosition: DoorPosition): Boolean =
        when (doorPosition) {
            DoorPosition.OPENING, DoorPosition.CLOSING -> false
            DoorPosition.UNKNOWN,
            DoorPosition.CLOSED,
            DoorPosition.OPENING_TOO_LONG,
            DoorPosition.OPEN,
            DoorPosition.OPEN_MISALIGNED,
            DoorPosition.CLOSING_TOO_LONG,
            DoorPosition.ERROR_SENSOR_CONFLICT,
            -> true
        }

    /**
     * Snapshot offset for a non-animated render — used when
     * `GarageIcon(static = true)`. For motion states the snapshot is a
     * mid-cycle position so the door visibly looks "in motion" without
     * actually animating.
     */
    fun staticPositionFor(doorPosition: DoorPosition): Float =
        when (doorPosition) {
            DoorPosition.OPENING -> OPENING_STATIC_POSITION
            DoorPosition.CLOSING -> CLOSING_STATIC_POSITION
            DoorPosition.UNKNOWN,
            DoorPosition.CLOSED,
            DoorPosition.OPENING_TOO_LONG,
            DoorPosition.OPEN,
            DoorPosition.OPEN_MISALIGNED,
            DoorPosition.CLOSING_TOO_LONG,
            DoorPosition.ERROR_SENSOR_CONFLICT,
            -> targetPositionFor(doorPosition)
        }

    /** Which overlay icon to draw on top of the door, if any. */
    internal fun overlayFor(doorPosition: DoorPosition): OverlayKind =
        when (doorPosition) {
            DoorPosition.OPENING -> OverlayKind.ARROW_UP
            DoorPosition.CLOSING -> OverlayKind.ARROW_DOWN
            DoorPosition.UNKNOWN,
            DoorPosition.OPENING_TOO_LONG,
            DoorPosition.CLOSING_TOO_LONG,
            DoorPosition.ERROR_SENSOR_CONFLICT,
            -> OverlayKind.WARNING
            DoorPosition.CLOSED,
            DoorPosition.OPEN,
            DoorPosition.OPEN_MISALIGNED,
            -> OverlayKind.NONE
        }
}

@Composable
internal fun DirectionOverlay(
    rotationDegrees: Float,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.3f)
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            tint = MaterialTheme.colorScheme.onBackground,
            contentDescription = contentDescription,
            modifier = Modifier
                .rotate(rotationDegrees)
                .fillMaxSize(0.9f),
        )
    }
}

@Composable
internal fun WarningOverlay() {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.3f)
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            tint = MaterialTheme.colorScheme.onBackground,
            contentDescription = "Warning Symbol",
            modifier = Modifier.fillMaxSize(0.6f),
        )
    }
}

object GarageColors {
    fun blendColors(
        color1: Color,
        color2: Color,
        ratio: Float,
    ): Color {
        val color1Int = color1.toArgb()
        val color2Int = color2.toArgb()
        return Color(ColorUtils.blendARGB(color1Int, color2Int, ratio))
    }
}

// --- Previews ---

private const val PREVIEW_BOX_DP = 400
private const val PREVIEW_ICON_HEIGHT_DP = 200
private const val PREVIEW_ICON_WIDTH_DP = 300

@Composable
private fun PreviewBox(content: @Composable (Modifier) -> Unit) {
    PreviewComponentSurface {
        Box(
            modifier = Modifier.size(PREVIEW_BOX_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            content(
                Modifier
                    .wrapContentSize()
                    .heightIn(max = PREVIEW_ICON_HEIGHT_DP.dp)
                    .widthIn(max = PREVIEW_ICON_WIDTH_DP.dp),
            )
        }
    }
}

@Preview
@Composable
fun OpeningPreview() {
    PreviewBox { mod -> GarageIcon(DoorPosition.OPENING, modifier = mod, static = true) }
}

@Preview
@Composable
fun ClosingPreview() {
    PreviewBox { mod -> GarageIcon(DoorPosition.CLOSING, modifier = mod, static = true) }
}

@Preview
@Composable
fun ClosedPreview() {
    PreviewBox { mod ->
        GarageIcon(
            doorPosition = DoorPosition.CLOSED,
            modifier = mod,
            static = true,
            color = LocalDoorStatusColorScheme.current.closedFresh,
        )
    }
}

@Preview
@Composable
fun OpenPreview() {
    PreviewBox { mod -> GarageIcon(DoorPosition.OPEN, modifier = mod, static = true) }
}

@Preview
@Composable
fun MidwayPreview() {
    PreviewBox { mod -> GarageIcon(DoorPosition.UNKNOWN, modifier = mod, static = true) }
}

@Preview
@Composable
fun OpeningTooLongPreview() {
    PreviewBox { mod -> GarageIcon(DoorPosition.OPENING_TOO_LONG, modifier = mod, static = true) }
}

@Preview
@Composable
fun ClosingTooLongPreview() {
    PreviewBox { mod -> GarageIcon(DoorPosition.CLOSING_TOO_LONG, modifier = mod, static = true) }
}

@Preview
@Composable
fun OpenMisalignedPreview() {
    PreviewBox { mod -> GarageIcon(DoorPosition.OPEN_MISALIGNED, modifier = mod, static = true) }
}

@Preview
@Composable
fun ErrorSensorConflictPreview() {
    PreviewBox { mod -> GarageIcon(DoorPosition.ERROR_SENSOR_CONFLICT, modifier = mod, static = true) }
}
