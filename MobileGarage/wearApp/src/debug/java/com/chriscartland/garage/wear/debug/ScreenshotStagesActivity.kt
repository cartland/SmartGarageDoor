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

package com.chriscartland.garage.wear.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.RemoteButtonState
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.domain.model.VoiceIntent
import com.chriscartland.garage.usecase.VoiceCommandIgnoreReason
import com.chriscartland.garage.usecase.VoiceCommandState
import com.chriscartland.garage.usecase.VoiceDoorState
import com.chriscartland.garage.wear.ui.HeroScreenContent
import com.chriscartland.garage.wear.ui.VoiceDemoContent
import com.chriscartland.garage.wear.ui.WearVoiceViewModel

/**
 * Debug-only fixture screen for capturing the Wear screenshot gallery and
 * Play Store listing assets on a Wear emulator. Renders [HeroScreenContent]
 * with canned states — no ViewModel, no network, no auth, and therefore no
 * path to the real door. This is the single enumeration of capture-worthy
 * states; `./scripts/generate-wear-screenshots.sh` drives it.
 *
 * Launch (debug build only):
 *   adb shell am start -n com.chriscartland.garage.debug/com.chriscartland.garage.wear.debug.ScreenshotStagesActivity \
 *     -e stage connecting|closed|inferred|holding|submitted|moving|open|signed_out|sign_in_error|voice_ready|voice_armed|voice_sent|voice_refused
 *
 * Stages mirror the hero interaction narrative:
 *   connecting    — cold start, no door event yet: "Connecting…", no ⚠ badge
 *   closed        — green closed door, "Hold to open"
 *   inferred      — a position with no affirmative sensor reading (Opening),
 *                   so the hint stops predicting: "Hold to press the remote"
 *   holding       — full hold ring, press about to fire, hint slot empty.
 *                   animateFloatAsState initializes AT its target on first
 *                   composition, so a static isHolding=true renders the
 *                   ring already full — deterministic; mid-sweep is not
 *                   capturable from a static fixture
 *   submitted     — the press is in flight: the ring completes and changes
 *                   colour, "Waiting for the door". This is the "your press
 *                   was sent" visual, and it is a STATE rather than a flash
 *                   precisely so it is capturable here
 *   moving        — door sliding open with the up arrow
 *   open          — red open door, "Hold to close"
 *   signed_out    — Sign in button under the door
 *   sign_in_error — transient "Sign-in failed" caption under the button
 *
 * Voice demo stages (a different Composable — VoiceDemoContent). All are
 * simulated by construction: the fixture passes canned VoiceCommandStates and
 * a canned demo door, with no controller and no environment at all.
 *   voice_ready   — at rest: "Simulated", "Tap to speak", demo door Closed
 *   voice_armed   — "Would open the door": the action named conditionally
 *   voice_sent    — the punchline: "Nothing was sent", demo door Moving
 *   voice_refused — the gate refusing a command the demo door has outgrown
 */
class ScreenshotStagesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stage = intent.getStringExtra(STAGE_EXTRA) ?: STAGE_CLOSED
        val voiceFixture = voiceFixtureFor(stage)
        val fixture = fixtureFor(stage)
        setContent {
            MaterialTheme {
                AppScaffold {
                    if (voiceFixture != null) {
                        VoiceDemoContent(
                            state = voiceFixture.state,
                            demoDoorState = voiceFixture.demoDoorState,
                            onMicTap = {},
                        )
                    } else {
                        HeroScreenContent(
                            doorPosition = fixture.doorPosition,
                            lastChangeTimeSeconds = null,
                            hasDoorData = fixture.hasDoorData,
                            authState = fixture.authState,
                            buttonState = fixture.buttonState,
                            isHolding = fixture.isHolding,
                            signInError = fixture.signInError,
                            onHoldStart = {},
                            onHoldEnd = {},
                            onVoiceDemoClick = {},
                            onSignInClick = {},
                        )
                    }
                }
            }
        }
    }

    /**
     * The voice demo renders a different Composable, so its stages are looked
     * up separately; a null result means "this is a hero-screen stage".
     *
     * `voice_armed` captures its countdown ring already FULL: the capture
     * script's settle (4s) outlasts the cancel window (3s), so the animation
     * has finished by the time the screencap lands. Deterministic, which is
     * what a fixture needs — a mid-sweep capture would depend on emulator
     * render latency. Same trade-off the hero screen's `holding` stage makes.
     */
    private data class VoiceStageFixture(
        val state: VoiceCommandState,
        val demoDoorState: VoiceDoorState,
    )

    private fun voiceFixtureFor(stage: String): VoiceStageFixture? =
        when (stage) {
            STAGE_VOICE_READY -> VoiceStageFixture(
                VoiceCommandState.Ready,
                VoiceDoorState.CLOSED,
            )
            STAGE_VOICE_ARMED -> VoiceStageFixture(
                VoiceCommandState.Armed(
                    intent = VoiceIntent.OPEN,
                    transcript = "open the garage door",
                    windowMs = WearVoiceViewModel.ARMED_WINDOW_MILLIS,
                ),
                VoiceDoorState.CLOSED,
            )
            STAGE_VOICE_SENT -> VoiceStageFixture(
                VoiceCommandState.Sent(intent = VoiceIntent.OPEN),
                VoiceDoorState.MOVING,
            )
            STAGE_VOICE_REFUSED -> VoiceStageFixture(
                VoiceCommandState.Ignored(
                    reason = VoiceCommandIgnoreReason.DOOR_ALREADY_OPEN,
                    transcript = "open the garage door",
                    classification = null,
                    engineName = "Rules v3",
                ),
                VoiceDoorState.OPEN,
            )
            else -> null
        }

    private data class StageFixture(
        val doorPosition: DoorPosition,
        val buttonState: RemoteButtonState,
        val authState: AuthState = SCREENSHOT_USER,
        val isHolding: Boolean = false,
        val signInError: Boolean = false,
        val hasDoorData: Boolean = true,
    )

    private fun fixtureFor(stage: String): StageFixture =
        when (stage) {
            STAGE_CONNECTING -> StageFixture(
                DoorPosition.UNKNOWN,
                RemoteButtonState.Ready,
                hasDoorData = false,
            )
            STAGE_INFERRED -> StageFixture(DoorPosition.OPENING, RemoteButtonState.Ready)
            STAGE_HOLDING -> StageFixture(
                DoorPosition.CLOSED,
                RemoteButtonState.AwaitingConfirmation,
                isHolding = true,
            )
            STAGE_SUBMITTED -> StageFixture(DoorPosition.CLOSED, RemoteButtonState.SendingToDoor)
            STAGE_MOVING -> StageFixture(DoorPosition.OPENING, RemoteButtonState.Succeeded)
            STAGE_OPEN -> StageFixture(DoorPosition.OPEN, RemoteButtonState.Ready)
            STAGE_SIGNED_OUT -> StageFixture(
                DoorPosition.OPEN,
                RemoteButtonState.Ready,
                authState = AuthState.Unauthenticated,
            )
            STAGE_SIGN_IN_ERROR -> StageFixture(
                DoorPosition.OPEN,
                RemoteButtonState.Ready,
                authState = AuthState.Unauthenticated,
                signInError = true,
            )
            else -> StageFixture(DoorPosition.CLOSED, RemoteButtonState.Ready)
        }

    private companion object {
        const val STAGE_EXTRA = "stage"
        const val STAGE_CONNECTING = "connecting"
        const val STAGE_CLOSED = "closed"
        const val STAGE_INFERRED = "inferred"
        const val STAGE_HOLDING = "holding"
        const val STAGE_SUBMITTED = "submitted"
        const val STAGE_MOVING = "moving"
        const val STAGE_OPEN = "open"
        const val STAGE_SIGNED_OUT = "signed_out"
        const val STAGE_SIGN_IN_ERROR = "sign_in_error"
        const val STAGE_VOICE_READY = "voice_ready"
        const val STAGE_VOICE_ARMED = "voice_armed"
        const val STAGE_VOICE_SENT = "voice_sent"
        const val STAGE_VOICE_REFUSED = "voice_refused"

        val SCREENSHOT_USER = AuthState.Authenticated(
            User(
                name = DisplayName("Screenshot User"),
                email = Email("screenshots@example.com"),
            ),
        )
    }
}
