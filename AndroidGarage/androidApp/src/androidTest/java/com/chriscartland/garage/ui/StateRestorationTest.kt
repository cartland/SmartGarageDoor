package com.chriscartland.garage.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/**
 * Tests that verify UI survives configuration changes and process death.
 *
 * Uses [StateRestorationTester] to simulate process death (save/restore state)
 * without needing a real device process kill. This runs as an instrumented test
 * because it needs the Compose runtime.
 *
 * These tests catch bugs like:
 * - rememberSaveable with non-Parcelable types (crashes on restore)
 * - remember losing state on configuration change
 * - ExpandableColumnCard ignoring external state updates
 */
class StateRestorationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Regression test: ExpandableColumnCard must reflect external state changes.
     *
     * Before the fix, the card used `remember { mutableStateOf(startExpanded) }` which
     * captured only the initial value. When DataStore loaded the persisted value later,
     * the card ignored it.
     */
    @Test
    fun expandableCard_reflectsExternalStateChange() {
        var externalExpanded by mutableStateOf(true)

        composeTestRule.setContent {
            ExpandableColumnCard(
                title = "Test Card",
                startExpanded = externalExpanded,
            ) {
                Text("Card Content")
            }
        }

        // Initially expanded — content visible
        composeTestRule.onNodeWithText("Card Content").assertIsDisplayed()

        // Simulate DataStore loading a saved "collapsed" value
        externalExpanded = false
        composeTestRule.waitForIdle()

        // Card should now be collapsed — content not displayed
        composeTestRule.onNodeWithText("Card Content").assertDoesNotExist()
    }

    /**
     * Regression test: ExpandableColumnCard must call onExpandedChange on click.
     *
     * Before the fix, clicking the card toggled local state but never called
     * onExpandedChange, so the preference was never saved to DataStore.
     */
    @Test
    fun expandableCard_clickCallsOnExpandedChange() {
        var callbackValue: Boolean? = null

        composeTestRule.setContent {
            ExpandableColumnCard(
                title = "Test Card",
                startExpanded = true,
                onExpandedChange = { callbackValue = it },
            ) {
                Text("Card Content")
            }
        }

        // Click title to collapse
        composeTestRule.onNodeWithText("Test Card").performClick()
        composeTestRule.waitForIdle()

        assert(callbackValue == false) {
            "Expected onExpandedChange(false) after collapsing, got $callbackValue"
        }
    }

    /**
     * Verify that ExpandableColumnCard survives state restoration (process death).
     *
     * StateRestorationTester simulates Bundle save/restore cycle.
     * Since we use `remember` (not `rememberSaveable`), the card resets
     * to the startExpanded value — which is fine because the persisted value
     * comes from DataStore, not the saved instance state.
     */
    @Test
    fun expandableCard_survivesStateRestoration() {
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            ExpandableColumnCard(
                title = "Test Card",
                startExpanded = true,
            ) {
                Text("Card Content")
            }
        }

        // Content visible before restoration
        composeTestRule.onNodeWithText("Card Content").assertIsDisplayed()

        // Simulate process death and restoration — should not crash
        restorationTester.emulateSavedInstanceStateRestore()

        // Card re-renders with startExpanded=true — content still visible
        composeTestRule.onNodeWithText("Card Content").assertIsDisplayed()
    }
}
