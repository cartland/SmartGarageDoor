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
import androidx.glance.testing.unit.hasText
import androidx.glance.text.Text
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.presentation.GlanceStatus
import com.chriscartland.garage.presentation.GlanceStatusMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A widget session keeps drawing after its first frame.
 *
 * `WidgetGlanceStatusTest` proves the reader's flow carries every change. This
 * proves the COMPOSITION follows that flow — the half that was missing when a
 * door went Opening and then Open inside one session and the home screen kept
 * the first. Glance does not restart a session that is still alive, so nothing
 * but the composition observing can put the second reading on screen.
 *
 * The drawing is a plain label here because the real one needs a Context to
 * find its words; what is under test is which reading gets drawn, not how.
 */
class GarageDoorWidgetSessionTest {
    private val now = 1_000_000L

    private fun reading(position: DoorPosition): GlanceStatus =
        GlanceStatusMapper.forGlance(
            doorPosition = position,
            lastCheckInEpochSeconds = now - 30,
            lastChangeEpochSeconds = now - 600,
            nowEpochSeconds = now,
            isFetchError = false,
        )

    private fun label(status: GlanceStatus): String = status.headline.toString()

    @Test
    fun aReadingThatArrivesAfterTheFirstFrameIsDrawn() =
        runGlanceAppWidgetUnitTest {
            val closed = reading(DoorPosition.CLOSED)
            val open = reading(DoorPosition.OPEN)
            val statuses = MutableStateFlow(closed)
            // What the session handed its drawing, in order. Asserted on
            // directly: the test environment only re-copies its node tree when
            // the recomposer's state FLOW emits Idle again, and a recomposition
            // that finishes within one scheduler pass never shows it anything
            // but Idle — so a node lookup after the change can read the first
            // frame however correct the composition is.
            val drawn = mutableListOf<GlanceStatus>()
            provideComposable {
                GarageDoorWidgetSession(firstFrame = closed, statuses = statuses) { shown ->
                    drawn += shown
                    Text(label(shown))
                }
            }
            onNode(hasText(label(closed))).assertExists()
            assertEquals("only the first frame has been drawn so far", setOf(closed), drawn.toSet())

            statuses.value = open
            awaitIdle()

            // The last thing drawn, not the whole list: a composition may draw
            // an unchanged frame more than once, and that is not the property.
            assertEquals(open, drawn.last())
        }

    @Test
    fun theFirstFrameIsDrawnBeforeTheFlowSaysAnything() =
        runGlanceAppWidgetUnitTest {
            // The door read off disk before composing is what the first frame
            // shows. A session that waited for its flow would open on nothing.
            val closed = reading(DoorPosition.CLOSED)
            provideComposable {
                GarageDoorWidgetSession(firstFrame = closed, statuses = emptyFlow()) { shown ->
                    Text(label(shown))
                }
            }

            onNode(hasText(label(closed))).assertExists()
        }
}
