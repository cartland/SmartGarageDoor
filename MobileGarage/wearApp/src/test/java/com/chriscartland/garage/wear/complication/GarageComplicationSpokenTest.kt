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

package com.chriscartland.garage.wear.complication

import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.presentation.GlanceStatusMapper
import com.chriscartland.garage.wear.complication.GarageComplicationWords.Spoken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What a screen reader hears for the complication carries the qualifier the
 * visual design refuses to leave off — never the bare door word.
 */
class GarageComplicationSpokenTest {
    private fun glance(
        doorPosition: DoorPosition? = DoorPosition.OPEN,
        lastCheckInEpochSeconds: Long? = NOW - 120,
        lastChangeEpochSeconds: Long? = NOW - 480,
        isFetchError: Boolean = false,
    ) = GlanceStatusMapper.forGlance(
        doorPosition = doorPosition,
        lastCheckInEpochSeconds = lastCheckInEpochSeconds,
        lastChangeEpochSeconds = lastChangeEpochSeconds,
        nowEpochSeconds = NOW,
        isFetchError = isFetchError,
    )

    @Test
    fun aLiveDoorIsSpokenWithItsRunningDuration() {
        assertEquals(Spoken.Live("Open", NOW - 480), GarageComplicationWords.spoken(glance(), "Open"))
    }

    @Test
    fun aDoorWeCannotVouchForIsSpokenAsNotConfirmedInsteadOfADuration() {
        // The reading is dated, and still gets no duration: a duration asserts
        // the door has been that way continuously.
        val stale = glance(lastCheckInEpochSeconds = NOW - 3_600)
        assertEquals(Spoken.NotConfirmed("Open"), GarageComplicationWords.spoken(stale, "Open"))
        // Positive control: the same door, confirmed, is spoken differently.
        assertNotEquals(GarageComplicationWords.spoken(stale, "Open"), GarageComplicationWords.spoken(glance(), "Open"))
    }

    @Test
    fun noDoorIsSpokenAsNoSignal() {
        assertEquals(Spoken.NoSignal, GarageComplicationWords.spoken(glance(doorPosition = null, lastChangeEpochSeconds = null), null))
    }

    @Test
    fun anUndatedLiveDoorIsSpokenPlainly() {
        assertEquals(Spoken.Undated("Open"), GarageComplicationWords.spoken(glance(lastChangeEpochSeconds = null), "Open"))
    }

    @Test
    fun theServiceDescribesWithTheSpokenFormNotTheBareDoorWord() {
        val source = File("src/main/java/com/chriscartland/garage/wear/complication/GarageDoorComplicationService.kt")
        assertTrue("Missing " + source.absolutePath, source.exists())
        val text = source.readText()
        assertTrue("the service must build its contentDescription from spokenText(", text.contains("spokenText("))
        assertTrue("the bare door word is back as the description", !text.contains(BARE_WORD))
        // Positive control for the assertion above.
        assertTrue(("        " + BARE_WORD + "\n").contains(BARE_WORD))
    }

    private companion object {
        const val NOW = 1_700_000_000L
        const val BARE_WORD = "val description = door ?:"
    }
}
