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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind the listening animation.
 *
 * The animation itself is not assertable from the command line, but this is —
 * and it is the part that can actually be wrong: an unclamped level would
 * push rings past the bezel, and unsmoothed RMS reads as a flicker rather
 * than a voice.
 */
class VoiceLevelTest {
    @Test
    fun silenceAndPeakMapToTheEndsOfTheRange() {
        assertEquals(0f, VoiceLevel.normalize(-2f), TOLERANCE)
        assertEquals(1f, VoiceLevel.normalize(10f), TOLERANCE)
        assertEquals(0.5f, VoiceLevel.normalize(4f), TOLERANCE)
    }

    /**
     * `onRmsChanged` is documented loosely and devices exceed the nominal
     * range. Clamping (rather than rescaling) keeps one loud spike from
     * redefining what "loud" means for the rest of the utterance — and, more
     * practically, keeps the rings inside the screen.
     */
    @Test
    fun outOfRangeInputIsClampedNotRescaled() {
        assertEquals(0f, VoiceLevel.normalize(-50f), TOLERANCE)
        assertEquals(1f, VoiceLevel.normalize(120f), TOLERANCE)
        assertTrue(VoiceLevel.normalize(Float.MAX_VALUE) <= 1f)
        assertTrue(VoiceLevel.normalize(-Float.MAX_VALUE) >= 0f)
    }

    @Test
    fun smoothingMovesTowardTheTargetWithoutJumpingToIt() {
        val stepped = VoiceLevel.smooth(previous = 0f, next = 1f)
        assertTrue("expected partial movement, got $stepped", stepped > 0f && stepped < 1f)
    }

    @Test
    fun repeatedSmoothingConvergesOnTheTarget() {
        var level = 0f
        repeat(30) { level = VoiceLevel.smooth(level, 1f) }
        assertEquals(1f, level, 0.01f)
    }

    /** A drop to silence has to actually settle, or the rings freeze loud. */
    @Test
    fun smoothingConvergesBackToSilence() {
        var level = 1f
        repeat(30) { level = VoiceLevel.smooth(level, 0f) }
        assertEquals(0f, level, 0.01f)
    }

    @Test
    fun quietRoomDoesNotCountAsSpeaking() {
        assertFalse(VoiceLevel.isSpeaking(VoiceLevel.normalize(-2f)))
        assertTrue(VoiceLevel.isSpeaking(VoiceLevel.normalize(10f)))
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
