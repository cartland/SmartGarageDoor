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
import com.chriscartland.garage.wear.ui.ConfirmRingState
import com.chriscartland.garage.wear.ui.HeroScreenContent
import com.chriscartland.garage.wear.ui.HeroScreenLayout
import com.chriscartland.garage.wear.ui.VoiceDemoContent
import com.chriscartland.garage.wear.ui.WearSettingsScreen
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
 *     -e stage connecting|closed|inferred|holding|submitted|bloom|moving|open|signed_out|sign_in_error|settings|settings_bottom|settings_local|voice_ready|voice_listening|voice_hearing|voice_armed|voice_committing|voice_sent|voice_refused
 *
 * Stages mirror the hero interaction narrative:
 *   connecting    — cold start, no door event yet: "Connecting…", no ⚠ badge
 *   closed        — green closed door, "Hold to open"
 *   inferred      — a position with no affirmative sensor reading (Opening),
 *                   so the hint stops predicting: "Hold to press the remote"
 *   holding       — full hold ring, press about to fire, hint slot empty.
 *                   The sweep animates from empty over HOLD_TO_CONFIRM_MILLIS
 *                   (2s) and the capture settle is 4s, so a static
 *                   isHolding=true reliably lands on the finished ring.
 *                   Mid-sweep is not capturable from a static fixture
 *   submitted     — the press is in flight: a GAPPED ring rotating slowly,
 *                   "Waiting for the door". The rotation phase at capture
 *                   time is arbitrary, so this PNG legitimately differs
 *                   between regens (same as the voice pulse stages)
 *   bloom         — the commit instant, pinned rather than animated: the ring
 *                   thickened inward to fill its reserved band and gone full
 *                   white. Unreachable from `submitted`, whose settle outlasts
 *                   the ~700ms bloom, which is why it needs its own stage
 *   moving        — door sliding open with the up arrow
 *   open          — red open door, "Hold to close"
 *   signed_out    — Sign in button under the door
 *   sign_in_error — transient "Sign-in failed" caption under the button
 *   settings      — the settings PAGE (one swipe left of the door) on a
 *                   RELEASED build, signed in: header, account, version.
 *                   Deliberately does NOT name the release tag it was cut
 *                   from; that is internal plumbing. Only a fixture can show
 *                   this state, since a local build always has
 *                   WEAR_TAG_NUMBER = 0
 *   settings_bottom — the end of that same list. The update button is below
 *                   the fold, and a settle-then-capture fixture always opens
 *                   at scroll position 0, so the screen's only action would
 *                   otherwise appear in no screenshot at all
 *   settings_local — the same list signed out and on a build that never came
 *                   from a release, which is the one case that still names
 *                   itself. Also the signed-out half of the account row,
 *                   which must not look like the signed-in one
 *
 * Note the settings stages render WearSettingsScreen directly, so they carry
 * no page indicator — in the app it is drawn by the pager one level up.
 *
 * Voice demo stages (a different Composable — VoiceDemoContent). All are
 * simulated by construction: the fixture passes canned VoiceCommandStates and
 * a canned demo door, with no controller and no environment at all.
 *   voice_ready   — at rest: "Simulated", "Tap to speak", demo door Closed
 *   voice_listening — mic takeover, quiet: TWO lines total (the marker and the
 *                   example), with the pulse rings capped to clear both
 *   voice_hearing — mid-utterance: rings at a loud level, and ONE line of live
 *                   transcript, long enough that it has to ellipsize — at the
 *                   START, so the newest words are the ones you can read
 *   voice_armed   — "Would open the door": the action named conditionally,
 *                   under the SAME white ring the real button draws
 *   voice_committing — just after the commit: that ring post-bloom, holding
 *                   complete. The bloom's peak frame is the `bloom` stage —
 *                   it is literally the same component, so capturing it twice
 *                   would pin the same pixels
 *   voice_sent    — the punchline: "Nothing was sent", demo door Moving
 *   voice_refused — the gate refusing a command the demo door has outgrown
 *
 * Across the voice stages the "Simulated" marker and the mic must not move.
 * That is not decoration: the resting column is vertically centred, so before
 * the text slots reserved their height these two drifted 18px between states.
 * The gallery is where a regression would be visible.
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
                    if (stage == STAGE_SETTINGS ||
                        stage == STAGE_SETTINGS_LOCAL ||
                        stage == STAGE_SETTINGS_BOTTOM
                    ) {
                        // Settings, in the two states worth reviewing. The
                        // RELEASED + signed-in one cannot be reached from a
                        // local build at all — BuildConfig.WEAR_TAG_NUMBER is 0
                        // unless the build came from a release tag — so without
                        // a fixture the only version of this screen anyone could
                        // ever look at was the one real users never see.
                        //
                        // The pair also covers both account branches, which is
                        // the part a screenshot can actually adjudicate: a
                        // signed-in row renders an address, a signed-out one
                        // renders copy, and they must not look interchangeable.
                        WearSettingsScreen(
                            versionName = "0.5.0",
                            tagNumber = if (stage == STAGE_SETTINGS_LOCAL) 0 else 18,
                            authState = if (stage == STAGE_SETTINGS_LOCAL) {
                                AuthState.Unauthenticated
                            } else {
                                SCREENSHOT_USER
                            },
                            onOpenStore = { true },
                            // The end of the list, which a settle-then-capture
                            // fixture cannot otherwise reach: it always opens at
                            // scroll position 0, so the screen's only action —
                            // the update button — would never appear in the
                            // gallery at all. Anchoring past the last item lands
                            // the list at its end.
                            initialAnchorItemIndex = if (stage == STAGE_SETTINGS_BOTTOM) {
                                SETTINGS_LAST_ITEM_INDEX
                            } else {
                                0
                            },
                        )
                    } else if (voiceFixture != null) {
                        VoiceDemoContent(
                            state = voiceFixture.state,
                            demoDoorState = voiceFixture.demoDoorState,
                            partialTranscript = voiceFixture.partialTranscript,
                            listeningLevel = voiceFixture.listeningLevel,
                            onMicTap = {},
                            onCancel = {},
                        )
                    } else if (fixture.ring != null) {
                        // Ring frames that no static fixture can reach through
                        // HeroScreenContent, because they are transients the
                        // animation is already past by capture time. Pinned
                        // directly on the layout instead — see HeroScreenContent's
                        // KDoc for why the two are separable at all.
                        HeroScreenLayout(
                            doorPosition = fixture.doorPosition,
                            lastChangeTimeSeconds = null,
                            hasDoorData = fixture.hasDoorData,
                            authState = fixture.authState,
                            buttonState = fixture.buttonState,
                            signInError = fixture.signInError,
                            ring = fixture.ring,
                            onHoldStart = {},
                            onHoldEnd = {},
                            onVoiceDemoClick = {},
                            onSignInClick = {},
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
        val partialTranscript: String? = null,
        val listeningLevel: Float = 0f,
    )

    private fun voiceFixtureFor(stage: String): VoiceStageFixture? =
        when (stage) {
            STAGE_VOICE_READY -> VoiceStageFixture(
                VoiceCommandState.Ready,
                VoiceDoorState.CLOSED,
            )
            // Live interim text from the in-app recognizer. Only reachable via
            // SpeechRecognizer, which the emulator has no service for, so this
            // canned fixture is the only way to see this state at all.
            STAGE_VOICE_LISTENING -> VoiceStageFixture(
                VoiceCommandState.Listening(attempt = 1),
                VoiceDoorState.CLOSED,
                // No transcript yet: the prompt is still doing its job.
                partialTranscript = null,
            )
            STAGE_VOICE_HEARING -> VoiceStageFixture(
                VoiceCommandState.Listening(attempt = 1),
                VoiceDoorState.CLOSED,
                // Mid-utterance: prompt has given way to live text, the cancel
                // hint has stepped aside, and the rings are at a loud level.
                //
                // Deliberately long enough to WRAP. A short partial proves
                // nothing about the one risk this block actually has — text
                // pinned near the bottom of a round screen being clipped at
                // both ends by the mask — and people do say whole sentences.
                partialTranscript = "open the garage door please",
                listeningLevel = 0.85f,
            )
            STAGE_VOICE_ARMED -> VoiceStageFixture(
                VoiceCommandState.Armed(
                    intent = VoiceIntent.OPEN,
                    transcript = "open the garage door",
                    windowMs = WearVoiceViewModel.ARMED_WINDOW_MILLIS,
                ),
                VoiceDoorState.CLOSED,
            )
            // The commit instant. Worth its own stage because the ring's second
            // job — completing and HOLDING rather than vanishing — is only
            // observable here, and the same reasoning that made the hero
            // screen's `submitted` a state rather than a flash applies: a
            // transient is not capturable.
            STAGE_VOICE_COMMITTING -> VoiceStageFixture(
                VoiceCommandState.Sending(intent = VoiceIntent.OPEN),
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
        /** Non-null pins the ring directly instead of letting it animate. */
        val ring: ConfirmRingState? = null,
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
            // The commit bloom at full: ring thickened inward to fill its band
            // and gone full white. ~700ms long in production and therefore
            // never caught by a settle-then-capture script, which is exactly
            // why it is pinned here — it is the frame that has to prove the
            // band works, that the door and both labels survive the moment the
            // press lands.
            STAGE_BLOOM -> StageFixture(
                DoorPosition.CLOSED,
                RemoteButtonState.SendingToServer,
                ring = ConfirmRingState(sweep = 1f, bloom = 1f),
            )
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
        const val STAGE_BLOOM = "bloom"
        const val STAGE_SETTINGS = "settings"
        const val STAGE_SETTINGS_LOCAL = "settings_local"
        const val STAGE_SETTINGS_BOTTOM = "settings_bottom"

        /**
         * Last item of the RELEASED, signed-in settings list: header, account
         * sub-header, account value, version sub-header, version, update
         * button. The local-build caption and the store-unavailable caption are
         * both absent in that shape, which is what makes this a fixed number.
         *
         * Coupled to [com.chriscartland.garage.wear.ui.WearSettingsScreen]'s
         * item list by hand. Adding an item there and not here costs a gallery
         * that stops short of the bottom — visible in the very screenshot this
         * exists to produce, so it fails loudly rather than silently.
         */
        const val SETTINGS_LAST_ITEM_INDEX = 5
        const val STAGE_MOVING = "moving"
        const val STAGE_OPEN = "open"
        const val STAGE_SIGNED_OUT = "signed_out"
        const val STAGE_SIGN_IN_ERROR = "sign_in_error"
        const val STAGE_VOICE_READY = "voice_ready"
        const val STAGE_VOICE_LISTENING = "voice_listening"
        const val STAGE_VOICE_HEARING = "voice_hearing"
        const val STAGE_VOICE_ARMED = "voice_armed"
        const val STAGE_VOICE_COMMITTING = "voice_committing"
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
