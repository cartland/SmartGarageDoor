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

package com.chriscartland.garage.widget

import com.chriscartland.garage.presentation.DoorHeadline
import com.chriscartland.garage.presentation.StatusHeadline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The widget's words: every headline has one, no two share one, and they are the
 * app's own strings rather than a second vocabulary.
 */
class GarageWidgetWordsTest {
    private val allHeadlines: List<StatusHeadline> =
        DoorHeadline.entries.map { StatusHeadline.Door(it) } +
            listOf(StatusHeadline.Connecting, StatusHeadline.NoSignal)

    @Test
    fun everyHeadlineHasItsOwnWord() {
        // A mapping that sent two different door states to one string would show
        // "Open" for a closing door. `when` exhaustiveness guarantees each case
        // is HANDLED; only this guarantees each is handled DIFFERENTLY.
        val ids = allHeadlines.map { GarageWidgetWords.headline(it) }
        assertEquals(
            "two headlines resolve to the same string resource: $ids",
            ids.size,
            ids.toSet().size,
        )
    }

    @Test
    fun noHeadlineResolvesToAMissingResource() {
        val zero = allHeadlines.filter { GarageWidgetWords.headline(it) == 0 }
        assertTrue("headlines with no string resource: $zero", zero.isEmpty())
    }

    @Test
    fun theDoorWordsAreTheAppsOwnStringsAndNotACopy() {
        // The point of this file is that the widget and the door screen cannot
        // describe one door two ways. That only holds while the widget keeps
        // POINTING AT the home_ strings — the moment someone adds
        // widget_door_state_open, the two can drift and nothing else would say
        // so. This reads the source rather than the resources because the
        // resource id alone cannot tell you which name produced it.
        val source = File("src/main/java/com/chriscartland/garage/widget/GarageWidgetWords.kt")
        assertTrue("missing ${source.absolutePath}", source.exists())
        val referenced = Regex("""R\.string\.(\w+)""")
            .findAll(source.readText())
            .map { it.groupValues[1] }
            .toList()
        assertTrue(
            "GarageWidgetWords referenced no strings at all — this check went blind",
            referenced.isNotEmpty(),
        )
        val ownWords = referenced.filterNot { it.startsWith("home_door_state_") }
        assertTrue(
            "The widget must reuse the Home screen's door words. These look like a " +
                "second vocabulary: $ownWords",
            ownWords.isEmpty(),
        )
    }

    @Test
    fun theSourceCheckCanActuallyFail() {
        // Positive control for the regex above: run it against a file that does
        // reference a non-home_ string and confirm it comes back non-empty. A
        // pattern that matched nothing would make the previous test vacuous.
        val widget = File("src/main/java/com/chriscartland/garage/widget/GarageDoorWidget.kt")
        val referenced = Regex("""R\.string\.(\w+)""")
            .findAll(widget.readText())
            .map { it.groupValues[1] }
            .toList()
        assertTrue(
            "the string-reference regex found nothing in GarageDoorWidget.kt, which " +
                "does reference widget_ strings — the pattern is not measuring anything",
            referenced.any { it.startsWith("widget_") },
        )
    }
}
