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
 * Configuration-change resilience.
 *
 * Uses [ActivityScenario.recreate] to simulate a configuration change
 * (rotation, dark mode, locale, font scale, density, multi-window resize —
 * they all reduce to the same Activity-recreate cycle).
 *
 * Pre-PR-A: only asserted "doesn't crash" and explicitly accepted "may reset
 * to Home" — the back stack was a plain `remember { mutableStateListOf(...) }`
 * that did not survive Activity recreation. The save-restore signal is now
 * `rememberNavBackStack()` and `rememberSaveable` for in-screen UI state, so
 * post-recreate the user lands on the **same** screen they were on. These
 * tests pin that contract.
 */
class ConfigurationChangeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * Activity must recreate without crashing. The original failure was
     * `rememberSaveable` Bundling Nav3 Screen objects that weren't
     * Parcelable; the test stays in place to catch a similar regression
     * in any future saveable wiring.
     */
    @Test
    fun appSurvivesActivityRecreation() {
        composeTestRule.onNodeWithText("Garage").assertIsDisplayed()

        composeTestRule.activityRule.scenario.recreate()

        composeTestRule.onNodeWithText("Garage").assertIsDisplayed()
    }

    /**
     * A sub-screen reached via two clicks (Settings → Diagnostics) must
     * still be the active screen after recreation.
     *
     * "Diagnostics" is the topbar title for that sub-screen and appears
     * nowhere else, so its presence post-recreate proves the back stack
     * was restored to depth-2 (`[Home, Diagnostics]`).
     */
    @Test
    fun subScreenSurvivesRecreation() {
        // Navigate Settings tab → Diagnostics row.
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Diagnostics").performClick()
        composeTestRule.waitForIdle()
        // "Diagnostics" topbar title is unique to this screen.
        composeTestRule.onNodeWithText("Diagnostics").assertIsDisplayed()

        composeTestRule.activityRule.scenario.recreate()

        // Back stack survived: still on Diagnostics.
        composeTestRule.onNodeWithText("Diagnostics").assertIsDisplayed()
    }

    /**
     * Navigate to each top-level tab and recreate — none should crash.
     *
     * The test does not assert which tab is selected post-recreate per
     * iteration (that would require a per-tab unique text marker the
     * default state doesn't always emit). The depth-2 case above is the
     * load-bearing assertion that the back stack rides through recreation.
     */
    @Test
    fun allTabsSurviveRecreation() {
        for (tab in listOf("Home", "History", "Settings")) {
            composeTestRule.onNodeWithText(tab).performClick()
            composeTestRule.waitForIdle()

            composeTestRule.activityRule.scenario.recreate()

            composeTestRule.onNodeWithText("Garage").assertIsDisplayed()
        }
    }
}
