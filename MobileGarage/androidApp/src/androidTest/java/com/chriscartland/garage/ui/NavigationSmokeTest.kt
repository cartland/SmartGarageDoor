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

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.chriscartland.garage.MainActivity
import org.junit.Rule
import org.junit.Test

/**
 * Navigation smoke tests that verify all screens are reachable without crashing.
 *
 * These tests launch the real MainActivity and navigate using the bottom bar.
 * They don't verify data content (network is unavailable in tests) — they
 * only verify the screen renders without exceptions.
 */
class NavigationSmokeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreenDisplays() {
        // Home is the start destination — should show the top bar
        composeTestRule.onNodeWithText("Garage").assertIsDisplayed()
    }

    @Test
    fun bottomBarDisplaysAllTabs() {
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("History").assertIsDisplayed()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun navigateToHistory() {
        composeTestRule.onNodeWithText("History").performClick()
        composeTestRule.waitForIdle()
        // If we get here without crashing, the History screen rendered
        composeTestRule.onNodeWithText("Garage").assertIsDisplayed()
    }

    @Test
    fun navigateToProfile() {
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.waitForIdle()
        // If we get here without crashing, the Profile screen rendered
        composeTestRule.onNodeWithText("Garage").assertIsDisplayed()
    }

    @Test
    fun navigateBackToHome() {
        // Go to History, then back to Home
        composeTestRule.onNodeWithText("History").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Garage").assertIsDisplayed()
    }

    @Test
    fun navigateAllScreensSequentially() {
        // Home → History → Profile → Home
        composeTestRule.onNodeWithText("History").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Home").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Garage").assertIsDisplayed()
    }
}
