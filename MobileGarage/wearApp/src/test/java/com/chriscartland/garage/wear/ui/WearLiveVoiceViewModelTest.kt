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
import com.chriscartland.garage.usecase.VoiceCommandController
import com.chriscartland.garage.usecase.VoiceCommandIgnoreReason
import com.chriscartland.garage.usecase.VoiceCommandState
import com.chriscartland.garage.usecase.VoiceDoorState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
     * Cancellable right up to the last moment before the ring completes.
     *
     * The deadline is the ring finishing, not some earlier point of no return
     * — the same promise the hold makes, where releasing cancels until the
     * sweep closes. Asserted one millisecond short of the window because that
     * is the boundary a refactor would quietly move; a cancel at the halfway
     * mark would keep passing while the last second silently stopped working.
     */
    @Test
    fun cancellingOneMillisecondBeforeTheRingClosesPressesNothing() =
        runTest {
            val viewModel = createViewModel(DoorPosition.CLOSED)
            speak(viewModel, "open the garage door")

            advanceTimeBy(WearVoiceViewModel.ARMED_WINDOW_MILLIS - 1)
            runCurrent()
            assertTrue(
                "Still cancellable: the ring has not closed yet.",
                viewModel.state.value is VoiceCommandState.Armed,
            )
            viewModel.onCancel()
            runCurrent()

            letTheWindowElapse()
            assertEquals(0, remoteButtonRepository.pushCount)
        }

    /**
     * The voice countdown takes exactly as long as the hold's, because both
     * rings make the same promise about the same door.
     *
     * Pinned from the LIVE surface as well as in `WearConfirmParityTest`: this
     * is the one where the number decides how long a real garage door press
     * can be called off, so it is worth failing twice.
     */
    @Test
    fun theCancelWindowMatchesTheHoldToConfirmDuration() =
        runTest {
            val viewModel = createViewModel(DoorPosition.CLOSED)
            speak(viewModel, "open the garage door")

            val armed = viewModel.state.value as VoiceCommandState.Armed
            assertEquals(WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS, armed.windowMs)
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

    // --- Keeping the screen awake -------------------------------------------

    /**
     * `Sending` is not guaranteed to be observable, so nothing may depend on
     * seeing it.
     *
     * `controller.state` is a StateFlow and therefore CONFLATES. When a press
     * resolves inside a single dispatch, `Sending` is overwritten by `Sent`
     * before any collector is scheduled — a probe subscriber here observes
     * exactly `Ready, Listening, Armed, Sent`, with no `Sending` at all. That
     * is asserted below rather than described, because it is the premise the
     * production code is written against.
     *
     * Over real HTTP the press takes far longer than a dispatch and `Sending`
     * does arrive, which is what makes this the sort of gap that would only
     * ever surface on a fast network — and never in a place anyone was looking.
     */
    @Test
    fun theWaitForTheDoorStartsEvenWhenSendingIsNeverObserved() =
        runTest {
            val viewModel = createViewModel(DoorPosition.CLOSED)
            val seen = mutableListOf<VoiceCommandState>()
            backgroundScope.launch { viewModel.state.collect { seen += it } }
            runCurrent()

            speak(viewModel, "open the garage door")
            letTheWindowElapse()

            assertEquals(
                "The premise: a fast press skips Sending entirely. If this ever " +
                    "starts failing, the conflation window changed and the Sent " +
                    "fallback in WearVoiceViewModel may no longer be load-bearing " +
                    "— check it still is before deleting it. Saw: $seen",
                emptyList<VoiceCommandState>(),
                seen.filterIsInstance<VoiceCommandState.Sending>(),
            )
            assertEquals(1, remoteButtonRepository.pushCount)
            assertTrue(
                "A press went out, so the door is being waited on — regardless of " +
                    "which state announced it.",
                viewModel.awaitingDoorReaction.value,
            )
        }

    /**
     * Voice is the one interaction here a wrist can conduct without touching
     * anything. Between the tap that opens the mic and the outcome there is no
     * contact to keep the display alive, and a watch that sleeps mid-utterance
     * takes the microphone with it.
     */
    @Test
    fun theScreenStaysAwakeFromTheFirstWordToTheOutcome() =
        runTest {
            val viewModel = createViewModel(DoorPosition.CLOSED)
            runCurrent()
            assertFalse("Nothing has started yet.", viewModel.keepScreenOn.value)

            viewModel.onMicTap()
            runCurrent()
            assertTrue("The mic is open and no finger is on the screen.", viewModel.keepScreenOn.value)

            viewModel.onTranscript("open the garage door")
            runCurrent()
            assertTrue("Counting down to a real press.", viewModel.keepScreenOn.value)

            letTheWindowElapse()
            assertTrue("Showing the outcome of that press.", viewModel.keepScreenOn.value)
        }

    /**
     * The screen outlives the command by a cooldown, because the moment
     * everything stops is the moment there is finally something to read.
     * Releasing exactly then would black out the answer.
     */
    @Test
    fun theScreenIsReleasedAfterACooldownRatherThanTheInstantItEnds() =
        runTest {
            val viewModel = createViewModel(DoorPosition.CLOSED)
            speak(viewModel, "is the garage door open")
            assertTrue(viewModel.state.value is VoiceCommandState.Ignored)
            assertTrue(viewModel.keepScreenOn.value)

            advanceTimeBy(VoiceCommandController.IGNORED_DISMISS_MS + 1)
            runCurrent()
            assertEquals(VoiceCommandState.Ready, viewModel.state.value)
            assertTrue(
                "The refusal has expired but the screen holds through the cooldown.",
                viewModel.keepScreenOn.value,
            )

            advanceTimeBy(WearVoiceViewModel.SCREEN_ON_COOLDOWN_MILLIS + 1)
            runCurrent()
            assertFalse("Cooldown over: let go.", viewModel.keepScreenOn.value)
        }

    /**
     * A press is not finished when the server acknowledges it — it is finished
     * when the door moves. That gap is a second or two of mechanism plus a poll,
     * and it is the part the user is actually waiting through.
     */
    @Test
    fun aSentPressKeepsTheScreenAwakeUntilTheDoorMoves() =
        runTest {
            val viewModel = createViewModel(DoorPosition.CLOSED)
            speak(viewModel, "open the garage door")
            letTheWindowElapse()
            assertEquals(1, remoteButtonRepository.pushCount)
            assertTrue(viewModel.awaitingDoorReaction.value)

            // The outcome expires. The controller is done; the interaction is not.
            advanceTimeBy(WearVoiceViewModel.RESULT_FLASH_MILLIS + 1)
            runCurrent()
            assertEquals(VoiceCommandState.Ready, viewModel.state.value)
            assertTrue(
                "Back at rest, but still waiting on the door it just told to move.",
                viewModel.keepScreenOn.value,
            )

            doorRepository.setCurrentDoorEvent(DoorEvent(doorPosition = DoorPosition.OPENING))
            runCurrent()
            assertFalse("The door answered: stop waiting.", viewModel.awaitingDoorReaction.value)
        }

    /**
     * A door that never moves — a relay that did not fire, an opener with no
     * power — ends the wait on a timeout rather than pinning the screen on.
     */
    @Test
    fun aDoorThatNeverMovesEndsTheWaitOnItsOwn() =
        runTest {
            val viewModel = createViewModel(DoorPosition.CLOSED)
            speak(viewModel, "open the garage door")
            letTheWindowElapse()
            assertTrue(viewModel.awaitingDoorReaction.value)

            advanceTimeBy(WearVoiceViewModel.DOOR_REACTION_TIMEOUT_MILLIS + 1)
            runCurrent()
            assertFalse(viewModel.awaitingDoorReaction.value)

            advanceTimeBy(WearVoiceViewModel.SCREEN_ON_COOLDOWN_MILLIS + 1)
            runCurrent()
            assertFalse(viewModel.keepScreenOn.value)
        }

    /** Nothing left, so there is no door reaction to wait for. */
    @Test
    fun aFailedPressDoesNotWaitForADoorThatWasNeverTold() =
        runTest {
            val viewModel = createViewModel(DoorPosition.CLOSED)
            remoteButtonRepository.setPushSucceeds(false)

            speak(viewModel, "open the garage door")
            letTheWindowElapse()

            assertTrue(viewModel.state.value is VoiceCommandState.Failed)
            assertFalse(viewModel.awaitingDoorReaction.value)
        }

    /**
     * `Listening` is the one phase that can genuinely hang — a recognizer that
     * never returns and never errors — and it is also the phase with a live
     * microphone. The cap is what stops it holding the display indefinitely.
     */
    @Test
    fun aHungRecognizerDoesNotHoldTheScreenForever() =
        runTest {
            val viewModel = createViewModel(DoorPosition.CLOSED)
            viewModel.onMicTap()
            runCurrent()
            assertTrue(viewModel.keepScreenOn.value)

            advanceTimeBy(WearVoiceViewModel.SCREEN_ON_CAP_MILLIS + 1)
            runCurrent()
            assertTrue("Still listening…", viewModel.state.value is VoiceCommandState.Listening)
            assertFalse("…but no longer holding the screen for it.", viewModel.keepScreenOn.value)
        }

    // --- Getting out of the way ---------------------------------------------

    /**
     * Once the door starts moving, this screen is a microphone sitting on top
     * of the animation the user asked to see.
     */
    @Test
    fun theDoorStartingToMoveAsksTheAppToLeaveTheVoiceScreen() =
        runTest {
            val viewModel = createViewModel(DoorPosition.CLOSED)
            runCurrent()
            var dismissals = 0
            backgroundScope.launch { viewModel.doorStartedMoving.collect { dismissals++ } }
            runCurrent()
            assertEquals(0, dismissals)

            doorRepository.setCurrentDoorEvent(DoorEvent(doorPosition = DoorPosition.OPENING))
            runCurrent()
            assertEquals(1, dismissals)

            // Settling is not a reason to go anywhere; only starting to move is.
            doorRepository.setCurrentDoorEvent(DoorEvent(doorPosition = DoorPosition.OPEN))
            runCurrent()
            assertEquals(1, dismissals)
        }

    /**
     * Arriving while the door is ALREADY moving must not bounce the user
     * straight back out — that would make the mic unreachable for the whole of
     * a transit, which is exactly when someone might want to reverse it.
     *
     * The second half is the positive control: without it, a `doorStartedMoving`
     * that never fired at all would satisfy the first half perfectly.
     */
    @Test
    fun arrivingWhileTheDoorIsAlreadyMovingDoesNotBounceStraightBackOut() =
        runTest {
            val viewModel = createViewModel(DoorPosition.OPENING)
            runCurrent()
            var dismissals = 0
            backgroundScope.launch { viewModel.doorStartedMoving.collect { dismissals++ } }
            runCurrent()
            assertEquals(
                "A door already moving on arrival is context, not news.",
                0,
                dismissals,
            )

            doorRepository.setCurrentDoorEvent(DoorEvent(doorPosition = DoorPosition.OPEN))
            runCurrent()
            doorRepository.setCurrentDoorEvent(DoorEvent(doorPosition = DoorPosition.CLOSING))
            runCurrent()
            assertEquals("A real transition still fires.", 1, dismissals)
        }
}
