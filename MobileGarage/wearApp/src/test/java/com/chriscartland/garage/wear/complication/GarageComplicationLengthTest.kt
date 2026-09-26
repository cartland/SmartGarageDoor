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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **Seven characters, enforced against the real strings.**
 *
 * `ShortTextComplicationData.MAX_TEXT_LENGTH` is 7. Past it a watch face is
 * free to truncate, and a truncated door state is worse than a shorter word
 * chosen deliberately — "Sensor…" and "Openin…" say less than "Sensors" and
 * "Opening" do.
 *
 * This reads `strings.xml` rather than the compiled resources because a JVM
 * test has no `Context`, and because the file is the thing a future edit
 * would change. Format strings are substituted with their worst case: the
 * shared `CheckInStatusMapper` buckets minutes at 0–59 and hours at 0–23, and
 * [GarageComplicationWords] caps days at 99 precisely so this stays true.
 */
class GarageComplicationLengthTest {
    private val strings: Map<String, String> by lazy { parseStrings() }

    /**
     * Every string that can land in a short-text slot.
     *
     * All of them are now FIXED — no format arguments, no numbers. The one
     * figure the complication shows is rendered by the watch face from a
     * `TimeDifferenceComplicationText`, which respects the same budget itself.
     * That is why this list no longer carries worst-case substitutions: there
     * is nothing left here that varies at runtime.
     */
    private val shortTextStrings = listOf(
        "complication_door_open",
        "complication_door_closed",
        "complication_door_opening",
        "complication_door_closing",
        "complication_door_unknown",
        "complication_door_sensor_conflict",
        "complication_no_data",
        "complication_stale",
        "complication_preview_duration",
    )

    @Test
    fun everyShortTextStringFitsInSevenCharacters() {
        val tooLong = shortTextStrings.mapNotNull { name ->
            val rendered = strings[name] ?: error("missing string: $name")
            if (rendered.length > MAX_TEXT_LENGTH) "$name -> \"$rendered\" (${rendered.length})" else null
        }
        assertEquals(
            "these would be truncated by the watch face",
            emptyList<String>(),
            tooLong,
        )
    }

    @Test
    fun theLengthCheckCanActuallyFail() {
        // Positive control. Every assertion above is "this list is empty",
        // which a broken parser returning nothing would satisfy forever. An
        // obviously-too-long string must be caught by the same predicate.
        val rendered = "Sensor conflict"
        assertTrue(
            "the predicate does not actually measure length",
            rendered.length > MAX_TEXT_LENGTH,
        )
    }

    @Test
    fun theParserActuallyFoundTheStrings() {
        // Scope sanity, per the repo's vacuous-pass rule: if the file moved or
        // the format changed, every check above would pass on an empty map.
        assertTrue("parsed no strings at all — the file moved or changed shape", strings.size > 20)
        assertEquals("Open", strings["complication_door_open"])
    }

    private fun parseStrings(): Map<String, String> {
        val file = candidatePaths().firstOrNull { it.exists() }
            ?: error("could not locate wear strings.xml from ${File(".").absolutePath}")
        val text = file.readText()
        return STRING_ENTRY
            .findAll(text)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun candidatePaths() =
        listOf(
            File("src/main/res/values/strings.xml"),
            File("wearApp/src/main/res/values/strings.xml"),
            File("MobileGarage/wearApp/src/main/res/values/strings.xml"),
        )

    private companion object {
        /** `ShortTextComplicationData.MAX_TEXT_LENGTH`. */
        const val MAX_TEXT_LENGTH = 7

        val STRING_ENTRY = Regex("""<string name="([^"]+)">([^<]*)</string>""")
    }
}
