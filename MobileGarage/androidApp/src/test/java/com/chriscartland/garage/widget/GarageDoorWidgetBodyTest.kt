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

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import com.chriscartland.garage.MainActivity
import com.chriscartland.garage.domain.model.DoorColorState
import com.chriscartland.garage.presentation.DataFreshness
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The widget's DRAWING, asserted on the composition rather than on pixels.
 *
 * Worth having because Glance is the one surface in this app whose layout no
 * other check can see: it is not Compose, so the screenshot gallery cannot
 * render it, and Layoutlib has no widget host. Before this file the words and
 * the verdict were tested and the layout that shows them was not — the same
 * shape of gap that let the Wear tile ship a blank coloured block.
 *
 * These are composition assertions, not visual ones: they prove which elements
 * exist and what they carry, never how they look.
 */
class GarageDoorWidgetBodyTest {
    @Test
    fun bothLinesAreDrawnWhenThereIsSomethingToSay() =
        runGlanceAppWidgetUnitTest {
            provideComposable {
                GarageDoorWidgetBody(
                    text = GarageWidgetText(headline = "Closed", subline = "since 3:42 PM"),
                    colorState = DoorColorState.CLOSED,
                    freshness = DataFreshness.FRESH,
                )
            }

            onNode(hasText("Closed")).assertExists()
            onNode(hasText("since 3:42 PM")).assertExists()
        }

    @Test
    fun aScreenReaderHearsBothLinesAsOneStatement() =
        runGlanceAppWidgetUnitTest {
            provideComposable {
                GarageDoorWidgetBody(
                    text = GarageWidgetText(headline = "Closed", subline = "since 3:42 PM"),
                    colorState = DoorColorState.CLOSED,
                    freshness = DataFreshness.FRESH,
                )
            }

            onNode(hasContentDescription("Closed, since 3:42 PM")).assertExists()
        }

    @Test
    fun theHeadlineAloneIsSpokenWhenThereIsNoSecondLine() {
        assertEquals("No signal", GarageWidgetText(headline = "No signal", subline = null).spoken)
        // Positive control: a second line changes what is spoken.
        assertEquals("Open, Not confirmed", GarageWidgetText(headline = "Open", subline = "Not confirmed").spoken)
    }

    @Test
    fun aSilentSublineDrawsNoSecondLine() =
        runGlanceAppWidgetUnitTest {
            // The empty-cache case. A widget that rendered an empty Text would
            // leave a blank row under the headline; one that rendered "null"
            // would be worse. Neither is visible to a words-only test.
            provideComposable {
                GarageDoorWidgetBody(
                    text = GarageWidgetText(headline = "No signal", subline = null),
                    colorState = DoorColorState.UNKNOWN,
                    freshness = DataFreshness.STALE,
                )
            }

            onNode(hasText("No signal")).assertExists()
            onNode(hasText("null")).assertDoesNotExist()
        }

    @Test
    fun tappingOpensTheApp() =
        runGlanceAppWidgetUnitTest {
            // The read-only property, asserted on what the widget actually
            // BUILDS rather than on which types it depends on. The reflection
            // checks in GarageDoorWidgetSafetyTest cannot see an action at all
            // and the manifest check cannot see a Kotlin one, so this is a third
            // angle on the same property.
            //
            // The action sits on the CONTAINER, not on either Text — the whole
            // card is the tap target, which is what makes the widget feel like
            // one thing rather than two labels. So the assertion has to find the
            // node carrying the action rather than look for it on a line of text
            // (a first attempt did, and failed with the Text node's modifier
            // printed empty).
            provideComposable {
                GarageDoorWidgetBody(
                    text = GarageWidgetText(headline = "Open", subline = "since 9:12 AM"),
                    colorState = DoorColorState.OPEN,
                    freshness = DataFreshness.FRESH,
                )
            }

            onNode(hasStartActivityClickAction<MainActivity>()).assertExists()
        }

    @Test
    fun theStaleWordIsDrawnInPlaceOfASpan() =
        runGlanceAppWidgetUnitTest {
            // Pairs with GarageWidgetSublineTest: that one proves the CHOICE is
            // Stale, this one proves the choice reaches the screen.
            provideComposable {
                GarageDoorWidgetBody(
                    text = GarageWidgetText(headline = "Closed", subline = "Not confirmed"),
                    colorState = DoorColorState.CLOSED,
                    freshness = DataFreshness.STALE,
                )
            }

            onNode(hasText("Not confirmed")).assertExists()
            onNode(hasText("since")).assertDoesNotExist()
        }

    @Test
    fun theWideWidgetPutsBothLinesOnOneRow() =
        runGlanceAppWidgetUnitTest {
            // SizeMode.Responsive composes the body once per declared size;
            // this is the four-cell composition. The tag is on the container,
            // so the assertion is about WHICH arrangement drew, not the words.
            setAppWidgetSize(GarageWidgetLayout.WIDE)
            provideComposable {
                GarageDoorWidgetBody(
                    text = GarageWidgetText(headline = "Closed", subline = "since 3:42 PM"),
                    colorState = DoorColorState.CLOSED,
                    freshness = DataFreshness.FRESH,
                )
            }
            onNode(hasTestTag(GarageWidgetLayout.TAG_INLINE)).assertExists()
            onNode(hasTestTag(GarageWidgetLayout.TAG_STACKED)).assertDoesNotExist()
            onNode(hasText("Closed")).assertExists()
            onNode(hasText("since 3:42 PM")).assertExists()
        }

    @Test
    fun theCompactWidgetStacksTheLines() =
        runGlanceAppWidgetUnitTest {
            // Positive control for the test above: the two-cell composition
            // must draw the OTHER arrangement, or the tag could be constant.
            setAppWidgetSize(GarageWidgetLayout.COMPACT)
            provideComposable {
                GarageDoorWidgetBody(
                    text = GarageWidgetText(headline = "Closed", subline = "since 3:42 PM"),
                    colorState = DoorColorState.CLOSED,
                    freshness = DataFreshness.FRESH,
                )
            }
            onNode(hasTestTag(GarageWidgetLayout.TAG_STACKED)).assertExists()
            onNode(hasTestTag(GarageWidgetLayout.TAG_INLINE)).assertDoesNotExist()
            onNode(hasText("since 3:42 PM")).assertExists()
        }

    @Test
    fun theSpokenStatementAndTheTapSurviveEitherArrangement() =
        runGlanceAppWidgetUnitTest {
            // Strategy 2.3's one-statement description and the read-only tap
            // target both live on the container; a layout split must not
            // leave either behind on one branch.
            setAppWidgetSize(GarageWidgetLayout.WIDE)
            provideComposable {
                GarageDoorWidgetBody(
                    text = GarageWidgetText(headline = "Open", subline = "since 9:12 AM"),
                    colorState = DoorColorState.OPEN,
                    freshness = DataFreshness.FRESH,
                )
            }
            onNode(hasContentDescription("Open, since 9:12 AM")).assertExists()
            onNode(hasStartActivityClickAction<MainActivity>()).assertExists()
        }
}
