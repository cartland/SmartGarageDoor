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

import com.chriscartland.garage.domain.model.VoiceIntent
import com.chriscartland.garage.testcommon.TestDispatcherProvider
import com.chriscartland.garage.usecase.ClassifyVoiceIntentUseCase
import com.chriscartland.garage.usecase.RuleBasedVoiceIntentClassifier
import com.chriscartland.garage.usecase.SimulatedVoiceCommandEnvironment
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the **simulated** Wear voice demo.
 *
 * Two things are under test, and the second matters more than the first:
 *
 *  1. The loop behaves — a confident imperative arms, counts down, and
 *     commits; everything else is refused with the reason the screen shows.
 *  2. **It can never operate the real garage door.** The demo runs the real
 *     shared controller, classifier and gate, so the only thing standing
 *     between it and a real press is which [VoiceCommandEnvironment] it was
 *     given. [cannotReachTheRealRemoteButton] pins that structurally over the
 *     constructor, and every behavioural test below additionally asserts on
 *     the *demo* door, which is the only door in reach.
 *
 * The DI half of the same property (that the Wear graph binds the simulated
 * environment and nothing else) lives in `WearComponentGraphTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearVoiceViewModelTest {
    private lateinit var environment: SimulatedVoiceCommandEnvironment

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(): WearVoiceViewModel {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        environment = SimulatedVoiceCommandEnvironment(backgroundScope)
        return WearVoiceViewModel(
            // The real classifier, not a stub: the point of the experiment is
            // to feel the production grammar's strictness on a watch.
            classifyVoiceIntent = ClassifyVoiceIntentUseCase(RuleBasedVoiceIntentClassifier()),
            environment = environment,
            dispatchers = TestDispatcherProvider(testDispatcher),
        )
    }

    /**
     * Start recording haptic cues. Must be called before the actions under
     * test: `hapticCues` is Channel-backed, so a single collector receives
     * each cue exactly once.
     */
    private fun TestScope.recordCues(viewModel: WearVoiceViewModel): List<HapticCue> {
        val cues = mutableListOf<HapticCue>()
        backgroundScope.launch { viewModel.hapticCues.collect { cues += it } }
        runCurrent()
        return cues
    }

    /** Tap the mic and hand the recognizer's answer back. */
    private fun TestScope.speak(
        viewModel: WearVoiceViewModel,
        transcript: String?,
    ) {
        viewModel.onMicTap()
        runCurrent()
        viewModel.onTranscript(transcript)
        runCurrent()
    }

    // --- The safety property -------------------------------------------------

    /**
     * The watch voice surface is an experiment, so it must have no way to
     * reach the remote button. Asserted over the constructor rather than by
     * observing behaviour, because behaviour only proves the paths a test
     * happens to walk — this fails the moment someone *wires in* a real-door
     * dependency, which is the actual regression to guard.
     */
    @Test
    fun cannotReachTheRealRemoteButton() {
        val forbidden = setOf(
            "PushRemoteButtonUseCase",
            "RemoteButtonRepository",
            "NetworkButtonDataSource",
            "ButtonStateMachine",
            "WearHomeViewModel",
        )
        val dependencies = WearVoiceViewModel::class.java.constructors
            .flatMap { it.parameterTypes.asList() }
            .map { it.simpleName }
            .toSet()
        val violations = dependencies intersect forbidden
        assertTrue(
            "WearVoiceViewModel must not depend on $violations. The watch voice " +
                "surface is simulated; the remote button stays reachable only by " +
                "holding the door on the hero screen.",
            violations.isEmpty(),
        )
    }

    // --- The loop ------------------------------------------------------------

    @Test
    fun aConfidentOpenCommandArmsAgainstTheDemoDoor() =
        runTest {
            val viewModel = createViewModel()
            speak(viewModel, "open the garage door")

            val armed = viewModel.state.value as VoiceCommandState.Armed
            assertEquals(VoiceIntent.OPEN, armed.intent)
            assertEquals("open the garage door", armed.transcript)
            assertEquals(WearVoiceViewModel.ARMED_WINDOW_MILLIS, armed.windowMs)
        }

    @Test
    fun lettingTheWindowElapseMovesOnlyTheDemoDoor() =
        runTest {
            val viewModel = createViewModel()
            speak(viewModel, "open the garage door")

            advanceTimeBy(WearVoiceViewModel.ARMED_WINDOW_MILLIS + 1)
            runCurrent()
            assertTrue(viewModel.state.value is VoiceCommandState.Sending)

            advanceTimeBy(SimulatedVoiceCommandEnvironment.PRESS_DELAY_MS + 1)
            runCurrent()
            assertTrue(viewModel.state.value is VoiceCommandState.Sent)
            assertEquals(VoiceDoorState.MOVING, viewModel.demoDoorState.value)

            advanceTimeBy(SimulatedVoiceCommandEnvironment.TRANSIT_MS + 1)
            runCurrent()
            assertEquals(VoiceDoorState.OPEN, viewModel.demoDoorState.value)
        }

    @Test
    fun theDemoDoorGatesTheSecondIdenticalCommand() =
        runTest {
            val viewModel = createViewModel()
            speak(viewModel, "open the garage door")
            // Explicit rather than advanceUntilIdle(): the fake transit runs in
            // backgroundScope, which advanceUntilIdle() does not treat as work to
            // wait for, so it would stop the clock with the door still MOVING.
            advanceTimeBy(
                WearVoiceViewModel.ARMED_WINDOW_MILLIS +
                    SimulatedVoiceCommandEnvironment.PRESS_DELAY_MS +
                    SimulatedVoiceCommandEnvironment.TRANSIT_MS + 1,
            )
            runCurrent()
            assertEquals(VoiceDoorState.OPEN, viewModel.demoDoorState.value)

            // Same words, different world: the gate now refuses.
            speak(viewModel, "open the garage door")
            val ignored = viewModel.state.value as VoiceCommandState.Ignored
            assertEquals(VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN, ignored.reason)
        }

    @Test
    fun closingAnAlreadyClosedDemoDoorIsRefused() =
        runTest {
            val viewModel = createViewModel()
            speak(viewModel, "close the garage door")

            val ignored = viewModel.state.value as VoiceCommandState.Ignored
            assertEquals(VoiceCommandIgnoreReason.DOOR_ALREADY_CLOSED, ignored.reason)
        }

    @Test
    fun aMovingDemoDoorRefusesBothDirections() =
        runTest {
            val viewModel = createViewModel()
            environment.setDoorState(VoiceDoorState.MOVING)

            speak(viewModel, "open the garage door")
            assertEquals(
                VoiceCommandIgnoreReason.DOOR_MOVING,
                (viewModel.state.value as VoiceCommandState.Ignored).reason,
            )

            speak(viewModel, "close the garage door")
            assertEquals(
                VoiceCommandIgnoreReason.DOOR_MOVING,
                (viewModel.state.value as VoiceCommandState.Ignored).reason,
            )
        }

    @Test
    fun anUnknownDemoDoorStateRefusesRatherThanGuesses() =
        runTest {
            val viewModel = createViewModel()
            environment.setDoorState(VoiceDoorState.UNKNOWN)

            speak(viewModel, "open the garage door")
            assertEquals(
                VoiceCommandIgnoreReason.DOOR_STATE_UNKNOWN,
                (viewModel.state.value as VoiceCommandState.Ignored).reason,
            )
        }

    @Test
    fun aNegatedCommandIsNotACommand() =
        runTest {
            val viewModel = createViewModel()
            speak(viewModel, "don't open the door")

            val ignored = viewModel.state.value as VoiceCommandState.Ignored
            assertEquals(VoiceCommandIgnoreReason.NOT_A_COMMAND, ignored.reason)
            assertEquals(VoiceDoorState.CLOSED, viewModel.demoDoorState.value)
        }

    @Test
    fun aQuestionIsHeardButNotActedOn() =
        runTest {
            val viewModel = createViewModel()
            speak(viewModel, "can you open the door")

            val ignored = viewModel.state.value as VoiceCommandState.Ignored
            assertEquals(VoiceCommandIgnoreReason.NOT_CONFIDENT, ignored.reason)
            assertEquals(VoiceDoorState.CLOSED, viewModel.demoDoorState.value)
        }

    @Test
    fun silenceIsReportedAsNoSpeech() =
        runTest {
            val viewModel = createViewModel()
            speak(viewModel, null)

            assertEquals(
                VoiceCommandIgnoreReason.NO_SPEECH,
                (viewModel.state.value as VoiceCommandState.Ignored).reason,
            )
        }

    @Test
    fun aWatchWithNoRecognizerSaysSo() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onMicTap()
            runCurrent()
            viewModel.onCaptureUnavailable()
            runCurrent()

            assertEquals(
                VoiceCommandIgnoreReason.RECOGNIZER_UNAVAILABLE,
                (viewModel.state.value as VoiceCommandState.Ignored).reason,
            )
        }

    // --- Getting out of it ---------------------------------------------------

    @Test
    fun tappingDuringTheWindowCancelsAndReListens() =
        runTest {
            val viewModel = createViewModel()
            speak(viewModel, "open the garage door")
            assertTrue(viewModel.state.value is VoiceCommandState.Armed)

            viewModel.onMicTap()
            runCurrent()

            assertEquals(2, (viewModel.state.value as VoiceCommandState.Listening).attempt)
            advanceUntilIdle()
            assertEquals(VoiceDoorState.CLOSED, viewModel.demoDoorState.value)
        }

    @Test
    fun backgroundingTheAppCancelsAPendingCommand() =
        runTest {
            val viewModel = createViewModel()
            speak(viewModel, "open the garage door")
            assertTrue(viewModel.state.value is VoiceCommandState.Armed)

            viewModel.onBackgrounded()
            runCurrent()

            assertEquals(VoiceCommandState.Ready, viewModel.state.value)
            // The real assertion: the window's commit never ran.
            advanceUntilIdle()
            assertEquals(VoiceDoorState.CLOSED, viewModel.demoDoorState.value)
        }

    /**
     * Swiping back out of the demo is **not** a lifecycle stop — the app stays
     * perfectly foreground — so [WearVoiceViewModel.onBackgrounded] never fires
     * for it and nothing else would stop the countdown.
     *
     * Until `onScreenLeft` existed, leaving mid-countdown left the demo running
     * behind the hero screen: it committed off-screen, moved the demo door, and
     * buzzed the wrist for a command the user had already walked away from.
     * Asserting all three is the point — state alone would have passed while
     * the door still moved.
     */
    @Test
    fun poppingTheScreenMidCountdownCommitsNothing() =
        runTest {
            val viewModel = createViewModel()
            val cues = recordCues(viewModel)
            speak(viewModel, "open the garage door")
            assertTrue(viewModel.state.value is VoiceCommandState.Armed)

            viewModel.onScreenLeft()
            runCurrent()
            assertEquals(VoiceCommandState.Ready, viewModel.state.value)

            // Well past the point the commit was scheduled for.
            advanceTimeBy(WearVoiceViewModel.ARMED_WINDOW_MILLIS * 2)
            runCurrent()
            assertEquals(VoiceDoorState.CLOSED, viewModel.demoDoorState.value)
            assertEquals(listOf(HapticCue.VoiceArmed), cues)
        }

    /**
     * The microphone must not outlive the screen that opened it. Also covers
     * the race it creates: the recognizer's callback can land after the pop,
     * and must not resurrect a session the user has left.
     */
    @Test
    fun poppingTheScreenStopsListening() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onMicTap()
            runCurrent()
            assertTrue(viewModel.state.value is VoiceCommandState.Listening)

            viewModel.onScreenLeft()
            runCurrent()
            assertEquals(VoiceCommandState.Ready, viewModel.state.value)

            viewModel.onTranscript("open the garage door")
            runCurrent()
            assertEquals(VoiceCommandState.Ready, viewModel.state.value)
        }

    /**
     * "Nothing was sent" is the entire message of the demo, and it arrives with
     * a second line explaining that the demo door reacts instead. The shared
     * 1.5s default is sized for a receipt on the real button; two lines of new
     * information on a wrist is not a 1.5-second read.
     */
    @Test
    fun theOutcomeStaysUpLongEnoughToRead() =
        runTest {
            val viewModel = createViewModel()
            speak(viewModel, "open the garage door")
            advanceTimeBy(WearVoiceViewModel.ARMED_WINDOW_MILLIS + 1)
            runCurrent()
            advanceTimeBy(SimulatedVoiceCommandEnvironment.PRESS_DELAY_MS + 1)
            runCurrent()
            assertTrue(viewModel.state.value is VoiceCommandState.Sent)

            // The moment the shared default would have cleared it.
            advanceTimeBy(VoiceCommandController.RESULT_FLASH_MS)
            runCurrent()
            assertTrue(
                "The demo's punchline must outlast the real button's receipt",
                viewModel.state.value is VoiceCommandState.Sent,
            )

            advanceTimeBy(
                WearVoiceViewModel.RESULT_FLASH_MILLIS - VoiceCommandController.RESULT_FLASH_MS,
            )
            runCurrent()
            assertEquals(VoiceCommandState.Ready, viewModel.state.value)
        }

    /**
     * A buzz is only useful at the instant it describes.
     *
     * These cues were Channel-backed, which *queues* for an absent collector —
     * so cues emitted while no screen was subscribed were saved and replayed in
     * a burst when one came back. Combined with a demo that used to keep
     * running after being swiped away, returning to the screen buzzed twice for
     * a command abandoned seconds earlier. Dropping is the right failure mode:
     * a missed buzz is nothing, a late one is a lie.
     */
    @Test
    fun cuesAreNotQueuedWhileNothingIsWatching() =
        runTest {
            val viewModel = createViewModel()

            // No collector at all: this arms and then commits.
            speak(viewModel, "open the garage door")
            advanceTimeBy(WearVoiceViewModel.ARMED_WINDOW_MILLIS + 1)
            runCurrent()

            val cues = recordCues(viewModel)
            advanceTimeBy(SimulatedVoiceCommandEnvironment.PRESS_DELAY_MS + 1)
            runCurrent()

            assertEquals(emptyList<HapticCue>(), cues)
        }

    @Test
    fun aDemoDoorThatMovesDuringTheWindowAbortsTheCommit() =
        runTest {
            val viewModel = createViewModel()
            speak(viewModel, "open the garage door")

            // Someone opened the (demo) door while the countdown ran.
            environment.setDoorState(VoiceDoorState.OPEN)
            advanceTimeBy(WearVoiceViewModel.ARMED_WINDOW_MILLIS + 1)
            runCurrent()

            // Reported as "changed", not "already open": the controller collapses
            // every second-gate failure into one reason, and "the world moved
            // under you" is the more accurate thing to tell someone who watched
            // the countdown run.
            assertEquals(
                VoiceCommandIgnoreReason.DOOR_STATE_CHANGED,
                (viewModel.state.value as VoiceCommandState.Ignored).reason,
            )
            // And nothing was pressed, not even in the simulation.
            assertEquals(VoiceDoorState.OPEN, viewModel.demoDoorState.value)
        }

    // --- Cancelling --------------------------------------------------------
    //
    // One rule: a tap starts what is not running and stops what is. On the
    // watch the whole screen is the tap target, so cancel must mean stop —
    // not the phone's cancel-and-re-listen, which would open a live mic from
    // a brush during the countdown.

    @Test
    fun tappingWhileListeningStopsInsteadOfDoingNothing() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onMicTap()
            runCurrent()
            assertTrue(viewModel.state.value is VoiceCommandState.Listening)

            viewModel.onCancel()
            runCurrent()

            assertEquals(VoiceCommandState.Ready, viewModel.state.value)
        }

    @Test
    fun cancellingTheCountdownReturnsToReadyWithoutReListening() =
        runTest {
            val viewModel = createViewModel()
            speak(viewModel, "open the garage door")
            assertTrue(viewModel.state.value is VoiceCommandState.Armed)

            viewModel.onCancel()
            runCurrent()

            assertEquals(VoiceCommandState.Ready, viewModel.state.value)
            // And the countdown it interrupted never reaches the demo door.
            advanceTimeBy(WearVoiceViewModel.ARMED_WINDOW_MILLIS * 2)
            runCurrent()
            assertEquals(VoiceDoorState.CLOSED, viewModel.demoDoorState.value)
        }

    @Test
    fun cancellingClearsAnyPartialText() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onMicTap()
            runCurrent()
            viewModel.onPartialTranscript("open the gar")
            runCurrent()

            viewModel.onCancel()
            runCurrent()

            assertEquals(null, viewModel.partialTranscript.value)
        }

    /** Cancelling is a silent, deliberate act — it is not a refusal. */
    @Test
    fun cancellingDoesNotBuzz() =
        runTest {
            val viewModel = createViewModel()
            val cues = recordCues(viewModel)

            speak(viewModel, "open the garage door")
            viewModel.onCancel()
            runCurrent()
            advanceUntilIdle()

            assertEquals(listOf(HapticCue.VoiceArmed), cues)
        }

    // --- Live partial text (in-app capture only) -----------------------------

    @Test
    fun partialTextIsShownWhileListening() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onMicTap()
            runCurrent()

            viewModel.onPartialTranscript("open the")
            runCurrent()
            assertEquals("open the", viewModel.partialTranscript.value)

            viewModel.onPartialTranscript("open the garage")
            runCurrent()
            assertEquals("open the garage", viewModel.partialTranscript.value)
        }

    @Test
    fun partialTextIsClearedWhenTheAttemptEnds() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onMicTap()
            runCurrent()
            viewModel.onPartialTranscript("open the garage")
            runCurrent()

            viewModel.onTranscript("open the garage door")
            runCurrent()

            // The outcome replaces it; stale interim text must not linger under
            // "Would open the door".
            assertEquals(null, viewModel.partialTranscript.value)
        }

    /**
     * Recognizers can deliver a partial after the attempt is over. Accepting
     * it would paint interim text from an abandoned capture over the outcome
     * of whatever is on screen now.
     */
    @Test
    fun partialTextArrivingAfterTheAttemptIsIgnored() =
        runTest {
            val viewModel = createViewModel()
            speak(viewModel, "open the garage door")
            assertTrue(viewModel.state.value is VoiceCommandState.Armed)

            viewModel.onPartialTranscript("late straggler")
            runCurrent()

            assertEquals(null, viewModel.partialTranscript.value)
        }

    // --- Haptics -------------------------------------------------------------

    /**
     * Pins the user-visible property (arriving on the screen is silent), not
     * the mechanism: it is the exhaustive `when` over states, rather than the
     * `drop(1)`, that makes it true today. Removing the drop leaves this test
     * green, which is correct — the drop is defence against a future shared
     * controller replaying a non-Ready state, not the reason for the silence.
     */
    @Test
    fun openingTheScreenDoesNotBuzz() =
        runTest {
            val viewModel = createViewModel()
            val cues = recordCues(viewModel)

            runCurrent()

            assertEquals(emptyList<HapticCue>(), cues)
        }

    @Test
    fun aCommittedCommandBuzzesWhenArmedHalfwayAndAtTheCommitInstant() =
        runTest {
            val viewModel = createViewModel()
            val cues = recordCues(viewModel)

            speak(viewModel, "open the garage door")
            assertEquals(listOf(HapticCue.VoiceArmed), cues)

            advanceTimeBy(WearVoiceViewModel.ARMED_WINDOW_MILLIS + 1)
            runCurrent()
            assertEquals(
                listOf(HapticCue.VoiceArmed, HapticCue.VoiceHalfway, HapticCue.VoiceCommitted),
                cues,
            )
        }

    /**
     * The countdown ring travels the bezel exactly like the hero screen's hold
     * ring, so it has to feel the same on the way round. It used to have only
     * its two endpoints, which left the longer of the two journeys (3s, against
     * the hold's 2s) with the least to go on.
     *
     * Asserted at the midpoint rather than only at the end, because "the cue
     * fires" and "the cue fires *when the ring is halfway*" are different
     * claims and only the second one is the feature.
     */
    @Test
    fun theCancelWindowTicksAtItsMidpointLikeTheHold() =
        runTest {
            val viewModel = createViewModel()
            val cues = recordCues(viewModel)

            speak(viewModel, "open the garage door")

            // Just before halfway: still only the arming cue.
            advanceTimeBy(WearVoiceViewModel.ARMED_WINDOW_MILLIS / 2 - 1)
            runCurrent()
            assertEquals(listOf(HapticCue.VoiceArmed), cues)

            advanceTimeBy(2)
            runCurrent()
            assertEquals(listOf(HapticCue.VoiceArmed, HapticCue.VoiceHalfway), cues)
        }

    /**
     * Cancelling before the midpoint must take the pending tick with it — a
     * pacing cue for a countdown that is no longer running would be worse than
     * never having one at all.
     */
    @Test
    fun cancellingBeforeTheMidpointKillsThePendingTick() =
        runTest {
            val viewModel = createViewModel()
            val cues = recordCues(viewModel)

            speak(viewModel, "open the garage door")
            viewModel.onCancel()
            runCurrent()

            advanceTimeBy(WearVoiceViewModel.ARMED_WINDOW_MILLIS * 2)
            runCurrent()
            assertEquals(listOf(HapticCue.VoiceArmed), cues)
        }

    @Test
    fun aRefusalBuzzesOnceAndOnlyOnce() =
        runTest {
            val viewModel = createViewModel()
            val cues = recordCues(viewModel)

            speak(viewModel, "don't open the door")
            advanceUntilIdle()

            assertEquals(listOf(HapticCue.VoiceRefused), cues)
        }

    @Test
    fun aCancelledCommandNeverBuzzesTheCommitCue() =
        runTest {
            val viewModel = createViewModel()
            val cues = recordCues(viewModel)

            speak(viewModel, "open the garage door")
            viewModel.onBackgrounded()
            runCurrent()
            advanceUntilIdle()

            assertEquals(listOf(HapticCue.VoiceArmed), cues)
        }
}
