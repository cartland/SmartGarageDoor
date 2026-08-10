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

import com.chriscartland.garage.domain.model.VoiceIntent
import com.chriscartland.garage.usecase.VoiceCommandIgnoreReason
import com.chriscartland.garage.usecase.VoiceCommandState
import com.chriscartland.garage.wear.ui.WearVoiceViewModel.Companion.ARMED_WINDOW_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The simulation's wording is a safety signal, so it is pinned like one.
 *
 * These assert on resource IDs rather than on rendered text — a JVM unit test
 * has no resources — which is enough for the property that actually matters:
 * **a state that describes an action must not resolve to the same string on
 * both surfaces.** The failure this prevents is someone adding a state, or a
 * refusal reason, and wiring only the live wording, so the rehearsal quietly
 * begins announcing "Opening the door" while opening nothing. Sharing a string
 * is invisible in review and obvious here.
 *
 * The converse is asserted too. Strings that SHOULD be shared drifting into two
 * copies is a smaller problem, but it is the thing that makes the rehearsal
 * stop being a rehearsal — every gratuitous difference is one more way the
 * practice run fails to teach the real run.
 */
class VoiceStringsTest {
    private val ignoredReasonsAboutTheDoor = listOf(
        VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN,
        VoiceCommandIgnoreReason.DOOR_ALREADY_CLOSED,
        VoiceCommandIgnoreReason.DOOR_MOVING,
        VoiceCommandIgnoreReason.DOOR_STUCK,
        VoiceCommandIgnoreReason.DOOR_STATE_UNKNOWN,
        VoiceCommandIgnoreReason.DOOR_STATE_CHANGED,
    )

    private val ignoredReasonsAboutTheUtterance = listOf(
        VoiceCommandIgnoreReason.NO_SPEECH,
        VoiceCommandIgnoreReason.RECOGNIZER_UNAVAILABLE,
        VoiceCommandIgnoreReason.NOT_A_COMMAND,
        VoiceCommandIgnoreReason.NOT_CONFIDENT,
    )

    /**
     * The two lists above are maintained by hand, which means a new refusal
     * added to the enum lands in neither and escapes both tests silently —
     * they iterate their list, so an absent reason is simply never asserted.
     * This makes that omission fail loudly instead, and it is the reason the
     * lists can stay explicit (which is what keeps them readable as a
     * statement of which refusals talk about a door).
     */
    @Test
    fun everyRefusalIsClassifiedAsAboutTheDoorOrAboutTheUtterance() {
        assertEquals(
            "A refusal reason is in neither list, so no test words it. Add it to " +
                "ignoredReasonsAboutTheDoor if it talks about a door, otherwise to " +
                "ignoredReasonsAboutTheUtterance.",
            VoiceCommandIgnoreReason.entries.toSet(),
            (ignoredReasonsAboutTheDoor + ignoredReasonsAboutTheUtterance).toSet(),
        )
    }

    /**
     * Every state that names an action or an outcome.
     *
     * Listed explicitly rather than derived, so adding a state to
     * `VoiceCommandState` does not silently escape this test — the `when` in
     * [VoiceStrings.primaryLine] forces you to word it, and this forces you to
     * word it twice.
     */
    private val statesThatDescribeAnAction = listOf(
        VoiceCommandState.Armed(
            intent = VoiceIntent.OPEN,
            transcript = "open the garage door",
            windowMs = ARMED_WINDOW_MILLIS,
        ),
        VoiceCommandState.Armed(
            intent = VoiceIntent.CLOSE,
            transcript = "close the garage door",
            windowMs = ARMED_WINDOW_MILLIS,
        ),
        VoiceCommandState.Sending(intent = VoiceIntent.OPEN),
        VoiceCommandState.Sent(intent = VoiceIntent.OPEN),
        VoiceCommandState.Failed(intent = VoiceIntent.OPEN),
    )

    @Test
    fun everyStateThatDescribesAnActionIsWordedPerSurface() {
        statesThatDescribeAnAction.forEach { state ->
            assertNotEquals(
                "$state must not share one string across both surfaces: the live " +
                    "surface states the action as fact, the simulation must keep it " +
                    "conditional.",
                VoiceStrings.primaryLine(state, VoiceSurfaceMode.Live),
                VoiceStrings.primaryLine(state, VoiceSurfaceMode.Simulated),
            )
        }
    }

    @Test
    fun refusalsAboutTheDoorNameWhichDoorTheyMean() {
        ignoredReasonsAboutTheDoor.forEach { reason ->
            assertNotEquals(
                "$reason talks about a door, so it must say WHICH door — the " +
                    "simulation's refusals name the demo door.",
                VoiceStrings.ignoredLine(reason, VoiceSurfaceMode.Live),
                VoiceStrings.ignoredLine(reason, VoiceSurfaceMode.Simulated),
            )
        }
    }

    @Test
    fun theDoorContextLineNamesWhichDoorItIsShowing() {
        assertNotEquals(
            VoiceStrings.doorLine(VoiceSurfaceMode.Live),
            VoiceStrings.doorLine(VoiceSurfaceMode.Simulated),
        )
    }

    /**
     * Refusals about what was HEARD are identical on both surfaces, because
     * they are identical facts: the classifier did not understand you, and no
     * door was involved in that conclusion.
     */
    @Test
    fun refusalsAboutTheUtteranceAreShared() {
        ignoredReasonsAboutTheUtterance.forEach { reason ->
            assertEquals(
                "$reason is about the speech, not the door, so both surfaces " +
                    "should say the same thing.",
                VoiceStrings.ignoredLine(reason, VoiceSurfaceMode.Live),
                VoiceStrings.ignoredLine(reason, VoiceSurfaceMode.Simulated),
            )
        }
    }

    /** Nothing has happened yet in either world, so nothing differs yet. */
    @Test
    fun statesBeforeAnyActionAreShared() {
        listOf(VoiceCommandState.Ready, VoiceCommandState.Listening(attempt = 1)).forEach { state ->
            assertEquals(
                "$state describes no action, so both surfaces should word it the same.",
                VoiceStrings.primaryLine(state, VoiceSurfaceMode.Live),
                VoiceStrings.primaryLine(state, VoiceSurfaceMode.Simulated),
            )
        }
    }

    /**
     * Only the live surface draws the in-flight ring, which claims "the server
     * has not answered yet" — a claim the simulation cannot make.
     */
    @Test
    fun onlyTheLiveSurfaceClaimsARoundTrip() {
        assertEquals(true, VoiceSurfaceMode.Live.hasRealRoundTrip)
        assertEquals(false, VoiceSurfaceMode.Simulated.hasRealRoundTrip)
    }

    /** The rings are different colours, which is the signal that needs no reading. */
    @Test
    fun theTwoSurfacesDrawDifferentlyColouredRings() {
        assertNotEquals(
            VoiceSurfaceMode.Live.ringColors.sweep,
            VoiceSurfaceMode.Simulated.ringColors.sweep,
        )
        assertNotEquals(
            VoiceSurfaceMode.Live.ringColors.committed,
            VoiceSurfaceMode.Simulated.ringColors.committed,
        )
    }
}
