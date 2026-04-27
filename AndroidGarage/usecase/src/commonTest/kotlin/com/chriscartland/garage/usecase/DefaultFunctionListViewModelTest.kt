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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FeatureAllowlist
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import com.chriscartland.garage.testcommon.FakeFeatureAllowlistRepository
import com.chriscartland.garage.testcommon.FakeRemoteButtonRepository
import com.chriscartland.garage.testcommon.FakeSnoozeRepository
import com.chriscartland.garage.testcommon.TestDispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultFunctionListViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var doorRepository: FakeDoorRepository
    private lateinit var remoteButtonRepository: FakeRemoteButtonRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var snoozeRepository: FakeSnoozeRepository
    private lateinit var featureAllowlistRepository: FakeFeatureAllowlistRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        doorRepository = FakeDoorRepository()
        remoteButtonRepository = FakeRemoteButtonRepository()
        authRepository = FakeAuthRepository()
        snoozeRepository = FakeSnoozeRepository()
        featureAllowlistRepository = FakeFeatureAllowlistRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(authState: AuthState = AuthState.Unauthenticated): DefaultFunctionListViewModel {
        authRepository.setAuthState(authState)
        val ensureFreshIdToken = EnsureFreshIdTokenUseCase(authRepository)
        return DefaultFunctionListViewModel(
            pushRemoteButtonUseCase = PushRemoteButtonUseCase(
                ensureFreshIdToken,
                authRepository,
                remoteButtonRepository,
            ),
            fetchCurrentDoorEventUseCase = FetchCurrentDoorEventUseCase(doorRepository),
            fetchRecentDoorEventsUseCase = FetchRecentDoorEventsUseCase(doorRepository),
            snoozeNotificationsUseCase = SnoozeNotificationsUseCase(
                ensureFreshIdToken,
                authRepository,
                snoozeRepository,
            ),
            signInWithGoogleUseCase = SignInWithGoogleUseCase(authRepository),
            signOutUseCase = SignOutUseCase(authRepository),
            observeDoorEventsUseCase = ObserveDoorEventsUseCase(doorRepository),
            observeFeatureAccessUseCase = ObserveFeatureAccessUseCase(featureAllowlistRepository),
            dispatchers = TestDispatcherProvider(testDispatcher),
            appVersion = "test",
        )
    }

    private fun authenticated(): AuthState.Authenticated =
        AuthState.Authenticated(
            user = User(
                name = DisplayName("Test"),
                email = Email("test@test.com"),
                idToken = FirebaseIdToken(idToken = "token", exp = Long.MAX_VALUE),
            ),
        )

    @Test
    fun refreshDoorStatusInvokesRepository() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            viewModel.refreshDoorStatus()
            advanceUntilIdle()
            assertEquals(1, doorRepository.fetchCurrentDoorEventCount)
        }

    @Test
    fun refreshDoorHistoryInvokesRepository() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            viewModel.refreshDoorHistory()
            advanceUntilIdle()
            assertEquals(1, doorRepository.fetchRecentDoorEventsCount)
        }

    @Test
    fun openOrCloseDoorPushesButtonWhenAuthenticated() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(authState = authenticated())
            viewModel.openOrCloseDoor()
            advanceUntilIdle()
            assertEquals(1, remoteButtonRepository.pushCount)
        }

    @Test
    fun openOrCloseDoorIsNoopWhenUnauthenticated() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(authState = AuthState.Unauthenticated)
            viewModel.openOrCloseDoor()
            advanceUntilIdle()
            assertEquals(0, remoteButtonRepository.pushCount)
        }

    @Test
    fun snoozeOneHourCallsRepoWithLatestDoorEventTimestamp() =
        runTest(testDispatcher) {
            doorRepository.setCurrentDoorEvent(
                DoorEvent(lastChangeTimeSeconds = 12345L),
            )
            val viewModel = createViewModel(authState = authenticated())
            advanceUntilIdle() // let init's collector cache the door event

            viewModel.snoozeNotificationsForOneHour()
            advanceUntilIdle()

            assertEquals(1, snoozeRepository.snoozeCount)
            assertEquals(12345L, snoozeRepository.snoozeCalls.last().snoozeEventTimestampSeconds)
        }

    @Test
    fun signInWithGoogleUpdatesAuthState() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            authRepository.setSignInResult(authenticated())
            viewModel.signInWithGoogle(GoogleIdToken("test-token"))
            advanceUntilIdle()
            assertIs<AuthState.Authenticated>(authRepository.authState.value)
        }

    @Test
    fun signOutUpdatesAuthState() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(authState = authenticated())
            viewModel.signOut()
            advanceUntilIdle()
            assertIs<AuthState.Unauthenticated>(authRepository.authState.value)
        }

    @Test
    fun accessGrantedIsNullBeforeAllowlistResolves() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(authState = authenticated())
            advanceUntilIdle()
            assertNull(viewModel.accessGranted.value)
        }

    @Test
    fun accessGrantedReflectsAllowlistTrue() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(authState = authenticated())
            featureAllowlistRepository.setAllowlist(FeatureAllowlist(functionList = true))
            advanceUntilIdle()
            assertTrue(viewModel.accessGranted.value == true)
        }

    @Test
    fun accessGrantedReflectsAllowlistFalse() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(authState = authenticated())
            featureAllowlistRepository.setAllowlist(FeatureAllowlist(functionList = false))
            advanceUntilIdle()
            assertFalse(viewModel.accessGranted.value == true)
            // Distinguish "false" from "null"
            assertEquals(false, viewModel.accessGranted.value)
        }

    @Test
    fun accessGrantedClearsToNullWhenAllowlistClears() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(authState = authenticated())
            featureAllowlistRepository.setAllowlist(FeatureAllowlist(functionList = true))
            advanceUntilIdle()
            assertEquals(true, viewModel.accessGranted.value)
            featureAllowlistRepository.setAllowlist(null)
            advanceUntilIdle()
            assertNull(viewModel.accessGranted.value)
        }
}
