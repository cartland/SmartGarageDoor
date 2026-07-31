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
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.wear.ui.theme.WearRingColorScheme
import com.chriscartland.garage.wear.ui.theme.WearRingColors
import kotlinx.coroutines.delay

/**
 * The app's one "something is counting toward committing" indicator, shared by
 * the real garage button and the voice demo.
 *
 * It was two implementations until 0.5.1, and they had drifted: the hero screen
 * went neutral white in 0.4.1 with a commit that blooms inward into a reserved
 * band, while the demo kept the older peach ring that simply appeared complete
 * at the moment of commitment. The demo's entire job is to rehearse the real
 * interaction, so teaching a different vocabulary for the same moment is the one
 * thing it must not do — and two copies of an animation will always drift again.
 * One component, driven by one [rememberConfirmRingState], is the only way that
 * stays true.
 *
 * Callers place it as the topmost layer over the full screen; it takes no input,
 * so it can never block a gesture underneath.
 *
 * @param colors which world this ring is counting in. Defaults to the real one,
 *   so a surface has to ASK to look like the simulation — a new caller that
 *   forgets gets the honest ring, not a rehearsal-coloured one.
 */
@Composable
internal fun ConfirmRing(
    ring: ConfirmRingState,
    modifier: Modifier = Modifier,
    colors: WearRingColorScheme = WearRingColors.neutral,
) {
    Canvas(modifier = modifier) {
        val stroke = HeroLayout.RING_STROKE_DP.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)

        if (ring.showTrack) {
            // Faint full track under the sweep: "here is how far you have to go".
            drawArc(
                color = colors.track,
                startAngle = ConfirmRingSpec.ARC_START_ANGLE,
                sweepAngle = ConfirmRingSpec.FULL_SWEEP,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                alpha = ConfirmRingSpec.TRACK_ALPHA,
                style = Stroke(width = stroke),
            )
        }
        when {
            // Grown as a stroke rather than as a filled circle so it reads as
            // THIS ring swelling, not a new shape appearing over it.
            //
            // Its own arc rect, not the resting one: a stroke widens about its
            // centreline, so reusing the resting geometry would push half the
            // growth off-screen and send the other half inward past the band.
            // Re-inset by half the CURRENT stroke instead, which pins the outer
            // edge and spends every added pixel inward, where the band is.
            ring.bloom > 0f -> {
                val bloomStroke = HeroLayout.bloomStrokeDp(ring.bloom).dp.toPx()
                val bloomInset = HeroLayout
                    .bloomArcInsetDp(HeroLayout.bloomStrokeDp(ring.bloom))
                    .dp
                    .toPx()
                drawArc(
                    color = colors.committed,
                    startAngle = ConfirmRingSpec.ARC_START_ANGLE,
                    sweepAngle = ConfirmRingSpec.FULL_SWEEP,
                    useCenter = false,
                    topLeft = Offset(bloomInset, bloomInset),
                    size = Size(size.width - bloomStroke, size.height - bloomStroke),
                    style = Stroke(width = bloomStroke),
                )
            }
            ring.inFlight -> {
                val segment = ConfirmRingSpec.FULL_SWEEP / ConfirmRingSpec.IN_FLIGHT_SEGMENTS
                val visible = segment * ConfirmRingSpec.IN_FLIGHT_SEGMENT_FILL
                repeat(ConfirmRingSpec.IN_FLIGHT_SEGMENTS) { index ->
                    drawArc(
                        color = colors.committed,
                        startAngle = ConfirmRingSpec.ARC_START_ANGLE +
                            ring.rotation +
                            index * segment,
                        sweepAngle = visible,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            ring.sweep > 0f -> drawArc(
                color = colors.sweep,
                startAngle = ConfirmRingSpec.ARC_START_ANGLE,
                sweepAngle = ConfirmRingSpec.FULL_SWEEP * ring.sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * Drives [ConfirmRing]'s animation from a [RingPhase], and is the reason the two
 * screens genuinely match rather than merely looking similar.
 *
 * Split out from the drawing so a fixture can pin any frame: the commit bloom
 * lasts about 700ms and is the single most consequential thing either screen
 * draws — it is the app saying "that worked" — yet as one inseparable animated
 * Composable the one frame most worth reviewing was the one frame no gallery
 * could show. `ScreenshotStagesActivity` passes a canned [ConfirmRingState]
 * straight to [ConfirmRing] instead.
 *
 * @param sweepDurationMillis how long a full sweep takes. The only thing the two
 *   callers legitimately disagree about: the button counts a 2s hold, the demo
 *   counts its 3s cancel window.
 */
@Composable
internal fun rememberConfirmRingState(
    phase: RingPhase,
    sweepDurationMillis: Int,
    inFlight: Boolean,
): ConfirmRingState {
    // An Animatable rather than animateFloatAsState because the transitions are
    // not all the same shape: a new hold SNAPS to empty, an abandoned one
    // UNWINDS, and a committed one blooms.
    val sweep = remember { Animatable(0f) }
    val bloom = remember { Animatable(0f) }
    LaunchedEffect(phase, sweepDurationMillis) {
        when (phase) {
            RingPhase.Sweeping -> {
                bloom.snapTo(0f)
                // A new attempt always starts from empty. Without this it would
                // inherit whatever the previous unwind had left on screen and
                // over-report progress against the countdown that actually
                // fires, which always starts from zero — the ring would promise
                // a commit sooner than the timer will deliver one.
                sweep.snapTo(0f)
                sweep.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = sweepDurationMillis,
                        easing = LinearEasing,
                    ),
                )
            }
            // Deliberately does nothing: see RingPhase.Settling.
            RingPhase.Settling -> Unit
            RingPhase.Committed -> {
                sweep.snapTo(1f)
                bloom.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        ConfirmRingSpec.BLOOM_FILL_MILLIS,
                        easing = FastOutLinearInEasing,
                    ),
                )
                // Beat at full solid, so "it filled" is a moment you see rather
                // than an inflection you might miss.
                delay(ConfirmRingSpec.BLOOM_SOLID_MILLIS.toLong())
                bloom.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        ConfirmRingSpec.BLOOM_RECEDE_MILLIS,
                        easing = LinearOutSlowInEasing,
                    ),
                )
                // Falls through to a complete ring (sweep is still 1), which is
                // what stays on screen while the outcome is outstanding.
            }
            RingPhase.Idle -> {
                bloom.snapTo(0f)
                // Unwind, never snap. The sweep is handed back at a speed you
                // can follow, which is what makes "nothing was sent" legible: a
                // ring that vanishes reads as a glitch, one that runs backwards
                // reads as a cancellation.
                sweep.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        ConfirmRingSpec.UNWIND_MILLIS,
                        easing = LinearOutSlowInEasing,
                    ),
                )
            }
        }
    }

    // Rotation for the in-flight ring. Composed ONLY while something is
    // outstanding: an infinite transition renders frames for as long as it
    // exists, and this is a watch.
    val rotation = if (inFlight) {
        val transition = rememberInfiniteTransition(label = "inFlight")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = ConfirmRingSpec.FULL_SWEEP,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    ConfirmRingSpec.ROTATION_PERIOD_MILLIS,
                    easing = LinearEasing,
                ),
            ),
            label = "inFlightRotation",
        )
        angle
    } else {
        0f
    }

    return ConfirmRingState(
        sweep = sweep.value,
        bloom = bloom.value,
        rotation = rotation,
        inFlight = inFlight,
        showTrack = phase == RingPhase.Sweeping,
    )
}

/** One frame of the confirm ring, so a fixture can pin any of them. */
internal data class ConfirmRingState(
    val sweep: Float = 0f,
    val bloom: Float = 0f,
    val rotation: Float = 0f,
    val inFlight: Boolean = false,
    val showTrack: Boolean = false,
)

/**
 * What the indicator is currently saying.
 *
 * Shared vocabulary for both surfaces. Each derives its own phase from its own
 * state machine — see `HeroRing.phaseFor` and `VoiceRing.phaseFor` — but the
 * meanings, and therefore the animations, are identical.
 */
internal enum class RingPhase {
    /** Nothing outstanding: give back whatever sweep is left. */
    Idle,

    /** Counting toward a commit. */
    Sweeping,

    /**
     * The gesture ended and the state machine has not yet said which way it
     * went. On the hero screen `onTap` and `reset` both post to a Channel, so
     * for a frame or two `AwaitingConfirmation` is exactly as consistent with a
     * completed hold as with an abandoned one. Holding the ring still is the
     * only honest thing to draw: unwinding here would flinch on a press that
     * succeeded, and blooming here would claim a press that was never sent.
     *
     * The voice demo never produces this — its controller moves straight from
     * the cancel window to a decided outcome.
     */
    Settling,

    /** The commit really happened. This is what the bloom is allowed to key off. */
    Committed,
}

/** Timings and angles shared by both surfaces' rings. */
internal object ConfirmRingSpec {
    const val ARC_START_ANGLE = -90f
    const val FULL_SWEEP = 360f
    const val TRACK_ALPHA = 0.25f

    /**
     * How long an abandoned attempt takes to give its sweep back.
     *
     * Long enough to read as a rewind rather than a disappearance — the sweep is
     * the only record that the gesture happened, and watching it run backwards
     * is what makes "nothing was sent" land. Deliberately much shorter than the
     * build-up: this is a retraction, not a second gesture.
     */
    const val UNWIND_MILLIS = 450

    /** Commit bloom: ring thickens inward to fill its band, beats, recedes. */
    const val BLOOM_FILL_MILLIS = 220
    const val BLOOM_SOLID_MILLIS = 120
    const val BLOOM_RECEDE_MILLIS = 380

    /**
     * In-flight ring: how long one full turn takes, and how much of each segment
     * is drawn. Slow enough to read as deliberate progress rather than a spinner
     * in a hurry — it can be on screen for ten seconds or more.
     */
    const val ROTATION_PERIOD_MILLIS = 1_600
    const val IN_FLIGHT_SEGMENTS = 3
    const val IN_FLIGHT_SEGMENT_FILL = 0.62f
}
