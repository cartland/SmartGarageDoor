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
import com.chriscartland.garage.wear.ui.HeroScreenContent

/**
 * Debug-only fixture screen for capturing the Wear screenshot gallery and
 * Play Store listing assets on a Wear emulator. Renders [HeroScreenContent]
 * with canned states — no ViewModel, no network, no auth, and therefore no
 * path to the real door. This is the single enumeration of capture-worthy
 * states; `./scripts/generate-wear-screenshots.sh` drives it.
 *
 * Launch (debug build only):
 *   adb shell am start -n com.chriscartland.garage.debug/com.chriscartland.garage.wear.debug.ScreenshotStagesActivity \
 *     -e stage connecting|closed|armed|holding|moving|open|signed_out|sign_in_error
 *
 * Stages mirror the hero interaction narrative:
 *   connecting    — cold start, no door event yet: "Connecting…", no ⚠ badge
 *   closed        — green closed door, "Tap door to arm"
 *   armed         — armed with the faint hold ring, "Hold door to press"
 *   holding       — full hold ring ("ring filled, press about to fire").
 *                   animateFloatAsState initializes AT its target on first
 *                   composition, so a static isHolding=true renders the
 *                   ring already full — deterministic; mid-sweep is not
 *                   capturable from a static fixture
 *   moving        — door sliding open with the up arrow, "Door is moving"
 *   open          — red open door
 *   signed_out    — Sign in button under the door
 *   sign_in_error — transient "Sign-in failed" caption under the button
 */
class ScreenshotStagesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stage = intent.getStringExtra(STAGE_EXTRA) ?: STAGE_CLOSED
        val fixture = fixtureFor(stage)
        setContent {
            MaterialTheme {
                AppScaffold {
                    HeroScreenContent(
                        doorPosition = fixture.doorPosition,
                        lastChangeTimeSeconds = null,
                        hasDoorData = fixture.hasDoorData,
                        authState = fixture.authState,
                        buttonState = fixture.buttonState,
                        isHolding = fixture.isHolding,
                        signInError = fixture.signInError,
                        onDoorTap = {},
                        onHoldStart = {},
                        onHoldEnd = {},
                        onAnyTouch = {},
                        onSignInClick = {},
                    )
                }
            }
        }
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
            STAGE_ARMED -> StageFixture(DoorPosition.CLOSED, RemoteButtonState.AwaitingConfirmation)
            STAGE_HOLDING -> StageFixture(
                DoorPosition.CLOSED,
                RemoteButtonState.AwaitingConfirmation,
                isHolding = true,
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
        const val STAGE_ARMED = "armed"
        const val STAGE_HOLDING = "holding"
        const val STAGE_MOVING = "moving"
        const val STAGE_OPEN = "open"
        const val STAGE_SIGNED_OUT = "signed_out"
        const val STAGE_SIGN_IN_ERROR = "sign_in_error"

        val SCREENSHOT_USER = AuthState.Authenticated(
            User(
                name = DisplayName("Screenshot User"),
                email = Email("screenshots@example.com"),
            ),
        )
    }
}
