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
 * Unit tests for the **simulated** Wear voice surface.
 *
 * Two things are under test, and the second matters more than the first:
 *
 *  1. The loop behaves — a confident imperative arms, counts down, and
 *     commits; everything else is refused with the reason the screen shows.
 *  2. **It can never operate the real garage door.** It runs the same shared
 *     controller, classifier and gate the LIVE surface does, so the only thing
 *     standing between it and a real press is the environment it builds.
 *     [cannotReachTheRealRemoteButton] pins that structurally over the
 *     constructor, and every behavioural test below additionally asserts on
 *     the *demo* door, which is the only door in reach.
 *
 * This matters more now than it did when the watch had no live voice at all:
 * a sibling class one file away does press the real button, so "which one am I
 * looking at" is a question the type system has to answer rather than a reader.
 *
 * The DI half of the same property lives in `WearComponentGraphTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearSimulatedVoiceViewModelTest {
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(): WearSimulatedVoiceViewModel {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        return WearSimulatedVoiceViewModel(
            // The real classifier, not a stub: the point is to feel the
            // production grammar's strictness on a watch.
            classifyVoiceIntent = ClassifyVoiceIntentUseCase(RuleBasedVoiceIntentClassifier()),
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
        viewModel: WearSimulatedVoiceViewModel,
        transcript: String?,
    ) {
        viewModel.onMicTap()
        runCurrent()
        viewModel.onTranscript(transcript)
        runCurrent()
    }

    // --- The safety property -------------------------------------------------

    /**
     * The rehearsal must have no way to reach the remote button. Asserted over
     * the constructor rather than by observing behaviour, because behaviour
     * only proves the paths a test happens to walk — this fails the moment
     * someone *wires in* a real-door dependency, which is the actual
     * regression to guard.
     *
     * `ObserveDoorEventsUseCase` is on the list even though it cannot press
     * anything: it is how [WearLiveVoiceViewModel] reaches the real door, and a
     * simulation gated on the real door's position would be a rehearsal you
     * could not run while the garage was open — which is most of what it is
     * for.
     */
    @Test
    fun cannotReachTheRealRemoteButton() {
        val forbidden = setOf(
            "PushRemoteButtonUseCase",
            "RemoteButtonRepository",
            "NetworkButtonDataSource",
            "ButtonStateMachine",
            "ObserveDoorEventsUseCase",
            "DoorRepository",
            "WearHomeViewModel",
            "WearLiveVoiceViewModel",
        )
        val dependencies = WearSimulatedVoiceViewModel::class.java.constructors
            .flatMap { it.parameterTypes.asList() }
            .map { it.simpleName }
            .toSet()
        val violations = dependencies intersect forbidden
        assertTrue(
            "WearSimulatedVoiceViewModel must not depend on $violations. Settings " +
                "-> Simulated voice is a rehearsal; the real button is reachable " +
                "only from the door screen (hold it, or its mic).",
            violations.isEmpty(),
        )
    }

    /**
     * The inverse, and the reason the pair is meaningful: the sibling class
     * that IS live really does hold the button.
     *
     * Without this, deleting the live surface's dependency on
     * `PushRemoteButtonUseCase` — breaking voice control outright — would leave
     * every safety test in this file passing more comfortably than before. A
     * guard that gets happier as the feature dies is not measuring the feature.
     */
    @Test
    fun theLiveSurfaceDoesReachTheRealRemoteButton() {
        val dependencies = WearLiveVoiceViewModel::class.java.constructors
            .flatMap { it.parameterTypes.asList() }
            .map { it.simpleName }
            .toSet()
        assertTrue(
            "WearLiveVoiceViewModel must depend on PushRemoteButtonUseCase — it is " +
                "the surface that presses the real garage button. Found: $dependencies",
            "PushRemoteButtonUseCase" in dependencies,
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
            assertEquals(VoiceDoorState.MOVING, viewModel.doorState.value)

            advanceTimeBy(SimulatedVoiceCommandEnvironment.TRANSIT_MS + 1)
            runCurrent()
            assertEquals(VoiceDoorState.OPEN, viewModel.doorState.value)
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
            assertEquals(VoiceDoorState.OPEN, viewModel.doorState.value)

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
            viewModel.demoDoor.setDoorState(VoiceDoorState.MOVING)

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

    /**
     * The rehearsal has to teach the real thing, and a stuck door is the one
     * state where the two directions part company for a reason other than
     * "you asked for where it already is". If the simulation refused both,
     * practising on it would teach the wrong reflex for the case that matters.
     */
    @Test
    fun aStuckDemoDoorCanStillBeClosedButNotOpened() =
        runTest {
            val viewModel = createViewModel()
            viewModel.demoDoor.setDoorState(VoiceDoorState.STUCK)

            speak(viewModel, "open the garage door")
            assertEquals(
                VoiceCommandIgnoreReason.DOOR_STUCK,
                (viewModel.state.value as VoiceCommandState.Ignored).reason,
            )

            viewModel.demoDoor.setDoorState(VoiceDoorState.STUCK)
            speak(viewModel, "close the garage door")
            assertTrue(
                "A stuck demo door must still accept close",
                viewModel.state.value !is VoiceCommandState.Ignored,
            )
        }

    @Test
    fun anUnknownDemoDoorStateRefusesRatherThanGuesses() =
        runTest {
            val viewModel = createViewModel()
            viewModel.demoDoor.setDoorState(VoiceDoorState.UNKNOWN)

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
            assertEquals(VoiceDoorState.CLOSED, viewModel.doorState.value)
        }

    @Test
    fun aQuestionIsHeardButNotActedOn() =
        runTest {
            val viewModel = createViewModel()
            speak(viewModel, "can you open the door")

            val ignored = viewModel.state.value as VoiceCommandState.Ignored
            assertEquals(VoiceCommandIgnoreReason.NOT_CONFIDENT, ignored.reason)
            assertEquals(VoiceDoorState.CLOSED, viewModel.doorState.value)
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
            assertEquals(VoiceDoorState.CLOSED, viewModel.doorState.value)
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
            assertEquals(VoiceDoorState.CLOSED, viewModel.doorState.value)
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
            assertEquals(VoiceDoorState.CLOSED, viewModel.doorState.value)
            // Aborted, not Committed: walking away stopped a running countdown.
            assertEquals(listOf(HapticCue.VoiceListening, HapticCue.VoiceArmed, HapticCue.VoiceAborted), cues)
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
            // Past the commit's second beat too, so what this asserts is
            // "nothing was QUEUED" rather than "the second beat had not fired
            // yet" — which would pass for the wrong reason.
            advanceTimeBy(WearConfirmTiming.COMMIT_BEAT_GAP_MILLIS + 1)
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
            viewModel.demoDoor.setDoorState(VoiceDoorState.OPEN)
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
            assertEquals(VoiceDoorState.OPEN, viewModel.doorState.value)
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
            assertEquals(VoiceDoorState.CLOSED, viewModel.doorState.value)
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

    /**
     * Opening the microphone buzzes.
     *
     * This is the tap most likely to be made without looking — you press the
     * mic and are already lifting your wrist to speak — so it is the one where
     * silence costs most. Until 0.6.2 there was none: tapping the mic chip
     * produced no feedback at all until you had spoken AND been understood,
     * which on a failed capture meant the entire interaction was silent.
     */
    @Test
    fun openingTheMicrophoneBuzzes() =
        runTest {
            val viewModel = createViewModel()
            val cues = recordCues(viewModel)

            viewModel.onMicTap()
            runCurrent()

            assertEquals(listOf(HapticCue.VoiceListening), cues)
        }

    /**
     * Closing a live microphone without speaking buzzes too — a tap that stops
     * something must say so, exactly like a tap that starts something.
     *
     * The cue is the same [HapticCue.VoiceAborted] an abandoned countdown
     * gives, because it is the same news: you stopped it, nothing was sent.
     */
    @Test
    fun cancellingWhileStillListeningBuzzes() =
        runTest {
            val viewModel = createViewModel()
            val cues = recordCues(viewModel)

            viewModel.onMicTap()
            runCurrent()
            viewModel.onCancel()
            runCurrent()

            assertEquals(listOf(HapticCue.VoiceListening, HapticCue.VoiceAborted), cues)
        }

    /**
     * Re-listening after an outcome buzzes again.
     *
     * Each capture is its own attempt, and the acknowledgement belongs to the
     * tap rather than to the session — a second command must feel exactly like
     * the first, or the user learns that only the first tap registers.
     */
    @Test
    fun eachNewCaptureBuzzesItsOwnAcknowledgement() =
        runTest {
            val viewModel = createViewModel()
            val cues = recordCues(viewModel)

            speak(viewModel, "what a nice door")
            advanceUntilIdle()
            speak(viewModel, "what a nice door")
            advanceUntilIdle()

            assertEquals(
                listOf(
                    HapticCue.VoiceListening,
                    HapticCue.VoiceRefused,
                    HapticCue.VoiceListening,
                    HapticCue.VoiceRefused,
                ),
                cues,
            )
        }

    /**
     * Cancelling an armed countdown feels exactly like lifting a finger off
     * the door mid-hold, because it is the same act: a visible countdown you
     * stopped. [HapticCue.VoiceAborted] shares `HoldAborted`'s constant.
     *
     * Before 0.6.1 this was silent, on the reasoning that cancelling is
     * deliberate and therefore needs no confirmation. That held while the
     * countdown was a simulation; now that letting it finish presses the real
     * button, the wrist should confirm that it did not.
     */
    @Test
    fun cancellingAnArmedCountdownBuzzesLikeAnAbandonedHold() =
        runTest {
            val viewModel = createViewModel()
            val cues = recordCues(viewModel)

            speak(viewModel, "open the garage door")
            viewModel.onCancel()
            runCurrent()
            advanceUntilIdle()

            assertEquals(listOf(HapticCue.VoiceListening, HapticCue.VoiceArmed, HapticCue.VoiceAborted), cues)
        }

    /**
     * ...but an outcome expiring on its own does NOT buzz, even though it also
     * lands on `Ready`. The user did nothing, so there is nothing to confirm —
     * and the hold is equally silent when its own outcome fades.
     *
     * This is the case the naive "buzz whenever we reach Ready" version gets
     * wrong, and it fires after every single refusal, so getting it wrong is
     * loud.
     */
    @Test
    fun anExpiringOutcomeDoesNotBuzzAbort() =
        runTest {
            val viewModel = createViewModel()
            val cues = recordCues(viewModel)

            speak(viewModel, "what a nice garage door")
            advanceUntilIdle()

            assertEquals(
                "A refusal that timed out is not an abandoned countdown.",
                listOf(HapticCue.VoiceListening, HapticCue.VoiceRefused),
                cues,
            )
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
            assertEquals(listOf(HapticCue.VoiceListening, HapticCue.VoiceArmed), cues)

            advanceTimeBy(WearVoiceViewModel.ARMED_WINDOW_MILLIS + 1)
            runCurrent()
            // TWO commit beats, like the hold's: the commit is the one
            // irreversible moment either gesture has, and a wrist actuator
            // expresses emphasis as "again" rather than "harder".
            advanceTimeBy(WearConfirmTiming.COMMIT_BEAT_GAP_MILLIS + 1)
            runCurrent()
            assertEquals(
                listOf(
                    HapticCue.VoiceListening,
                    HapticCue.VoiceArmed,
                    HapticCue.VoiceHalfway,
                    HapticCue.VoiceCommitted,
                    HapticCue.VoiceCommitted,
                ),
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
            assertEquals(listOf(HapticCue.VoiceListening, HapticCue.VoiceArmed), cues)

            advanceTimeBy(2)
            runCurrent()
            assertEquals(listOf(HapticCue.VoiceListening, HapticCue.VoiceArmed, HapticCue.VoiceHalfway), cues)
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
            // The point is the ABSENCE of VoiceHalfway: a cancelled countdown
            // must never pace a countdown that is no longer running.
            assertEquals(listOf(HapticCue.VoiceListening, HapticCue.VoiceArmed, HapticCue.VoiceAborted), cues)
        }

    @Test
    fun aRefusalBuzzesOnceAndOnlyOnce() =
        runTest {
            val viewModel = createViewModel()
            val cues = recordCues(viewModel)

            speak(viewModel, "don't open the door")
            advanceUntilIdle()

            assertEquals(listOf(HapticCue.VoiceListening, HapticCue.VoiceRefused), cues)
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

            // No VoiceCommitted anywhere: the press never happened.
            assertEquals(listOf(HapticCue.VoiceListening, HapticCue.VoiceArmed, HapticCue.VoiceAborted), cues)
        }
}
