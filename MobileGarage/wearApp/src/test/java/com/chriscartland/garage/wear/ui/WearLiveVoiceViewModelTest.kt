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
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.domain.model.VoiceIntent
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import com.chriscartland.garage.testcommon.FakeRemoteButtonRepository
import com.chriscartland.garage.testcommon.TestDispatcherProvider
import com.chriscartland.garage.usecase.ClassifyVoiceIntentUseCase
import com.chriscartland.garage.usecase.ObserveDoorEventsUseCase
import com.chriscartland.garage.usecase.PushRemoteButtonUseCase
import com.chriscartland.garage.usecase.RuleBasedVoiceIntentClassifier
import com.chriscartland.garage.usecase.VoiceCommandIgnoreReason
import com.chriscartland.garage.usecase.VoiceCommandState
import com.chriscartland.garage.usecase.VoiceDoorState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the **live** Wear voice surface — the one that presses the
 * real garage button.
 *
 * Everything here is about a single question: **under exactly which conditions
 * does a spoken sentence reach [FakeRemoteButtonRepository.pushCount]?** The
 * sibling `WearSimulatedVoiceViewModelTest` already covers the loop's shape
 * (they share every line of it), so this file does not re-walk the state
 * machine. It walks the gates, and each test names the one it is holding open
 * or closed.
 *
 * The distinction matters because the failure modes are asymmetric. A
 * false negative is a command that does not open the door and the user says it
 * again. A false positive is a garage door that opens because a watch
 * misheard, which is why the refusals below are asserted as precisely as the
 * successes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearLiveVoiceViewModelTest {
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var doorRepository: FakeDoorRepository
    private lateinit var remoteButtonRepository: FakeRemoteButtonRepository

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * @param doorPosition the real door the gate will read. Set BEFORE the
     *   ViewModel is built, because the projection seeds itself synchronously
     *   from the repository's cached value — which is the behaviour that makes
     *   a first utterance work rather than being refused as UNKNOWN.
     */
    private fun TestScope.createViewModel(doorPosition: DoorPosition = DoorPosition.CLOSED): WearLiveVoiceViewModel {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        authRepository = FakeAuthRepository()
        doorRepository = FakeDoorRepository()
        remoteButtonRepository = FakeRemoteButtonRepository()
        doorRepository.setCurrentDoorEvent(DoorEvent(doorPosition = doorPosition))
        signIn()
        return WearLiveVoiceViewModel(
            // The real classifier, not a stub. A stub would let this file pass
            // while the grammar that actually decides what reaches the garage
            // was broken.
            classifyVoiceIntent = ClassifyVoiceIntentUseCase(RuleBasedVoiceIntentClassifier()),
            observeDoorEvents = ObserveDoorEventsUseCase(doorRepository),
            pushRemoteButton = PushRemoteButtonUseCase(authRepository, remoteButtonRepository),
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

    /** Tap the mic and hand the recognizer's answer back. */
    private fun TestScope.speak(
        viewModel: WearLiveVoiceViewModel,
        transcript: String?,
    ) {
        viewModel.onMicTap()
        runCurrent()
        viewModel.onTranscript(transcript)
        runCurrent()
    }

    /** Let the cancel window elapse, which is what commits a command. */
    private fun TestScope.letTheWindowElapse() {
        advanceTimeBy(WearVoiceViewModel.ARMED_WINDOW_MILLIS + 1)
        runCurrent()
    }

    // --- The door really moves ----------------------------------------------

    @Test
    fun aConfidentCommandPressesTheRealButton() =
        runTest {
            val viewModel = createViewModel(DoorPosition.CLOSED)
            speak(viewModel, "open the garage door")

            val armed = viewModel.state.value as VoiceCommandState.Armed
            assertEquals(VoiceIntent.OPEN, armed.intent)
            // Armed is not sent: the whole point of the window.
            assertEquals(0, remoteButtonRepository.pushCount)

            letTheWindowElapse()
            assertEquals(1, remoteButtonRepository.pushCount)
            assertTrue(viewModel.state.value is VoiceCommandState.Sent)
        }

    /**
     * The three seconds are load-bearing, not decorative: this is the only
     * thing standing between a misheard sentence and a garage door.
     */
    @Test
    fun cancellingInsideTheWindowPressesNothing() =
        runTest {
            val viewModel = createViewModel(DoorPosition.CLOSED)
            speak(viewModel, "open the garage door")

            advanceTimeBy(WearVoiceViewModel.ARMED_WINDOW_MILLIS / 2)
            runCurrent()
            viewModel.onCancel()
            runCurrent()

            letTheWindowElapse()
            assertEquals(0, remoteButtonRepository.pushCount)
        }

    /**
     * Walking away from the screen is a cancellation too. Without this, a
     * countdown would keep running behind the door screen and press the button
     * for a command the user had abandoned — off-screen, which is the worst
     * place for a real press to happen.
     */
    @Test
    fun leavingTheScreenPressesNothing() =
        runTest {
            val viewModel = createViewModel(DoorPosition.CLOSED)
            speak(viewModel, "open the garage door")

            viewModel.onScreenLeft()
            runCurrent()

            letTheWindowElapse()
            assertEquals(0, remoteButtonRepository.pushCount)
        }

    /** Backgrounding the app, same rule, different trigger. */
    @Test
    fun backgroundingPressesNothing() =
        runTest {
            val viewModel = createViewModel(DoorPosition.CLOSED)
            speak(viewModel, "open the garage door")

            viewModel.onBackgrounded()
            runCurrent()

            letTheWindowElapse()
            assertEquals(0, remoteButtonRepository.pushCount)
        }

    // --- The gates ----------------------------------------------------------

    /**
     * The projection is seeded synchronously, so the FIRST utterance is gated
     * against the door we already know about.
     *
     * This is the regression that a cold `Flow` would cause: an "already open"
     * refusal is correct here, whereas UNKNOWN would be the projection
     * admitting it had not caught up yet — and the user, looking at a screen
     * one swipe from a door plainly labelled Open, would have no way to tell
     * those two apart.
     */
    @Test
    fun theRealDoorGatesTheFirstUtterance() =
        runTest {
            val viewModel = createViewModel(DoorPosition.OPEN)
            assertEquals(VoiceDoorState.OPEN, viewModel.doorState.value)

            speak(viewModel, "open the garage door")

            val ignored = viewModel.state.value as VoiceCommandState.Ignored
            assertEquals(VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN, ignored.reason)
            assertEquals(0, remoteButtonRepository.pushCount)
        }

    @Test
    fun aMovingDoorRefusesEveryDirection() =
        runTest {
            val viewModel = createViewModel(DoorPosition.OPENING)
            assertEquals(VoiceDoorState.MOVING, viewModel.doorState.value)

            speak(viewModel, "close the garage door")

            val ignored = viewModel.state.value as VoiceCommandState.Ignored
            assertEquals(VoiceCommandIgnoreReason.DOOR_MOVING, ignored.reason)
            assertEquals(0, remoteButtonRepository.pushCount)
        }

    /**
     * Deny-by-default. A door stuck mid-transit projects to UNKNOWN, and
     * UNKNOWN refuses BOTH directions — the wrong-direction hazard is exactly
     * why: if the cached position is wrong, "open" may really close.
     */
    @Test
    fun anAnomalousDoorRefusesEveryDirection() =
        runTest {
            val viewModel = createViewModel(DoorPosition.OPENING_TOO_LONG)
            assertEquals(VoiceDoorState.UNKNOWN, viewModel.doorState.value)

            speak(viewModel, "open the garage door")

            val ignored = viewModel.state.value as VoiceCommandState.Ignored
            assertEquals(VoiceCommandIgnoreReason.DOOR_STATE_UNKNOWN, ignored.reason)
            assertEquals(0, remoteButtonRepository.pushCount)
        }

    /**
     * The gate is checked TWICE — once to arm, once to commit — so a door that
     * moves during the countdown cancels the press rather than completing it.
     *
     * The realistic version of this is someone pressing the wall button while
     * the watch is counting down.
     */
    @Test
    fun aDoorThatMovesDuringTheWindowCancelsThePress() =
        runTest {
            val viewModel = createViewModel(DoorPosition.CLOSED)
            speak(viewModel, "open the garage door")
            assertTrue(viewModel.state.value is VoiceCommandState.Armed)

            doorRepository.setCurrentDoorEvent(DoorEvent(doorPosition = DoorPosition.OPEN))
            runCurrent()

            letTheWindowElapse()
            val ignored = viewModel.state.value as VoiceCommandState.Ignored
            assertEquals(VoiceCommandIgnoreReason.DOOR_STATE_CHANGED, ignored.reason)
            assertEquals(0, remoteButtonRepository.pushCount)
        }

    /**
     * Speech that is not an unambiguous imperative never arms, so it can never
     * reach the window that would commit it. "Is the garage door open" is the
     * canonical case: it contains every keyword and means the opposite of a
     * command.
     */
    @Test
    fun aSentenceAboutTheDoorIsNotACommand() =
        runTest {
            val viewModel = createViewModel(DoorPosition.CLOSED)
            speak(viewModel, "is the garage door open")

            assertTrue(viewModel.state.value is VoiceCommandState.Ignored)
            letTheWindowElapse()
            assertEquals(0, remoteButtonRepository.pushCount)
        }

    /**
     * Signed out, nothing is sent — and the surface says so rather than
     * appearing to work. [PushRemoteButtonUseCase] refuses before touching the
     * network, so the repository is never even called.
     */
    @Test
    fun aSignedOutWatchPressesNothing() =
        runTest {
            val viewModel = createViewModel(DoorPosition.CLOSED)
            authRepository.setAuthState(AuthState.Unauthenticated)
            runCurrent()

            speak(viewModel, "open the garage door")
            letTheWindowElapse()

            assertEquals(0, remoteButtonRepository.pushCount)
            assertTrue(viewModel.state.value is VoiceCommandState.Failed)
        }

    /** A server that refuses is reported as a failure, not as a success. */
    @Test
    fun aRejectedPressIsReportedAsFailed() =
        runTest {
            val viewModel = createViewModel(DoorPosition.CLOSED)
            remoteButtonRepository.setPushSucceeds(false)

            speak(viewModel, "open the garage door")
            letTheWindowElapse()

            assertEquals(1, remoteButtonRepository.pushCount)
            assertTrue(viewModel.state.value is VoiceCommandState.Failed)
        }

    /**
     * Server logs can tell a spoken press from a held one.
     *
     * Not cosmetic: the two surfaces have different risk profiles, so "did
     * voice do this?" has to be answerable after the fact from the server side
     * alone.
     */
    @Test
    fun theAckTokenMarksThePressAsVoice() =
        runTest {
            val viewModel = createViewModel(DoorPosition.CLOSED)
            speak(viewModel, "open the garage door")
            letTheWindowElapse()

            val token = remoteButtonRepository.pushCalls.single().buttonAckToken
            assertTrue(
                "Expected the ack token to carry the voice marker, got: $token",
                token.contains("wear-test-voice"),
            )
        }
}
