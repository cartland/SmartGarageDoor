package com.chriscartland.garage.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import com.chriscartland.garage.MainActivity
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented tests for tab back stack behavior.
 *
 * Verifies that:
 * - Non-Home tabs stack on Home (Back reveals Home)
 * - Non-Home tabs replace each other (Back goes to Home, not previous tab)
 * - Home tab is always the root
 */
class BackStackTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun backFromHistoryReturnsToHome() {
        // Navigate to History
        composeTestRule.onNodeWithText("History").performClick()
        composeTestRule.waitForIdle()

        // Press system Back
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        // Should be on Home, not exited
        composeTestRule.onNodeWithText("Home").assertIsSelected()
    }

    @Test
    fun backFromSettingsReturnsToHome() {
        // Navigate to Settings
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.waitForIdle()

        // Press system Back
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        // Should be on Home
        composeTestRule.onNodeWithText("Home").assertIsSelected()
    }

    @Test
    fun settingsReplacesHistoryOnBackStack() {
        // Navigate: Home → History → Settings
        composeTestRule.onNodeWithText("History").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.waitForIdle()

        // Press Back — should go to Home, NOT History
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Home").assertIsSelected()
    }

    @Test
    fun tappingCurrentTabIsNoOp() {
        // Navigate to History
        composeTestRule.onNodeWithText("History").performClick()
        composeTestRule.waitForIdle()

        // Tap History again
        composeTestRule.onNodeWithText("History").performClick()
        composeTestRule.waitForIdle()

        // Still on History, Back still goes to Home
        Espresso.pressBack()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Home").assertIsSelected()
    }

    @Test
    fun tappingHomePopsToHome() {
        // Navigate to Settings
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.waitForIdle()

        // Tap Home
        composeTestRule.onNodeWithText("Home").performClick()
        composeTestRule.waitForIdle()

        // Should be on Home
        composeTestRule.onNodeWithText("Home").assertIsSelected()
    }
}
