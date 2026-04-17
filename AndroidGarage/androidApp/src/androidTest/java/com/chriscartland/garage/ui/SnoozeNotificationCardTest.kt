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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.chriscartland.garage.domain.model.SnoozeAction
import com.chriscartland.garage.domain.model.SnoozeState
import org.junit.Rule
import org.junit.Test

/**
 * Verifies that the snooze notification card displays the correct status
 * and action text for each state combination.
 *
 * Status text (left side) always reflects the current server state.
 * Action text (right side, under button) shows ephemeral feedback.
 */
class SnoozeNotificationCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // --- Status text ---

    @Test
    fun loadingStateShowsDoorNotifications() {
        composeTestRule.setContent {
            SnoozeNotificationCard(
                snoozeState = SnoozeState.Loading,
                snoozeAction = SnoozeAction.Idle,
            )
        }
        composeTestRule.onNodeWithText("Door notifications").assertIsDisplayed()
    }

    @Test
    fun notSnoozingStateShowsEnabled() {
        composeTestRule.setContent {
            SnoozeNotificationCard(
                snoozeState = SnoozeState.NotSnoozing,
                snoozeAction = SnoozeAction.Idle,
            )
        }
        composeTestRule.onNodeWithText("Door notifications enabled").assertIsDisplayed()
    }

    @Test
    fun snoozingStateShowsSnoozedUntilTime() {
        composeTestRule.setContent {
            SnoozeNotificationCard(
                snoozeState = SnoozeState.Snoozing(untilEpochSeconds = 999999999999L),
                snoozeAction = SnoozeAction.Idle,
            )
        }
        composeTestRule
            .onNodeWithText("Door notifications snoozed until", substring = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("or until the door moves").assertIsDisplayed()
    }

    // --- Action overlay ---

    @Test
    fun idleActionShowsNoOverlay() {
        composeTestRule.setContent {
            SnoozeNotificationCard(
                snoozeState = SnoozeState.NotSnoozing,
                snoozeAction = SnoozeAction.Idle,
            )
        }
        composeTestRule.onNodeWithText("Saving...").assertDoesNotExist()
        composeTestRule.onNodeWithText("Notifications resumed").assertDoesNotExist()
    }

    @Test
    fun sendingActionShowsSaving() {
        composeTestRule.setContent {
            SnoozeNotificationCard(
                snoozeState = SnoozeState.NotSnoozing,
                snoozeAction = SnoozeAction.Sending,
            )
        }
        composeTestRule.onNodeWithText("Saving...").assertIsDisplayed()
    }

    @Test
    fun succeededClearedShowsNotificationsResumed() {
        composeTestRule.setContent {
            SnoozeNotificationCard(
                snoozeState = SnoozeState.NotSnoozing,
                snoozeAction = SnoozeAction.Succeeded.Cleared,
            )
        }
        composeTestRule.onNodeWithText("Notifications resumed").assertIsDisplayed()
    }

    @Test
    fun succeededSetShowsSavedMessage() {
        composeTestRule.setContent {
            SnoozeNotificationCard(
                snoozeState = SnoozeState.Snoozing(untilEpochSeconds = 999999999999L),
                snoozeAction = SnoozeAction.Succeeded.Set(untilEpochSeconds = 999999999999L),
            )
        }
        composeTestRule
            .onNodeWithText("Saved! Snoozing until", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun failedNotAuthenticatedShowsSignInMessage() {
        composeTestRule.setContent {
            SnoozeNotificationCard(
                snoozeState = SnoozeState.NotSnoozing,
                snoozeAction = SnoozeAction.Failed.NotAuthenticated,
            )
        }
        composeTestRule.onNodeWithText("Sign in to snooze notifications").assertIsDisplayed()
    }

    @Test
    fun failedNetworkErrorShowsServerMessage() {
        composeTestRule.setContent {
            SnoozeNotificationCard(
                snoozeState = SnoozeState.Snoozing(untilEpochSeconds = 999999999999L),
                snoozeAction = SnoozeAction.Failed.NetworkError,
            )
        }
        // Status text still shows snoozed (not overwritten by error)
        composeTestRule
            .onNodeWithText("Door notifications snoozed until", substring = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Couldn't reach server").assertIsDisplayed()
    }

    @Test
    fun failedMissingDataShowsMissingMessage() {
        composeTestRule.setContent {
            SnoozeNotificationCard(
                snoozeState = SnoozeState.NotSnoozing,
                snoozeAction = SnoozeAction.Failed.MissingData,
            )
        }
        composeTestRule.onNodeWithText("No door event available").assertIsDisplayed()
    }

    // --- Button ---

    @Test
    fun snoozeButtonIsDisplayed() {
        composeTestRule.setContent {
            SnoozeNotificationCard(
                snoozeState = SnoozeState.NotSnoozing,
                snoozeAction = SnoozeAction.Idle,
            )
        }
        composeTestRule.onNodeWithText("Snooze").assertIsDisplayed()
    }
}
