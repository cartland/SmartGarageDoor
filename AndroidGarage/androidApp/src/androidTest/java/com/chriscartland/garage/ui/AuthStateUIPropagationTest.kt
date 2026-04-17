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
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.model.User
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

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
 */
@OptIn(ExperimentalPermissionsApi::class)
class AuthStateUIPropagationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val testUser = User(
        name = DisplayName("Test User"),
        email = Email("test@example.com"),
        idToken = FirebaseIdToken(idToken = "test-token", exp = Long.MAX_VALUE),
    )

    private val grantedPermission = object : PermissionState {
        override val permission = "android.permission.POST_NOTIFICATIONS"
        override val status = PermissionStatus.Granted

        override fun launchPermissionRequest() {}
    }

    // --- Layer 1: Static rendering ---

    @Test
    fun unknownAuthStateShowsCheckingText() {
        composeTestRule.setContent {
            HomeContent(
                currentDoorEvent = LoadingResult.Complete(null),
                authState = AuthState.Unknown,
                notificationPermissionState = grantedPermission,
            )
        }
        composeTestRule.onNodeWithText("Checking authentication...").assertIsDisplayed()
    }

    @Test
    fun unauthenticatedShowsSignInButton() {
        composeTestRule.setContent {
            HomeContent(
                currentDoorEvent = LoadingResult.Complete(null),
                authState = AuthState.Unauthenticated,
                notificationPermissionState = grantedPermission,
            )
        }
        composeTestRule.onNodeWithText("Sign to access garage remote button").assertIsDisplayed()
    }

    @Test
    fun authenticatedDoesNotShowSignInButton() {
        composeTestRule.setContent {
            HomeContent(
                currentDoorEvent = LoadingResult.Complete(null),
                authState = AuthState.Authenticated(testUser),
                notificationPermissionState = grantedPermission,
            )
        }
        composeTestRule
            .onNodeWithText("Sign to access garage remote button")
            .assertDoesNotExist()
    }

    // --- Layer 2: Dynamic — StateFlow drives recomposition ---

    @Test
    fun signInUpdatesUIFromUnauthenticatedToAuthenticated() {
        val authStateFlow = MutableStateFlow<AuthState>(AuthState.Unauthenticated)

        composeTestRule.setContent {
            val authState by authStateFlow.collectAsState()
            HomeContent(
                currentDoorEvent = LoadingResult.Complete(null),
                authState = authState,
                notificationPermissionState = grantedPermission,
            )
        }

        // Initially shows sign-in button
        composeTestRule
            .onNodeWithText("Sign to access garage remote button")
            .assertIsDisplayed()

        // Simulate sign-in completing
        authStateFlow.value = AuthState.Authenticated(testUser)
        composeTestRule.waitForIdle()

        // Sign-in button should be gone
        composeTestRule
            .onNodeWithText("Sign to access garage remote button")
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithText("Checking authentication...")
            .assertDoesNotExist()
    }

    @Test
    fun signOutUpdatesUIFromAuthenticatedToUnauthenticated() {
        val authStateFlow = MutableStateFlow<AuthState>(AuthState.Authenticated(testUser))

        composeTestRule.setContent {
            val authState by authStateFlow.collectAsState()
            HomeContent(
                currentDoorEvent = LoadingResult.Complete(null),
                authState = authState,
                notificationPermissionState = grantedPermission,
            )
        }

        // Initially no sign-in button (authenticated)
        composeTestRule
            .onNodeWithText("Sign to access garage remote button")
            .assertDoesNotExist()

        // Simulate sign-out
        authStateFlow.value = AuthState.Unauthenticated
        composeTestRule.waitForIdle()

        // Sign-in button should appear
        composeTestRule
            .onNodeWithText("Sign to access garage remote button")
            .assertIsDisplayed()
    }

    @Test
    fun unknownToAuthenticatedUpdatesUI() {
        val authStateFlow = MutableStateFlow<AuthState>(AuthState.Unknown)

        composeTestRule.setContent {
            val authState by authStateFlow.collectAsState()
            HomeContent(
                currentDoorEvent = LoadingResult.Complete(null),
                authState = authState,
                notificationPermissionState = grantedPermission,
            )
        }

        composeTestRule.onNodeWithText("Checking authentication...").assertIsDisplayed()

        authStateFlow.value = AuthState.Authenticated(testUser)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Checking authentication...").assertDoesNotExist()
    }

    @Test
    fun rapidSignInSignOutCycleUpdatesCorrectly() {
        val authStateFlow = MutableStateFlow<AuthState>(AuthState.Unauthenticated)

        composeTestRule.setContent {
            val authState by authStateFlow.collectAsState()
            HomeContent(
                currentDoorEvent = LoadingResult.Complete(null),
                authState = authState,
                notificationPermissionState = grantedPermission,
            )
        }

        // Sign in
        authStateFlow.value = AuthState.Authenticated(testUser)
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Sign to access garage remote button")
            .assertDoesNotExist()

        // Sign out
        authStateFlow.value = AuthState.Unauthenticated
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Sign to access garage remote button")
            .assertIsDisplayed()

        // Sign in again with different token
        val newUser = testUser.copy(
            idToken = FirebaseIdToken(idToken = "different-token", exp = Long.MAX_VALUE),
        )
        authStateFlow.value = AuthState.Authenticated(newUser)
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Sign to access garage remote button")
            .assertDoesNotExist()
    }
}
