/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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

package com.chriscartland.garage.wear.ui

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.chriscartland.garage.domain.model.RemoteButtonState
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import com.chriscartland.garage.testcommon.FakeRemoteButtonRepository
import com.chriscartland.garage.testcommon.TestDispatcherProvider
import com.chriscartland.garage.usecase.ButtonStateMachine
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import com.chriscartland.garage.usecase.ObserveAuthStateUseCase
import com.chriscartland.garage.usecase.ObserveDoorEventsUseCase
import com.chriscartland.garage.usecase.PushRemoteButtonUseCase
import com.chriscartland.garage.usecase.SignInWithGoogleUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Wear hero screen's tap-to-arm / hold-to-confirm flow.
 *
 * All network and auth boundaries are fakes ([FakeRemoteButtonRepository]
 * et al.) — nothing here can reach the real server or the real door. The
 * safety-critical properties pinned by these tests:
 *  - a quick tap while armed never submits a press (only a completed hold does)
 *  - releasing before the hold completes never submits
 *  - nothing arms or submits while signed out
 *  - touches keep the button armed (window counts from the last touch);
 *    only a quiet period with no touches disarms it
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearHomeViewModelTest {
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var doorRepository: FakeDoorRepository
    private lateinit var remoteButtonRepository: FakeRemoteButtonRepository

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(): WearHomeViewModel {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        authRepository = FakeAuthRepository()
        doorRepository = FakeDoorRepository()
        remoteButtonRepository = FakeRemoteButtonRepository()
        return WearHomeViewModel(
            observeDoorEvents = ObserveDoorEventsUseCase(doorRepository),
            observeAuthState = ObserveAuthStateUseCase(authRepository),
            pushRemoteButtonUseCase = PushRemoteButtonUseCase(authRepository, remoteButtonRepository),
            signInWithGoogleUseCase = SignInWithGoogleUseCase(authRepository),
            fetchCurrentDoorEventUseCase = FetchCurrentDoorEventUseCase(doorRepository),
            dispatchers = TestDispatcherProvider(testDispatcher),
            appVersion = "wear-test",
        )
    }

    private fun signIn() {
        authRepository.setAuthState(
            AuthState.Authenticated(
                User(
                    name = DisplayName("Test User"),
                    email = Email("test@example.com"),
                ),
            ),
        )
    }

    /** Arm the button: tap, then run the Preparing delay out. */
    private fun TestScope.arm(viewModel: WearHomeViewModel) {
        viewModel.onDoorTap()
        advanceTimeBy(PREPARING_DELAY_MILLIS + 1)
        runCurrent()
        assertEquals(RemoteButtonState.AwaitingConfirmation, viewModel.buttonState.value)
    }

    @Test
    fun tapWhileReadyArmsButton() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            viewModel.onDoorTap()
            runCurrent()
            assertEquals(RemoteButtonState.Preparing, viewModel.buttonState.value)
            advanceTimeBy(PREPARING_DELAY_MILLIS + 1)
            runCurrent()
            assertEquals(RemoteButtonState.AwaitingConfirmation, viewModel.buttonState.value)
            assertEquals(0, remoteButtonRepository.pushCount)
        }

    @Test
    fun tapWhileSignedOutDoesNothing() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onDoorTap()
            advanceTimeBy(PREPARING_DELAY_MILLIS + 1)
            runCurrent()
            assertEquals(RemoteButtonState.Ready, viewModel.buttonState.value)
            assertEquals(0, remoteButtonRepository.pushCount)
        }

    @Test
    fun completedHoldSubmitsPress() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            authRepository.setIdTokenResult(null)
            arm(viewModel)
            viewModel.onHoldStart()
            assertTrue(viewModel.isHolding.value)
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS + 1)
            runCurrent()
            assertEquals(1, remoteButtonRepository.pushCount)
            assertFalse(viewModel.isHolding.value)
            assertEquals(RemoteButtonState.SendingToDoor, viewModel.buttonState.value)
            assertTrue(
                remoteButtonRepository.pushCalls
                    .first()
                    .buttonAckToken
                    .startsWith("android-wear-test-"),
            )
        }

    @Test
    fun earlyReleaseDoesNotSubmit() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            arm(viewModel)
            viewModel.onHoldStart()
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS / 2)
            viewModel.onHoldEnd()
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS)
            runCurrent()
            assertEquals(0, remoteButtonRepository.pushCount)
            assertFalse(viewModel.isHolding.value)
            assertEquals(RemoteButtonState.AwaitingConfirmation, viewModel.buttonState.value)
        }

    @Test
    fun quickTapWhileArmedDoesNotSubmit() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            arm(viewModel)
            // A stray tap on the watch face must never fire the real button.
            viewModel.onDoorTap()
            runCurrent()
            assertEquals(RemoteButtonState.AwaitingConfirmation, viewModel.buttonState.value)
            assertEquals(0, remoteButtonRepository.pushCount)
        }

    @Test
    fun partialTapsKeepButtonArmed() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            arm(viewModel)
            // Repeated aborted holds spanning well past the original window:
            // every touch (down and up) restarts the disarm timer.
            repeat(3) {
                advanceTimeBy(ButtonStateMachine.DEFAULT_CONFIRMATION_TIMEOUT - 1_000L)
                viewModel.onHoldStart()
                advanceTimeBy(200L)
                viewModel.onHoldEnd()
                runCurrent()
                assertEquals(RemoteButtonState.AwaitingConfirmation, viewModel.buttonState.value)
            }
            assertEquals(0, remoteButtonRepository.pushCount)
            // A quiet period with no touches disarms.
            advanceTimeBy(ButtonStateMachine.DEFAULT_CONFIRMATION_TIMEOUT + 1)
            runCurrent()
            assertEquals(RemoteButtonState.Cancelled, viewModel.buttonState.value)
            assertEquals(0, remoteButtonRepository.pushCount)
        }

    @Test
    fun screenTouchKeepsButtonArmed() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            arm(viewModel)
            // A touch anywhere on the screen (not just the door) keeps it armed.
            advanceTimeBy(ButtonStateMachine.DEFAULT_CONFIRMATION_TIMEOUT - 1_000L)
            viewModel.onScreenTouch()
            runCurrent()
            advanceTimeBy(ButtonStateMachine.DEFAULT_CONFIRMATION_TIMEOUT - 1_000L)
            runCurrent()
            assertEquals(RemoteButtonState.AwaitingConfirmation, viewModel.buttonState.value)
            assertEquals(0, remoteButtonRepository.pushCount)
        }

    @Test
    fun holdStartedLateInArmedWindowStillSubmits() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            arm(viewModel)
            // Finger down just before the original window would have expired:
            // the touch restarts the timer, so the hold always gets its full
            // 2 seconds. (Previously the machine could disarm mid-hold and a
            // visually completed ring fired into a dead state.)
            advanceTimeBy(ButtonStateMachine.DEFAULT_CONFIRMATION_TIMEOUT - 500L)
            viewModel.onHoldStart()
            runCurrent()
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS + 1)
            runCurrent()
            assertEquals(1, remoteButtonRepository.pushCount)
            assertEquals(RemoteButtonState.SendingToDoor, viewModel.buttonState.value)
        }

    @Test
    fun armedDisarmsAfterQuietPeriod() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            arm(viewModel)
            advanceTimeBy(ButtonStateMachine.DEFAULT_CONFIRMATION_TIMEOUT + 1)
            runCurrent()
            assertEquals(RemoteButtonState.Cancelled, viewModel.buttonState.value)
            assertEquals(0, remoteButtonRepository.pushCount)
        }

    @Test
    fun holdWhileReadyDoesNothing() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            viewModel.onHoldStart()
            assertFalse(viewModel.isHolding.value)
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS + 1)
            runCurrent()
            assertEquals(RemoteButtonState.Ready, viewModel.buttonState.value)
            assertEquals(0, remoteButtonRepository.pushCount)
        }

    @Test
    fun doorMovementAfterSubmitSucceeds() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            arm(viewModel)
            viewModel.onHoldStart()
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS + 1)
            runCurrent()
            assertEquals(RemoteButtonState.SendingToDoor, viewModel.buttonState.value)
            doorRepository.setCurrentDoorEvent(
                DoorEvent(doorPosition = DoorPosition.OPENING, lastChangeTimeSeconds = 123L),
            )
            runCurrent()
            assertEquals(RemoteButtonState.Succeeded, viewModel.buttonState.value)
        }

    @Test
    fun submitFailureShowsServerFailed() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            remoteButtonRepository.setPushSucceeds(false)
            arm(viewModel)
            viewModel.onHoldStart()
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS + 1)
            runCurrent()
            assertEquals(1, remoteButtonRepository.pushCount)
            assertEquals(RemoteButtonState.ServerFailed, viewModel.buttonState.value)
        }

    @Test
    fun visiblePollsUntilHidden() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onVisible()
            runCurrent()
            assertEquals(1, doorRepository.fetchCurrentDoorEventCount)
            advanceTimeBy(WearHomeViewModel.IDLE_POLL_MILLIS + 1)
            runCurrent()
            assertEquals(2, doorRepository.fetchCurrentDoorEventCount)
            viewModel.onHidden()
            advanceTimeBy(WearHomeViewModel.IDLE_POLL_MILLIS * 5)
            runCurrent()
            assertEquals(2, doorRepository.fetchCurrentDoorEventCount)
        }

    @Test
    fun keepScreenOnOnlyWhilePressInFlightOrDoorMoving() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            runCurrent()
            assertFalse(viewModel.keepScreenOn.value)
            // Armed alone must NOT hold the screen awake (normal viewing).
            arm(viewModel)
            runCurrent()
            assertFalse(viewModel.keepScreenOn.value)
            // A submitted press does.
            viewModel.onHoldStart()
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS + 1)
            runCurrent()
            assertEquals(RemoteButtonState.SendingToDoor, viewModel.buttonState.value)
            assertTrue(viewModel.keepScreenOn.value)
            // Door starts moving: still watching.
            doorRepository.setCurrentDoorEvent(
                DoorEvent(doorPosition = DoorPosition.OPENING, lastChangeTimeSeconds = 123L),
            )
            runCurrent()
            assertTrue(viewModel.keepScreenOn.value)
            // Door reaches a resting state: release immediately.
            doorRepository.setCurrentDoorEvent(
                DoorEvent(doorPosition = DoorPosition.OPEN, lastChangeTimeSeconds = 130L),
            )
            runCurrent()
            assertFalse(viewModel.keepScreenOn.value)
        }

    @Test
    fun keepScreenOnCapsAtWindow() =
        runTest {
            val viewModel = createViewModel()
            // A door moving on its own (no press) lights the screen too…
            doorRepository.setCurrentDoorEvent(
                DoorEvent(doorPosition = DoorPosition.CLOSING, lastChangeTimeSeconds = 123L),
            )
            runCurrent()
            assertTrue(viewModel.keepScreenOn.value)
            // …but never longer than the cap, even if the state persists.
            advanceTimeBy(WearHomeViewModel.KEEP_SCREEN_ON_MILLIS + 1)
            runCurrent()
            assertFalse(viewModel.keepScreenOn.value)
        }

    @Test
    fun doorResponseGraceOutlastsSharedDefault() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            arm(viewModel)
            viewModel.onHoldStart()
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS + 1)
            runCurrent()
            assertEquals(RemoteButtonState.SendingToDoor, viewModel.buttonState.value)
            // The shared default (10s) would already have declared DoorFailed
            // here; the wear-specific grace keeps waiting.
            advanceTimeBy(ButtonStateMachine.DEFAULT_NETWORK_TIMEOUT + 1_000L)
            runCurrent()
            assertEquals(RemoteButtonState.SendingToDoor, viewModel.buttonState.value)
            // The grace eventually gives up if the door never responds.
            advanceTimeBy(
                WearHomeViewModel.DOOR_RESPONSE_TIMEOUT_MILLIS -
                    ButtonStateMachine.DEFAULT_NETWORK_TIMEOUT,
            )
            runCurrent()
            assertEquals(RemoteButtonState.DoorFailed, viewModel.buttonState.value)
        }

    @Test
    fun signInDelegatesToUseCase() =
        runTest {
            val viewModel = createViewModel()
            authRepository.setSignInResult(
                AuthState.Authenticated(
                    User(name = DisplayName("Test User"), email = Email("test@example.com")),
                ),
            )
            viewModel.onSignInResult(GoogleIdToken("test-google-token"))
            advanceUntilIdle()
            assertEquals(1, authRepository.signInCount)
            assertFalse(viewModel.signInError.value)
        }

    @Test
    fun nullSignInResultShowsTransientError() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onSignInResult(null)
            runCurrent()
            assertTrue(viewModel.signInError.value)
            assertEquals(0, authRepository.signInCount)
            advanceTimeBy(WearHomeViewModel.SIGN_IN_ERROR_DISPLAY_MILLIS + 1)
            runCurrent()
            assertFalse(viewModel.signInError.value)
        }

    @Test
    fun failedFirebaseSignInShowsTransientError() =
        runTest {
            val viewModel = createViewModel()
            authRepository.setSignInResult(AuthState.Unauthenticated)
            viewModel.onSignInResult(GoogleIdToken("test-google-token"))
            runCurrent()
            assertEquals(1, authRepository.signInCount)
            assertTrue(viewModel.signInError.value)
            advanceTimeBy(WearHomeViewModel.SIGN_IN_ERROR_DISPLAY_MILLIS + 1)
            runCurrent()
            assertFalse(viewModel.signInError.value)
        }

    @Test
    fun signInStartedClearsError() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onSignInResult(null)
            runCurrent()
            assertTrue(viewModel.signInError.value)
            viewModel.onSignInStarted()
            assertFalse(viewModel.signInError.value)
        }

    companion object {
        /** Mirrors ButtonStateMachine.DEFAULT_PREPARING_DELAY (500ms). */
        private const val PREPARING_DELAY_MILLIS = 500L
    }
}
