package com.chriscartland.garage.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.chriscartland.garage.MainActivity
import org.junit.Rule
import org.junit.Test

/**
 * Tests that the app survives configuration changes (rotation, dark mode toggle, etc.)
 * without crashing.
 *
 * Uses [ActivityScenario.recreate] to simulate a configuration change, which destroys
 * and recreates the Activity. This catches:
 * - State that isn't properly saved/restored
 * - ViewModels that don't survive recreation
 * - Compose state that crashes on re-composition after recreation
 */
class ConfigurationChangeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * The app must not crash when the Activity is recreated (e.g., rotation).
     *
     * This was the primary crash bug: rememberSaveable tried to Bundle-save
     * Screen objects that aren't Parcelable, causing a crash on save.
     */
    @Test
    fun appSurvivesActivityRecreation() {
        // Verify app is running
        composeTestRule.onNodeWithText("Garage").assertIsDisplayed()

        // Simulate configuration change (rotation)
        composeTestRule.activityRule.scenario.recreate()

        // App should still be running after recreation
        composeTestRule.onNodeWithText("Garage").assertIsDisplayed()
    }

    /**
     * Navigation state should survive configuration change.
     * After recreation, we should still see the app functioning.
     */
    @Test
    fun navigationSurvivesRecreation() {
        // Navigate to History
        composeTestRule.onNodeWithText("History").performClick()
        composeTestRule.waitForIdle()

        // Recreate Activity
        composeTestRule.activityRule.scenario.recreate()

        // App should still be functional (may reset to Home, that's OK)
        composeTestRule.onNodeWithText("Garage").assertIsDisplayed()
    }

    /**
     * Navigate to each screen and recreate — none should crash.
     */
    @Test
    fun allScreensSurviveRecreation() {
        for (tab in listOf("Home", "History", "Settings")) {
            composeTestRule.onNodeWithText(tab).performClick()
            composeTestRule.waitForIdle()

            composeTestRule.activityRule.scenario.recreate()

            composeTestRule.onNodeWithText("Garage").assertIsDisplayed()
        }
    }
}
