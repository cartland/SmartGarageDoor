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

package com.chriscartland.garage.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.domain.model.DoorAnimation.CLOSED_POSITION
import com.chriscartland.garage.domain.model.DoorAnimation.OPEN_POSITION
import com.chriscartland.garage.domain.model.DoorPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration

/**
 * Audit of the live garage-door animation **trajectory** — the behavior the
 * user actually sees, not just the static target mappings.
 *
 * `DoorAnimationTest` (a shared commonTest) pins the pure
 * `DoorAnimation.*For` mappings. This test pins the *wiring* in
 * [GarageIcon] / `AnimatedDoorIcon`: the `remember { Animatable(initial) }`
 * seed plus the `LaunchedEffect(doorPosition)` that drives `animateTo`.
 * That wiring is where "the animation sometimes plays, sometimes doesn't"
 * lives, and it is unobservable from screenshot tests (they render
 * `static = true` and Layoutlib can't advance a `LaunchedEffect`).
 *
 * Mechanism: drive `mainClock` manually (`autoAdvance = false`), flip
 * `doorPosition`, advance virtual time in steps, and read the live offset
 * back through the [DoorOffsetSemanticsKey] test-support semantics property.
 *
 * ## Behavior matrix this audit pins (see `docs/DOOR_ANIMATION.md`)
 *
 * | Entry scenario                          | OPENING               | CLOSING                |
 * |-----------------------------------------|-----------------------|------------------------|
 * | Fresh composition, NEW event (cold-open)| tween CLOSED→OPEN      | tween OPEN→CLOSED       |
 * | Fresh composition, already-animated     | snap to OPEN, no move  | snap to CLOSED, no move|
 * | Live transition while composed          | tween CLOSED→OPEN      | tween OPEN→CLOSED       |
 *
 * The first row is the cold-open / first-view case: a motion event this
 * [DoorAnimationMemory] hasn't seen replays the full slide from the start
 * (the behavior the user asked for — "start at the beginning regardless of
 * how long it's been in that state"). The second row is re-entry (tab-switch
 * / back-nav) of an *already-animated* event: it snaps to the target so the
 * slide is not replayed on every fresh composition (the 2.16.4 concern that
 * motivated suppressing it). The third row — a state change while the icon is
 * already alive — animates from the current value as before. See ADR-025
 * (amended): the seed is now gated on event identity, not always equal to the
 * target.
 *
 * **Instrumented — requires a connected device/emulator.** Not part of
 * `validate.sh`; runs via `./scripts/run-instrumented-tests.sh` and the
 * post-merge instrumented job. (validate.sh DOES compile this source set, so
 * a signature break is still caught pre-submit.)
 */
class GarageDoorAnimationBehaviorTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // Short virtual duration keeps the audit fast; the curve shape is
    // identical to the production 12s tween, just compressed.
    private val testDuration: Duration = Duration.ofMillis(1000)
    private val stepMs = 250L
    private val steps = 6 // 6 * 250 = 1500ms > duration, so the tween completes

    // --- Fresh composition, NEW event (cold-open): slides from the start ------

    @Test
    fun freshComposition_opening_newEvent_slidesFromStart() {
        val memory = DoorAnimationMemory()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDoorAnimationMemory provides memory) {
                GarageIcon(
                    doorPosition = DoorPosition.OPENING,
                    modifier = Modifier.size(ICON_DP.dp),
                    duration = testDuration,
                    lastChangeTimeSeconds = EVENT_T,
                )
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        // Seeded at the CLOSED "from" end, not the target — proves the slide
        // starts at the beginning regardless of how long the door has been
        // opening. Then slides up to OPEN.
        assertEquals(
            "slide should start at the CLOSED end",
            CLOSED_POSITION,
            composeTestRule.doorOffset(),
            START_EPS,
        )
        val samples = composeTestRule.sampleOffsets()
        assertMonotonic(samples, decreasing = true)
        assertMotionOccurred(samples)
        assertEquals(OPEN_POSITION, samples.last(), EPS)
    }

    @Test
    fun freshComposition_closing_newEvent_slidesFromStart() {
        val memory = DoorAnimationMemory()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDoorAnimationMemory provides memory) {
                GarageIcon(
                    doorPosition = DoorPosition.CLOSING,
                    modifier = Modifier.size(ICON_DP.dp),
                    duration = testDuration,
                    lastChangeTimeSeconds = EVENT_T,
                )
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        assertEquals(
            "slide should start at the OPEN end",
            OPEN_POSITION,
            composeTestRule.doorOffset(),
            START_EPS,
        )
        val samples = composeTestRule.sampleOffsets()
        assertMonotonic(samples, decreasing = false)
        assertMotionOccurred(samples)
        assertEquals(CLOSED_POSITION, samples.last(), EPS)
    }

    // --- Fresh composition, already-animated event (re-entry): snaps ----------

    @Test
    fun reentry_opening_sameEvent_snapsToTarget_withNoMotion() {
        val memory = DoorAnimationMemory()
        // Simulate the event having already animated (e.g. the cold-open slide
        // played, then the user tab-switched away and back).
        memory.consumeAnimateFromStart(DoorMotionKey(DoorPosition.OPENING, EVENT_T))
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDoorAnimationMemory provides memory) {
                GarageIcon(
                    doorPosition = DoorPosition.OPENING,
                    modifier = Modifier.size(ICON_DP.dp),
                    duration = testDuration,
                    lastChangeTimeSeconds = EVENT_T,
                )
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        // Already animated → seeds at the target (OPEN), no replay.
        assertEquals(OPEN_POSITION, composeTestRule.doorOffset(), EPS)
        assertNoMotion(composeTestRule.sampleOffsets(), expected = OPEN_POSITION)
    }

    @Test
    fun reentry_closing_sameEvent_snapsToTarget_withNoMotion() {
        val memory = DoorAnimationMemory()
        memory.consumeAnimateFromStart(DoorMotionKey(DoorPosition.CLOSING, EVENT_T))
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDoorAnimationMemory provides memory) {
                GarageIcon(
                    doorPosition = DoorPosition.CLOSING,
                    modifier = Modifier.size(ICON_DP.dp),
                    duration = testDuration,
                    lastChangeTimeSeconds = EVENT_T,
                )
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        assertEquals(CLOSED_POSITION, composeTestRule.doorOffset(), EPS)
        assertNoMotion(composeTestRule.sampleOffsets(), expected = CLOSED_POSITION)
    }

    // --- Live transition while composed: slides ------------------------------

    @Test
    fun liveTransition_closedToOpening_slidesOpen() {
        val state = mutableStateOf(DoorPosition.CLOSED)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDoorAnimationMemory provides DoorAnimationMemory()) {
                GarageIcon(
                    doorPosition = state.value,
                    modifier = Modifier.size(ICON_DP.dp),
                    duration = testDuration,
                    lastChangeTimeSeconds = EVENT_T,
                )
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        // Settled closed before the transition.
        assertEquals(CLOSED_POSITION, composeTestRule.doorOffset(), EPS)

        composeTestRule.runOnUiThread { state.value = DoorPosition.OPENING }
        composeTestRule.mainClock.advanceTimeByFrame() // relaunch LaunchedEffect

        val samples = composeTestRule.sampleOffsets()
        // Door slides up: offset decreases CLOSED(0.0) → OPEN(-0.75).
        assertMonotonic(samples, decreasing = true)
        assertMotionOccurred(samples)
        assertEquals(OPEN_POSITION, samples.last(), EPS)
    }

    @Test
    fun liveTransition_openToClosing_slidesClosed() {
        val state = mutableStateOf(DoorPosition.OPEN)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDoorAnimationMemory provides DoorAnimationMemory()) {
                GarageIcon(
                    doorPosition = state.value,
                    modifier = Modifier.size(ICON_DP.dp),
                    duration = testDuration,
                    lastChangeTimeSeconds = EVENT_T,
                )
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        assertEquals(OPEN_POSITION, composeTestRule.doorOffset(), EPS)

        composeTestRule.runOnUiThread { state.value = DoorPosition.CLOSING }
        composeTestRule.mainClock.advanceTimeByFrame()

        val samples = composeTestRule.sampleOffsets()
        // Door slides down: offset increases OPEN(-0.75) → CLOSED(0.0).
        assertMonotonic(samples, decreasing = false)
        assertMotionOccurred(samples)
        assertEquals(CLOSED_POSITION, samples.last(), EPS)
    }

    // --- Direction flip mid-slide: reverses toward the new target ------------

    @Test
    fun liveTransition_openingThenClosing_reversesTowardClosed() {
        val state = mutableStateOf(DoorPosition.CLOSED)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDoorAnimationMemory provides DoorAnimationMemory()) {
                GarageIcon(
                    doorPosition = state.value,
                    modifier = Modifier.size(ICON_DP.dp),
                    duration = testDuration,
                    lastChangeTimeSeconds = EVENT_T,
                )
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        // Start opening, advance to roughly mid-slide.
        composeTestRule.runOnUiThread { state.value = DoorPosition.OPENING }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(stepMs * 2) // ~halfway
        val midway = composeTestRule.doorOffset()
        assertTrue(
            "expected mid-slide between OPEN and CLOSED, was $midway",
            midway < CLOSED_POSITION - EPS && midway > OPEN_POSITION + EPS,
        )

        // Flip to closing — the tween reverses toward CLOSED.
        composeTestRule.runOnUiThread { state.value = DoorPosition.CLOSING }
        composeTestRule.mainClock.advanceTimeByFrame()
        val samples = composeTestRule.sampleOffsets()
        assertMonotonic(samples, decreasing = false) // heads back toward CLOSED(0.0)
        assertEquals(CLOSED_POSITION, samples.last(), EPS)
    }

    // --- helpers --------------------------------------------------------------

    private fun ComposeContentTestRule.doorOffset(): Float =
        onNode(SemanticsMatcher.keyIsDefined(DoorOffsetSemanticsKey))
            .fetchSemanticsNode()
            .config[DoorOffsetSemanticsKey]

    /** Advance the clock in [steps] of [stepMs], collecting the offset each tick. */
    private fun ComposeContentTestRule.sampleOffsets(): List<Float> {
        val out = mutableListOf(doorOffset())
        repeat(steps) {
            mainClock.advanceTimeBy(stepMs)
            out += doorOffset()
        }
        return out
    }

    private fun assertNoMotion(
        samples: List<Float>,
        expected: Float,
    ) {
        samples.forEachIndexed { i, v ->
            assertEquals("sample[$i] drifted from pinned target: $samples", expected, v, EPS)
        }
    }

    private fun assertMonotonic(
        samples: List<Float>,
        decreasing: Boolean,
    ) {
        for (i in 1 until samples.size) {
            val ok =
                if (decreasing) {
                    samples[i] <= samples[i - 1] + EPS
                } else {
                    samples[i] >= samples[i - 1] - EPS
                }
            assertTrue(
                "non-monotonic (${if (decreasing) "decreasing" else "increasing"}) " +
                    "at index $i: $samples",
                ok,
            )
        }
    }

    private fun assertMotionOccurred(samples: List<Float>) {
        val travel = kotlin.math.abs(samples.first() - samples.last())
        assertTrue("expected visible travel, samples barely moved: $samples", travel > 0.1f)
    }

    private companion object {
        const val EPS = 0.01f

        // Looser tolerance for the first-frame read of a slide: one frame of
        // the compressed tween has already advanced the offset slightly off
        // the exact "from" end.
        const val START_EPS = 0.08f
        const val ICON_DP = 200

        // Arbitrary stable event timestamp; identity matters, not the value.
        const val EVENT_T = 1_000L
    }
}
