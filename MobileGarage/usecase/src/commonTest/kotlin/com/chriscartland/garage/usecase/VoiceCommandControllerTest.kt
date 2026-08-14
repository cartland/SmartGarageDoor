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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.VoiceIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val WINDOW = VoiceCommandController.DEFAULT_ARMED_WINDOW_MS
private const val IGNORED = VoiceCommandController.IGNORED_DISMISS_MS
private const val FLASH = VoiceCommandController.RESULT_FLASH_MS

/**
 * Exercises the voice-command state machine's safety properties under
 * virtual time: only HIGH arms, the gate runs at arm AND commit, a tap
 * during the window cancels-and-relistens, and nothing commits after
 * backgrounding. The environment is a hand-rolled fake so door state
 * and press outcomes are fully scriptable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCommandControllerTest {
    /** Scriptable environment: settable door, recorded presses. */
    private class FakeVoiceCommandEnvironment(
        private val pressDelayMs: Long = 0L,
    ) : VoiceCommandEnvironment {
        val door = MutableStateFlow(VoiceDoorState.CLOSED)
        override val doorState: StateFlow<VoiceDoorState> = door

        val presses = mutableListOf<VoiceIntent>()
        var failNextPress = false

        /** Server-gate answers, scripted. Null = the server allows it. */
        var serverReason: VoiceCommandIgnoreReason? = null
        var serverChecks = 0

        override suspend fun confirmWithServer(intent: VoiceIntent): VoiceCommandIgnoreReason? {
            serverChecks += 1
            return serverReason
        }

        override suspend fun pressButton(intent: VoiceIntent): Boolean {
            if (pressDelayMs > 0) delay(pressDelayMs)
            presses.add(intent)
            val success = !failNextPress
            failNextPress = false
            return success
        }
    }

    private fun TestScope.createController(
        environment: FakeVoiceCommandEnvironment,
        resultFlashMs: Long = VoiceCommandController.RESULT_FLASH_MS,
    ): VoiceCommandController =
        VoiceCommandController(
            classify = ClassifyVoiceIntentUseCase(RuleBasedVoiceIntentClassifier()),
            environment = environment,
            scope = backgroundScope,
            resultFlashMs = resultFlashMs,
        )

    @Test
    fun micTapFromReadyStartsListening() =
        runTest {
            val controller = createController(FakeVoiceCommandEnvironment())
            controller.onMicTap()
            assertEquals(VoiceCommandState.Listening(attempt = 1), controller.state.value)
        }

    @Test
    fun highConfidenceCommandArmsThenCommits() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("open the garage door")

            val armed = assertIs<VoiceCommandState.Armed>(controller.state.value)
            assertEquals(VoiceIntent.OPEN, armed.intent)
            assertEquals("open the garage door", armed.transcript)
            assertTrue(env.presses.isEmpty(), "Nothing may send during the cancel window")

            advanceTimeBy(WINDOW)
            runCurrent()
            assertEquals(listOf(VoiceIntent.OPEN), env.presses)
            assertEquals(VoiceCommandState.Sent(VoiceIntent.OPEN), controller.state.value)

            advanceTimeBy(FLASH)
            runCurrent()
            assertEquals(VoiceCommandState.Ready, controller.state.value)
        }

    @Test
    fun closeCommandCommitsWhenDoorOpen() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            env.door.value = VoiceDoorState.OPEN
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("close the garage door")
            advanceTimeBy(WINDOW)
            runCurrent()
            assertEquals(listOf(VoiceIntent.CLOSE), env.presses)
            assertEquals(VoiceCommandState.Sent(VoiceIntent.CLOSE), controller.state.value)
        }

    @Test
    fun tapDuringArmedCancelsAndRelistens() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("open the garage door")
            assertIs<VoiceCommandState.Armed>(controller.state.value)

            advanceTimeBy(WINDOW / 2)
            controller.onMicTap()
            assertEquals(VoiceCommandState.Listening(attempt = 2), controller.state.value)

            // The cancelled countdown must never fire.
            advanceTimeBy(WINDOW * 2)
            runCurrent()
            assertTrue(env.presses.isEmpty(), "Cancelled command must not send")
            assertEquals(VoiceCommandState.Listening(attempt = 2), controller.state.value)
        }

    /**
     * [VoiceCommandController.onCancel] is the other verb: stop, rather than
     * restart. Wear uses it because its whole screen is the tap target, so a
     * brush during the countdown must not open a live mic; callers of
     * [VoiceCommandController.onMicTap] are unaffected.
     */
    @Test
    fun cancelDuringArmedStopsInsteadOfRelistening() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("open the garage door")
            assertIs<VoiceCommandState.Armed>(controller.state.value)

            advanceTimeBy(WINDOW / 2)
            controller.onCancel()
            assertEquals(VoiceCommandState.Ready, controller.state.value)

            advanceTimeBy(WINDOW * 2)
            runCurrent()
            assertTrue(env.presses.isEmpty(), "Cancelled command must not send")
            assertEquals(VoiceCommandState.Ready, controller.state.value)
        }

    @Test
    fun cancelDuringListeningReturnsToReady() =
        runTest {
            val controller = createController(FakeVoiceCommandEnvironment())
            controller.onMicTap()
            assertIs<VoiceCommandState.Listening>(controller.state.value)

            controller.onCancel()

            assertEquals(VoiceCommandState.Ready, controller.state.value)
        }

    /** A late result from the abandoned capture must not resurrect it. */
    @Test
    fun transcriptArrivingAfterCancelIsDropped() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            val controller = createController(env)
            controller.onMicTap()
            controller.onCancel()

            controller.onTranscript("open the garage door")
            runCurrent()

            assertEquals(VoiceCommandState.Ready, controller.state.value)
            advanceTimeBy(WINDOW * 2)
            runCurrent()
            assertTrue(env.presses.isEmpty(), "A cancelled capture must not send")
        }

    /** A press cannot be unsent, so cancel has nothing to offer here. */
    @Test
    fun cancelDuringSendingIsIgnored() =
        runTest {
            val env = FakeVoiceCommandEnvironment(pressDelayMs = 100)
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("open the garage door")
            advanceTimeBy(WINDOW + 1)
            runCurrent()
            assertIs<VoiceCommandState.Sending>(controller.state.value)

            controller.onCancel()

            assertIs<VoiceCommandState.Sending>(controller.state.value)
            advanceTimeBy(200)
            runCurrent()
            assertEquals(listOf(VoiceIntent.OPEN), env.presses)
        }

    @Test
    fun cancelFromReadyDoesNothing() =
        runTest {
            val controller = createController(FakeVoiceCommandEnvironment())
            controller.onCancel()
            assertEquals(VoiceCommandState.Ready, controller.state.value)
        }

    @Test
    fun mediumConfidenceIsIgnoredNotConfident() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("can you open the garage door")
            val ignored = assertIs<VoiceCommandState.Ignored>(controller.state.value)
            assertEquals(VoiceCommandIgnoreReason.NOT_CONFIDENT, ignored.reason)
            advanceTimeBy(WINDOW * 2)
            runCurrent()
            assertTrue(env.presses.isEmpty(), "MEDIUM must never act")
        }

    @Test
    fun unknownIntentIsIgnoredNotACommand() =
        runTest {
            val controller = createController(FakeVoiceCommandEnvironment())
            controller.onMicTap()
            controller.onTranscript("is the door open")
            val ignored = assertIs<VoiceCommandState.Ignored>(controller.state.value)
            assertEquals(VoiceCommandIgnoreReason.NOT_A_COMMAND, ignored.reason)
        }

    @Test
    fun blankTranscriptIsIgnoredNoSpeech() =
        runTest {
            val controller = createController(FakeVoiceCommandEnvironment())
            controller.onMicTap()
            controller.onTranscript(null)
            val ignored = assertIs<VoiceCommandState.Ignored>(controller.state.value)
            assertEquals(VoiceCommandIgnoreReason.NO_SPEECH, ignored.reason)
        }

    @Test
    fun ignoredAutoDismissesToReady() =
        runTest {
            val controller = createController(FakeVoiceCommandEnvironment())
            controller.onMicTap()
            controller.onTranscript("is the door open")
            advanceTimeBy(IGNORED)
            runCurrent()
            assertEquals(VoiceCommandState.Ready, controller.state.value)
        }

    @Test
    fun gateBlocksOpenWhenAlreadyOpen() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            env.door.value = VoiceDoorState.OPEN
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("open the garage door")
            val ignored = assertIs<VoiceCommandState.Ignored>(controller.state.value)
            assertEquals(VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN, ignored.reason)
            assertTrue(env.presses.isEmpty())
        }

    @Test
    fun gateBlocksCloseWhenAlreadyClosed() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("close the garage door")
            val ignored = assertIs<VoiceCommandState.Ignored>(controller.state.value)
            assertEquals(VoiceCommandIgnoreReason.DOOR_ALREADY_CLOSED, ignored.reason)
        }

    @Test
    fun gateBlocksWhenDoorMoving() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            env.door.value = VoiceDoorState.MOVING
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("open the garage door")
            val ignored = assertIs<VoiceCommandState.Ignored>(controller.state.value)
            assertEquals(VoiceCommandIgnoreReason.DOOR_MOVING, ignored.reason)
        }

    /**
     * The point of STUCK: a door stopped partway can still be told to close.
     * This is the only state where the two directions disagree for a reason
     * other than "you asked for where it already is".
     */
    @Test
    fun gateAllowsCloseWhenDoorIsStuck() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            env.door.value = VoiceDoorState.STUCK
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("close the garage door")
            assertIs<VoiceCommandState.Armed>(controller.state.value)

            advanceTimeBy(WINDOW)
            runCurrent()
            assertEquals(1, env.presses.size, "A stuck door must still be closable")
        }

    @Test
    fun gateBlocksOpenWhenDoorIsStuck() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            env.door.value = VoiceDoorState.STUCK
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("open the garage door")
            val ignored = assertIs<VoiceCommandState.Ignored>(controller.state.value)
            assertEquals(VoiceCommandIgnoreReason.DOOR_STUCK, ignored.reason)
            assertTrue(env.presses.isEmpty())
        }

    // --- The server's second opinion ----------------------------------------

    @Test
    fun theServerIsAskedBeforeAnythingIsPressed() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("open the garage door")
            assertEquals(0, env.serverChecks, "Arming must not ask; only committing does")

            advanceTimeBy(WINDOW)
            runCurrent()
            assertEquals(1, env.serverChecks)
            assertEquals(listOf(VoiceIntent.OPEN), env.presses)
        }

    /**
     * The whole point of the third gate: a command that passed everything
     * local still does not reach the door if the server disagrees. The local
     * door here says CLOSED, so "open" is locally fine — only the server
     * refuses it.
     */
    @Test
    fun aServerRefusalStopsACommandTheLocalGateAllowed() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            env.serverReason = VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("open the garage door")
            assertIs<VoiceCommandState.Armed>(controller.state.value)

            advanceTimeBy(WINDOW)
            runCurrent()

            val ignored = assertIs<VoiceCommandState.Ignored>(controller.state.value)
            assertEquals(VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN, ignored.reason)
            assertTrue(env.presses.isEmpty(), "A server refusal must not reach the button")
        }

    /** An unreachable server refuses rather than proceeding on a guess. */
    @Test
    fun anUnreachableServerRefusesRatherThanPressing() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            env.serverReason = VoiceCommandIgnoreReason.SERVER_UNREACHABLE
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("open the garage door")
            advanceTimeBy(WINDOW)
            runCurrent()

            val ignored = assertIs<VoiceCommandState.Ignored>(controller.state.value)
            assertEquals(VoiceCommandIgnoreReason.SERVER_UNREACHABLE, ignored.reason)
            assertTrue(env.presses.isEmpty())
        }

    /**
     * Positive control for the three tests above. Without it, an environment
     * whose [VoiceCommandEnvironment.confirmWithServer] refused everything
     * would satisfy every refusal assertion here — and voice would be dead
     * while the suite stayed green.
     */
    @Test
    fun aServerThatAgreesLetsTheCommandThrough() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            env.serverReason = null
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("open the garage door")
            advanceTimeBy(WINDOW)
            runCurrent()

            assertEquals(listOf(VoiceIntent.OPEN), env.presses)
            assertEquals(VoiceCommandState.Sent(VoiceIntent.OPEN), controller.state.value)
        }

    /**
     * The local gate runs FIRST, so a locally-refused command never reaches the
     * network. Ordering matters for more than politeness: it keeps the endpoint
     * off the critical path for the refusals the client can already answer.
     */
    @Test
    fun aLocallyRefusedCommandNeverAsksTheServer() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            env.door.value = VoiceDoorState.OPEN
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("open the garage door")

            advanceTimeBy(WINDOW)
            runCurrent()
            assertEquals(0, env.serverChecks, "Local refusal must short-circuit the round trip")
        }

    @Test
    fun gateBlocksWhenDoorUnknown() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            env.door.value = VoiceDoorState.UNKNOWN
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("open the garage door")
            val ignored = assertIs<VoiceCommandState.Ignored>(controller.state.value)
            assertEquals(VoiceCommandIgnoreReason.DOOR_STATE_UNKNOWN, ignored.reason)
        }

    @Test
    fun doorMovedDuringWindowAbortsCommit() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("open the garage door")
            assertIs<VoiceCommandState.Armed>(controller.state.value)

            // Someone opens the door by hand mid-countdown.
            env.door.value = VoiceDoorState.OPEN
            advanceTimeBy(WINDOW)
            runCurrent()

            val ignored = assertIs<VoiceCommandState.Ignored>(controller.state.value)
            assertEquals(VoiceCommandIgnoreReason.DOOR_STATE_CHANGED, ignored.reason)
            assertTrue(env.presses.isEmpty(), "Commit must re-check the gate")
        }

    @Test
    fun backgroundedDuringArmedCancelsWithoutSending() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("open the garage door")
            controller.onBackgrounded()
            assertEquals(VoiceCommandState.Ready, controller.state.value)
            advanceTimeBy(WINDOW * 2)
            runCurrent()
            assertTrue(env.presses.isEmpty(), "Nothing may commit off-screen")
        }

    @Test
    fun backgroundedDuringListeningIsUntouched() =
        runTest {
            val controller = createController(FakeVoiceCommandEnvironment())
            controller.onMicTap()
            controller.onBackgrounded()
            assertEquals(VoiceCommandState.Listening(attempt = 1), controller.state.value)
        }

    @Test
    fun micTapDuringSendingIsNoOp() =
        runTest {
            val env = FakeVoiceCommandEnvironment(pressDelayMs = 500L)
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("open the garage door")
            advanceTimeBy(WINDOW)
            runCurrent()
            assertIs<VoiceCommandState.Sending>(controller.state.value)

            controller.onMicTap()
            assertIs<VoiceCommandState.Sending>(controller.state.value)

            advanceTimeBy(500L)
            runCurrent()
            assertEquals(listOf(VoiceIntent.OPEN), env.presses)
            assertEquals(VoiceCommandState.Sent(VoiceIntent.OPEN), controller.state.value)
        }

    @Test
    fun failedPressShowsFailedThenReady() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            env.failNextPress = true
            val controller = createController(env)
            controller.onMicTap()
            controller.onTranscript("open the garage door")
            advanceTimeBy(WINDOW)
            runCurrent()
            assertEquals(VoiceCommandState.Failed(VoiceIntent.OPEN), controller.state.value)
            advanceTimeBy(FLASH)
            runCurrent()
            assertEquals(VoiceCommandState.Ready, controller.state.value)
        }

    /**
     * How long the outcome stays up is a per-surface decision, so it has to be
     * a parameter rather than a constant. On the real button the outcome is a
     * receipt for something the user just watched happen and can be brief; on
     * the Wear demo the outcome IS the message ("Nothing was sent") and needs
     * long enough to read on a wrist.
     *
     * Pinned because the default and the override are read from different call
     * sites and only the default is exercised by the phone — a regression that
     * ignored the parameter would otherwise be invisible until someone counted
     * seconds on a watch.
     */
    @Test
    fun resultFlashDurationIsPerSurface() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            val longFlash = FLASH * 3
            val controller = createController(env, resultFlashMs = longFlash)
            controller.onMicTap()
            controller.onTranscript("open the garage door")
            advanceTimeBy(WINDOW)
            runCurrent()
            assertEquals(VoiceCommandState.Sent(VoiceIntent.OPEN), controller.state.value)

            // Still up at the moment the shared default would have cleared it.
            advanceTimeBy(FLASH)
            runCurrent()
            assertEquals(VoiceCommandState.Sent(VoiceIntent.OPEN), controller.state.value)

            advanceTimeBy(longFlash - FLASH)
            runCurrent()
            assertEquals(VoiceCommandState.Ready, controller.state.value)
        }

    @Test
    fun armedWindowIsAdjustableAndClamped() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            val controller = createController(env)
            controller.setArmedWindowMs(VoiceCommandController.MIN_ARMED_WINDOW_MS)
            controller.onMicTap()
            controller.onTranscript("open the garage door")

            // A shortened window still gives a real cancel opportunity...
            advanceTimeBy(VoiceCommandController.MIN_ARMED_WINDOW_MS / 2)
            runCurrent()
            assertTrue(env.presses.isEmpty())

            // ...and commits at the shortened deadline, not the default.
            advanceTimeBy(VoiceCommandController.MIN_ARMED_WINDOW_MS / 2)
            runCurrent()
            assertEquals(listOf(VoiceIntent.OPEN), env.presses)

            controller.setArmedWindowMs(0L)
            assertEquals(
                VoiceCommandController.MIN_ARMED_WINDOW_MS,
                controller.armedWindowMs.value,
                "Window must clamp to the safe minimum",
            )
            controller.setArmedWindowMs(10_000L)
            assertEquals(
                VoiceCommandController.MAX_ARMED_WINDOW_MS,
                controller.armedWindowMs.value,
                "Window must clamp to the maximum",
            )
        }

    @Test
    fun transcriptOutsideListeningIsDropped() =
        runTest {
            val env = FakeVoiceCommandEnvironment()
            val controller = createController(env)
            // A stale recognizer result arriving in Ready must not act.
            controller.onTranscript("open the garage door")
            assertEquals(VoiceCommandState.Ready, controller.state.value)
            advanceTimeBy(WINDOW * 2)
            runCurrent()
            assertTrue(env.presses.isEmpty())
        }
}
