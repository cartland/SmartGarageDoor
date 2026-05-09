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
     * A non-Home tab must still be the active screen after recreation.
     *
     * Depth-1 to a non-Home destination is the load-bearing assertion:
     * pre-PR-A this would always reset to Home regardless of where the
     * user was. Asserting that "Privacy Policy" (a Settings-only row)
     * is still displayed post-recreate proves the back stack survived.
     *
     * Why not navigate to a sub-screen? Diagnostics and FunctionList
     * are gated by the developer allowlist (`functionListAccess`), so
     * they're not reachable from a fresh install in test conditions.
     * "Privacy Policy" is in the always-visible "About" section of
     * Settings — independent of auth state, allowlist, or feature flags.
     */
    @Test
    fun nonHomeTabSurvivesRecreation() {
        // Navigate to Settings tab.
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.waitForIdle()
        // "Privacy Policy" row is unique to Settings and always visible.
        composeTestRule.onNodeWithText("Privacy Policy").assertIsDisplayed()

        composeTestRule.activityRule.scenario.recreate()

        // Back stack survived: still on Settings.
        composeTestRule.onNodeWithText("Privacy Policy").assertIsDisplayed()
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
