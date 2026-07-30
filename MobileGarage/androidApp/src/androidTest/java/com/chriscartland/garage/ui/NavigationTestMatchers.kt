/*
 * Copyright 2024 Chris Cartland. All rights reserved.
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

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag

/**
 * Matchers for navigation chrome, where a label can legitimately appear twice.
 *
 * The top app bar titles each screen ("History", "Settings"), and the nav rail /
 * bottom bar labels its tabs with the same words. On the History tab, "History"
 * therefore matches two nodes, and a bare `onNodeWithText` either fails
 * outright or silently picks the wrong one. These helpers say which is meant.
 */
object NavigationTestMatchers {
    /** Asserts the top app bar's title reads exactly [title]. */
    fun ComposeTestRule.assertTopBarTitle(title: String) {
        onNodeWithTag(TOP_BAR_TITLE_TEST_TAG)
            .assertIsDisplayed()
            .assertTextEquals(title)
    }

    /**
     * The selectable nav item labelled [label] — a tab, never the bar title.
     * Tabs carry the Selected semantics property (which is what `assertIsSelected`
     * reads); a title is plain text.
     */
    fun ComposeTestRule.onSelectableNodeWithText(label: String): SemanticsNodeInteraction = onNode(hasText(label) and isSelectable())
}
