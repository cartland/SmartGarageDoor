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

import com.chriscartland.garage.R
import com.chriscartland.garage.domain.model.VoiceIntent
import com.chriscartland.garage.usecase.VoiceCommandIgnoreReason
import com.chriscartland.garage.usecase.VoiceCommandState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The phone's two voice surfaces word their states per surface, the way the
 * watch's do (`VoiceStringsTest` there): the live card states the action as
 * fact, the simulated sheet keeps it conditional and says outright that
 * nothing was sent.
 */
class VoiceWordsTest {
    private val statesThatDescribeAnAction =
        listOf(
            VoiceCommandState.Armed(intent = VoiceIntent.OPEN, transcript = "open the garage door", windowMs = 3_000L),
            VoiceCommandState.Armed(intent = VoiceIntent.CLOSE, transcript = "close the garage door", windowMs = 3_000L),
            VoiceCommandState.Sending(intent = VoiceIntent.OPEN),
            VoiceCommandState.Sent(intent = VoiceIntent.OPEN),
            VoiceCommandState.Failed(intent = VoiceIntent.OPEN),
        )

    @Test
    fun everyStateThatDescribesAnActionIsWordedPerSurface() {
        statesThatDescribeAnAction.forEach { state ->
            assertNotEquals(
                "$state must not share one string across both surfaces",
                VoiceWords.primaryLine(state, VoiceSurfaceMode.Live),
                VoiceWords.primaryLine(state, VoiceSurfaceMode.Simulated),
            )
        }
    }

    @Test
    fun statesBeforeAnyActionAreShared() {
        // Positive control: a table that differed for every state would pass
        // the test above. Before anything is decided there is nothing to
        // condition, so the words are the same.
        listOf(
            VoiceCommandState.Ready,
            VoiceCommandState.Listening(attempt = 1),
            VoiceCommandState.Ignored(
                reason = VoiceCommandIgnoreReason.NO_SPEECH,
                transcript = null,
                classification = null,
                engineName = "test",
            ),
        ).forEach { state ->
            assertEquals(
                "$state has nothing to condition",
                VoiceWords.primaryLine(state, VoiceSurfaceMode.Live),
                VoiceWords.primaryLine(state, VoiceSurfaceMode.Simulated),
            )
        }
    }

    @Test
    fun theSimulationSaysOutrightThatNothingWasSent() {
        assertEquals(R.string.voice_sim_sent, VoiceWords.primaryLine(VoiceCommandState.Sent(VoiceIntent.OPEN), VoiceSurfaceMode.Simulated))
        assertEquals(
            R.string.voice_sim_sending_subtitle,
            VoiceWords.secondaryLine(VoiceCommandState.Sending(VoiceIntent.OPEN), VoiceSurfaceMode.Simulated),
        )
        // …and the live card still claims the round trip it makes.
        assertEquals(R.string.voice_control_sent, VoiceWords.primaryLine(VoiceCommandState.Sent(VoiceIntent.OPEN), VoiceSurfaceMode.Live))
    }

    @Test
    fun everyRefusalHasItsOwnLine() {
        val lines = VoiceCommandIgnoreReason.entries.map { VoiceWords.ignoredLine(it) }
        assertEquals("two refusals share a line", lines.size, lines.toSet().size)
    }

    @Test
    fun theTapCancelsOnlyWhileACommandIsArmed() {
        assertEquals(true, VoiceCommandUi.cancelsOnTap(statesThatDescribeAnAction.first()))
        // Positive control: every other state's tap is the mic.
        listOf(
            VoiceCommandState.Ready,
            VoiceCommandState.Listening(attempt = 1),
            VoiceCommandState.Sending(intent = VoiceIntent.OPEN),
            VoiceCommandState.Sent(intent = VoiceIntent.OPEN),
            VoiceCommandState.Failed(intent = VoiceIntent.OPEN),
        ).forEach { state ->
            assertEquals("$state", false, VoiceCommandUi.cancelsOnTap(state))
        }
    }
}
