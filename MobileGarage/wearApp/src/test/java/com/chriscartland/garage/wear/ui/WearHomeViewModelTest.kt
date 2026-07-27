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
import kotlinx.coroutines.launch
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
 * Unit tests for the Wear hero screen's single hold-to-press gesture.
 *
 * All network and auth boundaries are fakes ([FakeRemoteButtonRepository]
 * et al.) — nothing here can reach the real server or the real door.
 *
 * The safety-critical property is one sentence: **only a hold that runs the
 * full [WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS] while signed in and while
 * the button is at rest ever submits a press.** These tests attack that from
 * every direction — signed out, released early, released a millisecond early,
 * repeated aborted holds, and touches landing while a press is already in
 * flight.
 *
 * Not covered here, because it lives in the UI layer: the hold is also
 * abandoned when the finger drifts past a slop threshold (see
 * `GarageDoorTarget` in `HeroScreen.kt`). That path reaches this ViewModel as
 * an ordinary [WearHomeViewModel.onHoldEnd], which `abortedHold*` pins.
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

    /**
     * Start recording haptic cues. Must be called before the actions under
     * test: `hapticCues` is Channel-backed, so a single collector receives
     * each cue exactly once.
     */
    private fun TestScope.recordCues(viewModel: WearHomeViewModel): List<HapticCue> {
        val cues = mutableListOf<HapticCue>()
        backgroundScope.launch { viewModel.hapticCues.collect { cues += it } }
        runCurrent()
        return cues
    }

    /** Finger down, hold past the confirm duration, finger up. */
    private fun TestScope.completeHold(viewModel: WearHomeViewModel) {
        viewModel.onHoldStart()
        advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS + 1)
        runCurrent()
        viewModel.onHoldEnd()
        runCurrent()
    }

    // --- The one gesture ---

    @Test
    fun completedHoldSubmitsPress() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            authRepository.setIdTokenResult(null)
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
    fun armingDelayElapsesInsideTheHold() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            // Finger down alone arms the machine — the user never taps first.
            viewModel.onHoldStart()
            runCurrent()
            assertEquals(RemoteButtonState.Preparing, viewModel.buttonState.value)
            // The machine finishes arming while the finger is still down, well
            // before the hold completes, so the confirm always lands on an
            // armed machine and nothing is visible to the user in between.
            advanceTimeBy(PREPARING_DELAY_MILLIS + 1)
            runCurrent()
            assertEquals(RemoteButtonState.AwaitingConfirmation, viewModel.buttonState.value)
            assertEquals(0, remoteButtonRepository.pushCount)
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS)
            runCurrent()
            assertEquals(1, remoteButtonRepository.pushCount)
        }

    @Test
    fun releaseAfterCompletedHoldDoesNotResetTheMachine() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            // The finger is still down when the press fires; lifting it must
            // not be mistaken for an abort and cancel the in-flight press.
            completeHold(viewModel)
            assertEquals(1, remoteButtonRepository.pushCount)
            assertEquals(RemoteButtonState.SendingToDoor, viewModel.buttonState.value)
        }

    // --- Nothing else submits ---

    @Test
    fun holdWhileSignedOutDoesNothing() =
        runTest {
            val viewModel = createViewModel()
            completeHold(viewModel)
            assertEquals(RemoteButtonState.Ready, viewModel.buttonState.value)
            assertEquals(0, remoteButtonRepository.pushCount)
            assertFalse(viewModel.isHolding.value)
        }

    @Test
    fun abortedHoldDoesNotSubmitAndReturnsToReady() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            viewModel.onHoldStart()
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS / 2)
            viewModel.onHoldEnd()
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS)
            runCurrent()
            assertEquals(0, remoteButtonRepository.pushCount)
            assertFalse(viewModel.isHolding.value)
            // No lingering half-armed state: the next hold starts from scratch.
            assertEquals(RemoteButtonState.Ready, viewModel.buttonState.value)
        }

    @Test
    fun abortedHoldOneMillisecondEarlyDoesNotSubmit() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            viewModel.onHoldStart()
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS - 1)
            runCurrent()
            viewModel.onHoldEnd()
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS)
            runCurrent()
            assertEquals(0, remoteButtonRepository.pushCount)
            assertEquals(RemoteButtonState.Ready, viewModel.buttonState.value)
        }

    @Test
    fun repeatedAbortedHoldsNeverSubmit() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            // Fidgeting on the watch face: many partial holds, none completed.
            repeat(5) {
                viewModel.onHoldStart()
                advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS - 100L)
                viewModel.onHoldEnd()
                runCurrent()
                assertEquals(RemoteButtonState.Ready, viewModel.buttonState.value)
            }
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS * 4)
            runCurrent()
            assertEquals(0, remoteButtonRepository.pushCount)
        }

    @Test
    fun holdWhilePressInFlightDoesNotSubmitAgain() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            completeHold(viewModel)
            assertEquals(RemoteButtonState.SendingToDoor, viewModel.buttonState.value)
            // A second full hold landing while the first press is still in
            // flight must not queue another real button press.
            completeHold(viewModel)
            assertEquals(1, remoteButtonRepository.pushCount)
            assertFalse(viewModel.isHolding.value)
        }

    @Test
    fun holdWhileTerminalResultOnScreenDoesNotSubmit() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            remoteButtonRepository.setPushSucceeds(false)
            completeHold(viewModel)
            assertEquals(RemoteButtonState.ServerFailed, viewModel.buttonState.value)
            // Touching the door while the failure message is still showing
            // must not fire a retry the user did not ask for.
            completeHold(viewModel)
            assertEquals(1, remoteButtonRepository.pushCount)
        }

    // --- Request lifecycle ---

    @Test
    fun doorMovementAfterSubmitSucceeds() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            completeHold(viewModel)
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
            completeHold(viewModel)
            assertEquals(1, remoteButtonRepository.pushCount)
            assertEquals(RemoteButtonState.ServerFailed, viewModel.buttonState.value)
        }

    @Test
    fun doorResponseGraceOutlastsSharedDefault() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            completeHold(viewModel)
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

    // --- Haptics ---
    //
    // A buzz cannot be asserted from the command line, but the decision to
    // buzz can, and that is where the logic lives. These pin the sequence.

    @Test
    fun completedHoldEmitsEngagedThenHalfwayThenCommitted() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            val cues = recordCues(viewModel)
            viewModel.onHoldStart()
            runCurrent()
            assertEquals(listOf(HapticCue.HoldEngaged), cues)
            advanceTimeBy(WearHomeViewModel.HOLD_HALFWAY_MILLIS + 1)
            runCurrent()
            assertEquals(listOf(HapticCue.HoldEngaged, HapticCue.HoldHalfway), cues)
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS)
            runCurrent()
            assertEquals(
                listOf(HapticCue.HoldEngaged, HapticCue.HoldHalfway, HapticCue.PressCommitted),
                cues.take(3),
            )
        }

    @Test
    fun abortedHoldEmitsAbortedAndNeverCommitted() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            val cues = recordCues(viewModel)
            viewModel.onHoldStart()
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS - 1)
            runCurrent()
            viewModel.onHoldEnd()
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS)
            runCurrent()
            assertEquals(
                listOf(HapticCue.HoldEngaged, HapticCue.HoldHalfway, HapticCue.HoldAborted),
                cues,
            )
        }

    @Test
    fun abortBeforeHalfwayNeverEmitsHalfway() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            val cues = recordCues(viewModel)
            viewModel.onHoldStart()
            advanceTimeBy(WearHomeViewModel.HOLD_HALFWAY_MILLIS / 2)
            viewModel.onHoldEnd()
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS)
            runCurrent()
            assertEquals(listOf(HapticCue.HoldEngaged, HapticCue.HoldAborted), cues)
        }

    @Test
    fun signedOutHoldEmitsNothing() =
        runTest {
            val viewModel = createViewModel()
            val cues = recordCues(viewModel)
            completeHold(viewModel)
            assertEquals(emptyList<HapticCue>(), cues)
        }

    @Test
    fun doorMovedInResponseToOurPressEmitsSucceeded() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            val cues = recordCues(viewModel)
            completeHold(viewModel)
            doorRepository.setCurrentDoorEvent(
                DoorEvent(doorPosition = DoorPosition.OPENING, lastChangeTimeSeconds = 123L),
            )
            runCurrent()
            assertEquals(HapticCue.PressSucceeded, cues.last())
        }

    @Test
    fun doorMovedWithoutOurPressEmitsNothing() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            val cues = recordCues(viewModel)
            // Someone else opened the door. The watch must stay silent — a
            // buzz here would be a notification the user never asked for.
            doorRepository.setCurrentDoorEvent(
                DoorEvent(doorPosition = DoorPosition.OPENING, lastChangeTimeSeconds = 123L),
            )
            runCurrent()
            doorRepository.setCurrentDoorEvent(
                DoorEvent(doorPosition = DoorPosition.OPEN, lastChangeTimeSeconds = 140L),
            )
            runCurrent()
            assertEquals(emptyList<HapticCue>(), cues)
        }

    @Test
    fun failedPressEmitsFailed() =
        runTest {
            val viewModel = createViewModel()
            signIn()
            remoteButtonRepository.setPushSucceeds(false)
            val cues = recordCues(viewModel)
            completeHold(viewModel)
            assertEquals(RemoteButtonState.ServerFailed, viewModel.buttonState.value)
            assertEquals(HapticCue.PressFailed, cues.last())
        }

    // --- Polling and screen wake ---

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
            // An in-progress hold must NOT hold the screen awake: the user's
            // finger is already on the screen keeping it lit.
            viewModel.onHoldStart()
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS / 2)
            runCurrent()
            assertFalse(viewModel.keepScreenOn.value)
            // A submitted press does.
            advanceTimeBy(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS)
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

    // --- Sign-in ---

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
