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

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.ui.home.DeviceCheckInDisplay
import com.chriscartland.garage.ui.home.HomeMapper
import com.chriscartland.garage.ui.home.HomeStatusDisplay
import com.chriscartland.garage.usecase.ButtonHealthDisplay
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import com.chriscartland.garage.ui.home.HomeContent as HomeStatelessContent

/**
 * Verifies that auth state changes propagate to the Compose UI.
 *
 * Bug context: sign-in / sign-out worked (Firebase state changed) but the UI
 * did not visually update until the app was killed. These tests verify the
 * StateFlow → collectAsState → recomposition chain in isolation, without the
 * full DI or ViewModel layer, to establish that the Compose rendering path
 * is correct. If these pass but the device still fails, the issue is in the
 * ViewModel / DI / navigation layer.
 *
 * Layer 1: Static rendering — HomeContent shows the right UI for each AuthState.
 * Layer 2: Dynamic — StateFlow change triggers recomposition.
 *
 * Targets the stateless Home Composable in `ui.home` (post-mapper extraction);
 * the legacy `ui.HomeContent` is now a DI-resolving bridge unsuitable for a
 * unit-style instrumented test.
 */
class AuthStateUIPropagationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val testUser = User(
        name = DisplayName("Test User"),
        email = Email("test@example.com"),
    )

    private val unknownStatus = HomeStatusDisplay(
        doorPosition = DoorPosition.UNKNOWN,
        sinceLine = "Last change time unknown",
    )

    private val noDataCheckIn = DeviceCheckInDisplay(
        durationLabel = "No data yet",
        isStale = false,
    )

    // --- Layer 1: Static rendering ---

    @Test
    fun unknownAuthStateShowsCheckingText() {
        composeTestRule.setContent {
            HomeStatelessContent(
                status = unknownStatus,
                authState = HomeMapper.toHomeAuthState(AuthState.Unknown),
                deviceCheckIn = noDataCheckIn,
                buttonHealthDisplay = ButtonHealthDisplay.Loading,
            )
        }
        composeTestRule.onNodeWithText("Checking sign-in…").assertIsDisplayed()
    }

    @Test
    fun unauthenticatedShowsSignInButton() {
        composeTestRule.setContent {
            HomeStatelessContent(
                status = unknownStatus,
                authState = HomeMapper.toHomeAuthState(AuthState.Unauthenticated),
                deviceCheckIn = noDataCheckIn,
                buttonHealthDisplay = ButtonHealthDisplay.Loading,
            )
        }
        composeTestRule.onNodeWithText("Sign in with Google").assertIsDisplayed()
    }

    @Test
    fun authenticatedDoesNotShowSignInButton() {
        composeTestRule.setContent {
            HomeStatelessContent(
                status = unknownStatus,
                authState = HomeMapper.toHomeAuthState(AuthState.Authenticated(testUser)),
                deviceCheckIn = noDataCheckIn,
                buttonHealthDisplay = ButtonHealthDisplay.Loading,
            )
        }
        composeTestRule.onNodeWithText("Sign in with Google").assertDoesNotExist()
    }

    // --- Layer 2: Dynamic — StateFlow drives recomposition ---

    @Test
    fun signInUpdatesUIFromUnauthenticatedToAuthenticated() {
        val authStateFlow = MutableStateFlow<AuthState>(AuthState.Unauthenticated)

        composeTestRule.setContent {
            val authState by authStateFlow.collectAsState()
            HomeStatelessContent(
                status = unknownStatus,
                authState = HomeMapper.toHomeAuthState(authState),
                deviceCheckIn = noDataCheckIn,
                buttonHealthDisplay = ButtonHealthDisplay.Loading,
            )
        }

        // Initially shows sign-in button
        composeTestRule.onNodeWithText("Sign in with Google").assertIsDisplayed()

        // Simulate sign-in completing
        authStateFlow.value = AuthState.Authenticated(testUser)
        composeTestRule.waitForIdle()

        // Sign-in button should be gone
        composeTestRule.onNodeWithText("Sign in with Google").assertDoesNotExist()
        composeTestRule.onNodeWithText("Checking sign-in…").assertDoesNotExist()
    }

    @Test
    fun signOutUpdatesUIFromAuthenticatedToUnauthenticated() {
        val authStateFlow = MutableStateFlow<AuthState>(AuthState.Authenticated(testUser))

        composeTestRule.setContent {
            val authState by authStateFlow.collectAsState()
            HomeStatelessContent(
                status = unknownStatus,
                authState = HomeMapper.toHomeAuthState(authState),
                deviceCheckIn = noDataCheckIn,
                buttonHealthDisplay = ButtonHealthDisplay.Loading,
            )
        }

        // Initially no sign-in button (authenticated)
        composeTestRule.onNodeWithText("Sign in with Google").assertDoesNotExist()

        // Simulate sign-out
        authStateFlow.value = AuthState.Unauthenticated
        composeTestRule.waitForIdle()

        // Sign-in button should appear
        composeTestRule.onNodeWithText("Sign in with Google").assertIsDisplayed()
    }

    @Test
    fun unknownToAuthenticatedUpdatesUI() {
        val authStateFlow = MutableStateFlow<AuthState>(AuthState.Unknown)

        composeTestRule.setContent {
            val authState by authStateFlow.collectAsState()
            HomeStatelessContent(
                status = unknownStatus,
                authState = HomeMapper.toHomeAuthState(authState),
                deviceCheckIn = noDataCheckIn,
                buttonHealthDisplay = ButtonHealthDisplay.Loading,
            )
        }

        composeTestRule.onNodeWithText("Checking sign-in…").assertIsDisplayed()

        authStateFlow.value = AuthState.Authenticated(testUser)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Checking sign-in…").assertDoesNotExist()
    }

    @Test
    fun rapidSignInSignOutCycleUpdatesCorrectly() {
        val authStateFlow = MutableStateFlow<AuthState>(AuthState.Unauthenticated)

        composeTestRule.setContent {
            val authState by authStateFlow.collectAsState()
            HomeStatelessContent(
                status = unknownStatus,
                authState = HomeMapper.toHomeAuthState(authState),
                deviceCheckIn = noDataCheckIn,
                buttonHealthDisplay = ButtonHealthDisplay.Loading,
            )
        }

        // Sign in
        authStateFlow.value = AuthState.Authenticated(testUser)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Sign in with Google").assertDoesNotExist()

        // Sign out
        authStateFlow.value = AuthState.Unauthenticated
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Sign in with Google").assertIsDisplayed()

        // Sign in again with a different identity (ADR-027: AuthState is identity-only).
        val newUser = testUser.copy(name = DisplayName("Different User"))
        authStateFlow.value = AuthState.Authenticated(newUser)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Sign in with Google").assertDoesNotExist()
    }
}
