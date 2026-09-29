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

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.DoorAnimationMemory
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.RemoteButtonState
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.presentation.DataFreshness
import com.chriscartland.garage.presentation.DoorHeadline
import com.chriscartland.garage.presentation.ElapsedDuration
import com.chriscartland.garage.presentation.SinceStatus
import com.chriscartland.garage.presentation.StatusHeadline
import com.chriscartland.garage.presentation.StatusHeadlineMapper
import com.chriscartland.garage.wear.R
import com.chriscartland.garage.wear.auth.WearGoogleSignIn
import com.chriscartland.garage.wear.di.WearSignInConfig
import com.chriscartland.garage.wear.ui.theme.WearDoorColors
import com.chriscartland.garage.wear.ui.theme.WearFreshnessTint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Stateful hero screen: collects the ViewModel flows, owns the sign-in
 * launcher, and delegates rendering to [HeroScreenContent].
 *
 * Deliberately owns no app-scoped effects. Polling, the screen-wake window and
 * the press-outcome haptics belong to the app rather than to this screen being
 * on top, so they live in `WearApp`'s `DoorSurfaceEffects` — see its KDoc for
 * why that distinction matters once a second destination exists.
 */
@Composable
fun HeroScreen(
    viewModel: WearHomeViewModel,
    signInConfig: WearSignInConfig,
    onVoiceClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val doorEvent by viewModel.currentDoorEvent.collectAsStateWithLifecycle()
    val buttonState by viewModel.buttonState.collectAsStateWithLifecycle()
    val isHolding by viewModel.isHolding.collectAsStateWithLifecycle()
    val signInError by viewModel.signInError.collectAsStateWithLifecycle()
    val freshness by viewModel.freshness.collectAsStateWithLifecycle()
    val sinceStatus by viewModel.sinceStatus.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    HeroScreenContent(
        doorPosition = doorEvent?.doorPosition ?: DoorPosition.UNKNOWN,
        lastChangeTimeSeconds = doorEvent?.lastChangeTimeSeconds,
        sinceStatus = sinceStatus,
        hasDoorData = doorEvent != null,
        freshness = freshness,
        authState = authState,
        buttonState = buttonState,
        isHolding = isHolding,
        onHoldStart = viewModel::onHoldStart,
        onHoldEnd = viewModel::onHoldEnd,
        onAccessibilityArm = viewModel::onAccessibilityArm,
        onAccessibilityConfirm = viewModel::onAccessibilityConfirm,
        onVoiceClick = onVoiceClick,
        signInError = signInError,
        onSignInClick = {
            viewModel.onSignInStarted()
            scope.launch {
                val token = WearGoogleSignIn.requestGoogleIdToken(
                    context = context,
                    serverClientId = signInConfig.googleServerClientId,
                )
                viewModel.onSignInResult(token)
            }
        },
        modifier = modifier,
    )
}

/**
 * Drives the hold ring's animation from the ViewModel's signals and hands the
 * resulting frame to [HeroScreenLayout], which does the drawing.
 *
 * The split exists so the ring's transient states are reachable from a static
 * fixture. The commit bloom lasts about 700ms and is the single most
 * consequential thing the screen draws — it is the app saying "your press was
 * sent" — yet as one inseparable animated Composable it could never be
 * screenshotted, so the one frame most worth reviewing was the one frame no
 * gallery could show. `ScreenshotStagesActivity` calls [HeroScreenLayout] with
 * a canned [ConfirmRingState] instead.
 */
@Composable
fun HeroScreenContent(
    doorPosition: DoorPosition,
    lastChangeTimeSeconds: Long?,
    sinceStatus: SinceStatus?,
    hasDoorData: Boolean,
    freshness: DataFreshness,
    authState: AuthState,
    buttonState: RemoteButtonState,
    isHolding: Boolean,
    signInError: Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    onAccessibilityArm: () -> Unit,
    onAccessibilityConfirm: () -> Unit,
    onVoiceClick: () -> Unit,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inFlight = HeroRing.isInFlight(buttonState)
    val ringPhase = HeroRing.phaseFor(isHolding = isHolding, buttonState = buttonState)

    // Visual sweep of the radial indicator. The ViewModel's countdown is
    // authoritative for firing the press; this animation only mirrors it.
    // The animation itself is shared with the voice demo — see ConfirmRing for
    // why that is one component rather than two that look alike.
    val ring = rememberConfirmRingState(
        phase = ringPhase,
        sweepDurationMillis = WearHomeViewModel.HOLD_TO_CONFIRM_MILLIS.toInt(),
        inFlight = inFlight,
    )

    HeroScreenLayout(
        doorPosition = doorPosition,
        lastChangeTimeSeconds = lastChangeTimeSeconds,
        sinceStatus = sinceStatus,
        hasDoorData = hasDoorData,
        freshness = freshness,
        authState = authState,
        buttonState = buttonState,
        signInError = signInError,
        ring = ring,
        onHoldStart = onHoldStart,
        onHoldEnd = onHoldEnd,
        onAccessibilityArm = onAccessibilityArm,
        onAccessibilityConfirm = onAccessibilityConfirm,
        onVoiceClick = onVoiceClick,
        onSignInClick = onSignInClick,
        modifier = modifier,
    )
}

/**
 * Stateless hero layout (previewable): the door, the state label, the button
 * hint or sign-in chip, and the ring drawn over all of it.
 *
 * Geometry: the hold ring is centered on the PHYSICAL screen and hugs the
 * bezel (like the platform's own progress rings), and in the signed-in
 * layout the door is centered on the screen too, with the state label and
 * hint anchored near the bottom edge. The signed-out/unknown layout keeps a
 * centered column (smaller door + sign-in chip + reserved caption slot —
 * the 0.1.2 overflow fix); the ring never shows there because holding
 * requires authentication.
 *
 * Content never picks its own distance from the edge: every inset comes from
 * [HeroLayout], which reserves the outer band for the ring. See its KDoc for
 * why a bottom label sized against the screen's own circle is not enough.
 */
@Composable
internal fun HeroScreenLayout(
    doorPosition: DoorPosition,
    lastChangeTimeSeconds: Long?,
    sinceStatus: SinceStatus?,
    hasDoorData: Boolean,
    freshness: DataFreshness,
    authState: AuthState,
    buttonState: RemoteButtonState,
    signInError: Boolean,
    ring: ConfirmRingState,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    onAccessibilityArm: () -> Unit,
    onAccessibilityConfirm: () -> Unit,
    onVoiceClick: () -> Unit,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animationMemory = remember { DoorAnimationMemory() }
    ScreenScaffold(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (authState is AuthState.Authenticated) {
                DoorAndLabels(
                    door = {
                        GarageDoorTarget(
                            doorPosition = doorPosition,
                            lastChangeTimeSeconds = lastChangeTimeSeconds,
                            animationMemory = animationMemory,
                            suppressWarningOverlay = !hasDoorData,
                            freshness = freshness,
                            onHoldStart = onHoldStart,
                            onHoldEnd = onHoldEnd,
                            buttonState = buttonState,
                            onAccessibilityArm = onAccessibilityArm,
                            onAccessibilityConfirm = onAccessibilityConfirm,
                        )
                    },
                    labels = {
                        // Measured at the safe chord — near the bottom of a round
                        // screen the usable width is far narrower than the full
                        // width, so an unconstrained line is clipped by the mask
                        // at BOTH ends rather than wrapping (this is what bit
                        // "Hold to press the remote"). Long hints wrap, and the
                        // door is placed from whatever height that yields.
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = HeroScreenMappers.doorStateLabel(doorPosition, hasDoorData, freshness),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                            // ONE slot under the label; what it shows is
                            // HeroScreenMappers.slotFor's decision. minLines keeps
                            // the label still when the slot empties mid-hold.
                            Text(
                                text =
                                    when (
                                        val slot =
                                            HeroScreenMappers.slotFor(buttonState, doorPosition, hasDoorData, freshness, sinceStatus)
                                    ) {
                                        is HeroSlot.Words -> stringResource(slot.res)
                                        is HeroSlot.Since -> HeroSinceWords.text(slot.elapsed)
                                        HeroSlot.Empty -> ""
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                minLines = 1,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                // Voice demo entry point. Deliberately a SEPARATE target with a
                // separate gesture: the door is the one and only path to the
                // real garage, and its tap is deliberately dead so that only a
                // continuous hold can reach it. Hanging voice off the door's tap
                // would mean every sleeve brush (and every drift-cancelled hold,
                // which is a tap as far as the gesture detector can tell) opened
                // a full-screen mic.
                //
                // CenterEnd rather than the top: the top centre belongs to
                // TimeText, and at the vertical centre the round screen's chord
                // is at its widest, so a chip beside the door (which occupies
                // only the middle 46%) clears both the door and the mask.
                // Signed-in only — the signed-out screen has one job, and it has
                // already been fixed once for overflow (0.1.2).
                //
                // The last chip standing. Its twin, an overflow `⋮` mirrored at
                // CenterStart, is gone: settings is a page now, reached by the
                // platform's own swipe with the platform's own indicator. This
                // one stays because what it opens is not a peer surface — it is
                // a live microphone, and that must be asked for explicitly.
                VoiceChip(
                    onClick = onVoiceClick,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = VOICE_CHIP_EDGE_PADDING_DP.dp),
                )
            } else {
                // Signed-out/unknown: centered column. The smaller door keeps
                // the door + label + chip + caption inside the round viewport
                // (0.1.2 fix — the caption used to overflow into the screen's
                // clipped bottom edge).
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    GarageDoorTarget(
                        doorPosition = doorPosition,
                        lastChangeTimeSeconds = lastChangeTimeSeconds,
                        animationMemory = animationMemory,
                        suppressWarningOverlay = !hasDoorData,
                        freshness = freshness,
                        onHoldStart = onHoldStart,
                        onHoldEnd = onHoldEnd,
                        buttonState = buttonState,
                        onAccessibilityArm = onAccessibilityArm,
                        onAccessibilityConfirm = onAccessibilityConfirm,
                        modifier = Modifier.fillMaxWidth(DOOR_WIDTH_FRACTION_SIGNED_OUT),
                    )
                    Text(
                        text = HeroScreenMappers.doorStateLabel(doorPosition, hasDoorData, freshness),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    if (authState is AuthState.Unknown) {
                        Text(
                            text = stringResource(R.string.checking_sign_in),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Button(
                            onClick = onSignInClick,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(text = stringResource(R.string.sign_in))
                        }
                        // The caption always points at the phone: local sign-in
                        // fails on watches whose Play services lack the Identity
                        // module, and "Sign-in failed" alone was a dead end
                        // (strategy 0.5). Same slot at rest and on failure, so the
                        // transient failure (auto-cleared by the ViewModel) never
                        // reflows the column.
                        Text(
                            text = stringResource(if (signInError) R.string.sign_in_failed else R.string.sign_in_on_phone),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (signInError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            minLines = 1,
                        )
                    }
                }
            }
            // Hold-to-confirm ring: centered on the physical screen, hugging
            // the bezel — never around the door image, whose own box sits
            // wherever the layout puts it. LAST child on purpose: the ring
            // draws on top of everything (it takes no input, so it can never
            // block the door's gestures).
            ConfirmRing(
                ring = ring,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(HeroLayout.RING_EDGE_PADDING_DP.dp),
            )
        }
    }
}

/** Ring state derivation, kept pure so the honesty rules above are testable. */
internal object HeroRing {
    /**
     * Whether a press is genuinely outstanding — submitted to the server, or
     * submitted and waiting for the door to move. Only reachable by actually
     * calling the server, which is what makes it safe to celebrate.
     */
    fun isInFlight(buttonState: RemoteButtonState): Boolean =
        buttonState is RemoteButtonState.SendingToServer ||
            buttonState is RemoteButtonState.SendingToDoor

    fun phaseFor(
        isHolding: Boolean,
        buttonState: RemoteButtonState,
    ): RingPhase =
        when {
            isHolding -> RingPhase.Sweeping
            isInFlight(buttonState) -> RingPhase.Committed
            buttonState is RemoteButtonState.AwaitingConfirmation -> RingPhase.Settling
            else -> RingPhase.Idle
        }
}

/**
 * The door and the label block, placed from the block's MEASURED height.
 *
 * The block used to be pinned to the bottom and the door to the centre as two
 * independent facts, and a two-line block — a hint that wraps, "Not confirmed",
 * now the since line — crossed the door's frame on the small round watch. The
 * block is measured first at its chord width, then [HeroLayout.doorPlacement]
 * decides the door: dead centre at [DOOR_WIDTH_FRACTION] while the block has
 * room below it, otherwise moved up by exactly what the block needs with its
 * top corners kept inside the content circle, and shrunk only when moving is
 * not enough. The block is label + slot, and the slot keeps its line even
 * when empty (so the label never jumps), so at rest this is a TWO-line block:
 * the door now sits a couple of dp higher than it did on the 45 mm watch and
 * about a dozen higher on the 41 mm, and the label crosses nothing.
 */
@Composable
private fun DoorAndLabels(
    door: @Composable () -> Unit,
    labels: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(door, labels),
        modifier = modifier,
    ) { (doorMeasurables, labelMeasurables), constraints ->
        // A round watch window is square, so either edge is the diameter.
        val diameterPx = constraints.maxWidth
        val blockWidthPx = (diameterPx * BOTTOM_TEXT_WIDTH_FRACTION).roundToInt()
        val block = labelMeasurables.single().measure(Constraints(minWidth = blockWidthPx, maxWidth = blockWidthPx))
        val placement =
            HeroLayout.doorPlacement(
                diameterDp = diameterPx.toDp().value,
                blockHeightDp = block.height.toDp().value,
                blockWidthDp = blockWidthPx.toDp().value,
                doorFraction = DOOR_WIDTH_FRACTION,
            )
        val side = placement.sideDp.dp.roundToPx()
        val doorPlaceable = doorMeasurables.single().measure(Constraints.fixed(side, side))
        val bottomInsetPx = HeroLayout.bottomInsetDp(diameterPx.toDp().value, blockWidthPx.toDp().value).dp.roundToPx()
        layout(constraints.maxWidth, constraints.maxHeight) {
            doorPlaceable.place(
                x = (constraints.maxWidth - side) / 2,
                y = placement.centreYDp.dp.roundToPx() - side / 2,
            )
            block.place(
                x = (constraints.maxWidth - block.width) / 2,
                y = constraints.maxHeight - bottomInsetPx - block.height,
            )
        }
    }
}

/**
 * The holdable door: gestures land exactly on the door's box.
 *
 * There is deliberately no tap handler — a tap does nothing at all, and only
 * a continuous hold can reach the real garage button. The hold also cancels
 * when the finger drifts more than [HOLD_CANCEL_SLOP_DP], which is what
 * separates a deliberate thumb press (steady) from the sustained accidental
 * contact a sleeve or a wrist against a surface produces (wandering). That
 * drift check is the accidental-press protection that the old separate
 * arming tap used to provide.
 */
@Composable
private fun GarageDoorTarget(
    doorPosition: DoorPosition,
    lastChangeTimeSeconds: Long?,
    animationMemory: DoorAnimationMemory,
    suppressWarningOverlay: Boolean,
    freshness: DataFreshness,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    buttonState: RemoteButtonState,
    onAccessibilityArm: () -> Unit,
    onAccessibilityConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val doorDescription = stringResource(R.string.cd_garage_door)
    val armLabel = stringResource(R.string.a11y_arm_remote)
    val confirmLabel = stringResource(R.string.a11y_confirm_remote)
    val armedState = stringResource(R.string.a11y_door_armed)
    val currentOnHoldStart by rememberUpdatedState(onHoldStart)
    val currentOnHoldEnd by rememberUpdatedState(onHoldEnd)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .semantics {
                contentDescription = doorDescription
                // For screen readers, which cannot hold: two actions, never one
                // activation. Both drive the state machine's two-tap path, so
                // its confirmation window guards them exactly as it guards a
                // hold. Only the action that applies right now is offered.
                when (buttonState) {
                    RemoteButtonState.Ready -> {
                        customActions =
                            listOf(
                                CustomAccessibilityAction(armLabel) {
                                    onAccessibilityArm()
                                    true
                                },
                            )
                    }
                    RemoteButtonState.Preparing,
                    RemoteButtonState.AwaitingConfirmation,
                    -> {
                        stateDescription = armedState
                        customActions =
                            listOf(
                                CustomAccessibilityAction(confirmLabel) {
                                    onAccessibilityConfirm()
                                    true
                                },
                            )
                    }
                    RemoteButtonState.Cancelled,
                    RemoteButtonState.SendingToServer,
                    RemoteButtonState.SendingToDoor,
                    RemoteButtonState.Succeeded,
                    RemoteButtonState.ServerFailed,
                    RemoteButtonState.Forbidden,
                    RemoteButtonState.DoorFailed,
                    -> Unit
                }
            }.pointerInput(Unit) {
                val cancelSlopPx = HOLD_CANCEL_SLOP_DP.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    currentOnHoldStart()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        // Finger lifted, or this pointer vanished.
                        if (change == null || !change.pressed) break
                        // Drifted too far to be a deliberate press.
                        if ((change.position - down.position).getDistance() > cancelSlopPx) break
                    }
                    // Fires for release AND drift-cancel; the ViewModel treats
                    // an incomplete hold the same either way. awaitEachGesture
                    // waits for all pointers to lift before restarting, so a
                    // drift-cancelled finger cannot immediately re-arm.
                    currentOnHoldEnd()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Grey and dim the moment the watch is unsure this is current — which
        // on this device is every launch, since nothing survives the process.
        // Animated, so the first successful poll resolves the dial into colour
        // instead of snapping; on a wrist that motion is itself the signal
        // that the watch just heard from the garage.
        val doorColor by animateColorAsState(
            targetValue = WearFreshnessTint.tint(WearDoorColors.forPosition(doorPosition), freshness),
            animationSpec = WearFreshnessTint.animationSpec(),
            label = "doorFreshnessColor",
        )
        val doorAlpha by animateFloatAsState(
            targetValue = WearFreshnessTint.alphaFor(freshness),
            animationSpec = WearFreshnessTint.animationSpec(),
            label = "doorFreshnessAlpha",
        )
        WearGarageIcon(
            doorPosition = doorPosition,
            animationMemory = animationMemory,
            lastChangeTimeSeconds = lastChangeTimeSeconds,
            color = doorColor,
            suppressWarningOverlay = suppressWarningOverlay,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = doorAlpha },
        )
    }
}

/**
 * Small mic button that opens the LIVE voice surface.
 *
 * Iconic rather than labelled: at the screen's edge there is no room for a
 * caption that would not be clipped by the round mask. That is acceptable here
 * because opening this is not itself an action — it starts listening, and a
 * press still needs a confident command plus a three-second window in which a
 * tap anywhere calls it off. The rehearsal version lives in settings, out of
 * reach of a hand going for the real control.
 */
@Composable
private fun VoiceChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.cd_voice)
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(VOICE_CHIP_SIZE_DP.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mic_24),
            contentDescription = description,
            modifier = Modifier.size(VOICE_CHIP_ICON_SIZE_DP.dp),
        )
    }
}

/** The one line under the door label; decided by [HeroScreenMappers.slotFor]. */
internal sealed interface HeroSlot {
    data class Words(
        @StringRes val res: Int,
    ) : HeroSlot

    /** How long the door has been in its state — worded by [HeroSinceWords]. */
    data class Since(
        val elapsed: ElapsedDuration,
    ) : HeroSlot

    /** Mid-hold: the ring is the channel, and an empty line keeps the label still. */
    data object Empty : HeroSlot
}

/**
 * Words for the hero's since line, from the shared [ElapsedDuration] bucket
 * (ADR-035: shared decides the granularity, the watch words it).
 *
 * Single unit, like the tile beside it ("for 2 hours", not "2 hr 14 min"): the
 * bottom chord holds about a dozen characters. Under a minute says "just now"
 * rather than a seconds figure — the poll loop ticks every ten seconds, and a
 * number that jumps by ten reads as broken.
 */
internal object HeroSinceWords {
    @Composable
    fun text(elapsed: ElapsedDuration): String =
        when (elapsed) {
            is ElapsedDuration.Days -> pluralStringResource(R.plurals.hero_since_days, elapsed.days, elapsed.days)
            is ElapsedDuration.HoursMinutes -> pluralStringResource(R.plurals.hero_since_hours, elapsed.hours, elapsed.hours)
            is ElapsedDuration.Minutes -> stringResource(R.string.hero_since_minutes, elapsed.minutes)
            is ElapsedDuration.Seconds -> stringResource(R.string.hero_since_just_now)
        }
}

/** String/label mappers for the hero screen. */
internal object HeroScreenMappers {
    /**
     * No door event at all (cold start) renders the calm "Connecting…"
     * headline — mirrors the phone Home card. A real server-reported
     * UNKNOWN event (hasDoorData = true) keeps the "Unknown" label.
     *
     * "Connecting…" only stays honest for so long. Once [freshness] reaches
     * [DataFreshness.STALE] — the settle window elapsed and the watch still
     * has nothing — the label becomes "No signal", because at that point
     * "Connecting…" is describing an attempt that is not going well as
     * though it were going fine. This is the ONE text change the settle
     * window unlocks on the watch; the round mask leaves no room for the
     * banner the phone shows, so the headline does that job here.
     *
     * Deliberately NOT keyed on [freshness] alone: a watch that HAS a door
     * event keeps naming the door, muted, exactly as before. Replacing a
     * known position with "No signal" would throw away the last thing we
     * actually know at the moment it becomes most useful.
     */
    @Composable
    fun doorStateLabel(
        doorPosition: DoorPosition,
        hasDoorData: Boolean,
        freshness: DataFreshness,
    ): String =
        when (
            val headline =
                StatusHeadlineMapper.forDoor(
                    doorPosition = doorPosition.takeIf { hasDoorData },
                    freshness = freshness,
                )
        ) {
            // The shared DoorHeadline, not a local re-collapse of DoorPosition:
            // the tile and the complication already word this enum, and the
            // screen behind them must not be the one place that could drift
            // from it (strategy 1.4).
            is StatusHeadline.Door -> stringResource(doorHeadlineRes(headline.headline))
            StatusHeadline.Connecting -> stringResource(R.string.door_state_connecting)
            StatusHeadline.NoSignal -> stringResource(R.string.door_state_no_signal)
        }

    /**
     * The word for each shared [DoorHeadline]. Plain (not @Composable) so it is
     * pinned on the JVM against the tile's words — see HeroScreenMappersTest.
     */
    fun doorHeadlineRes(headline: DoorHeadline): Int =
        when (headline) {
            DoorHeadline.UNKNOWN -> R.string.door_state_unknown
            DoorHeadline.CLOSED -> R.string.door_state_closed
            DoorHeadline.OPENING -> R.string.door_state_opening
            DoorHeadline.OPEN -> R.string.door_state_open
            DoorHeadline.CLOSING -> R.string.door_state_closing
            DoorHeadline.SENSOR_CONFLICT -> R.string.door_state_sensor_conflict
        }

    /**
     * What the ONE line under the door label shows — one slot, so the block
     * never grows past two lines at rest and the door keeps its room.
     *
     * At rest a dated, confirmed door says how long it has been that way: the
     * pre-act question the watch could not answer before (strategy 2.1). A
     * door we cannot vouch for says "Not confirmed" INSTEAD — a duration
     * asserts the door has been that way continuously, and a door we have lost
     * contact with may have moved twice since (the tile's rule, kept here). An
     * undated door, or no door at all, keeps the hold hint — the one place a
     * first-time user learns the gesture. Mid-hold the slot is the ring's
     * (empty, so the label does not jump); in flight and on failure the hint
     * for that state wins over everything.
     */
    fun slotFor(
        buttonState: RemoteButtonState,
        doorPosition: DoorPosition,
        hasDoorData: Boolean,
        freshness: DataFreshness,
        sinceStatus: SinceStatus?,
    ): HeroSlot =
        when (buttonState) {
            RemoteButtonState.Ready ->
                when {
                    !hasDoorData -> HeroSlot.Words(holdHint(doorPosition, hasDoorData))
                    freshness.isSpoken -> HeroSlot.Words(R.string.door_state_not_confirmed)
                    sinceStatus != null -> HeroSlot.Since(sinceStatus.elapsed)
                    else -> HeroSlot.Words(holdHint(doorPosition, hasDoorData))
                }
            RemoteButtonState.Preparing,
            RemoteButtonState.AwaitingConfirmation,
            RemoteButtonState.Cancelled,
            RemoteButtonState.Succeeded,
            -> HeroSlot.Empty
            RemoteButtonState.SendingToServer -> HeroSlot.Words(R.string.button_hint_sending)
            RemoteButtonState.SendingToDoor -> HeroSlot.Words(R.string.button_hint_waiting_for_door)
            RemoteButtonState.ServerFailed -> HeroSlot.Words(R.string.button_hint_server_failed)
            RemoteButtonState.Forbidden -> HeroSlot.Words(R.string.button_hint_forbidden)
            RemoteButtonState.DoorFailed -> HeroSlot.Words(R.string.button_hint_door_failed)
        }

    /**
     * The resting hint, keyed off what [doorStateLabel] will render rather
     * than off the raw [DoorPosition], so the hint and the label above it can
     * never disagree.
     *
     * Only CLOSED and OPEN rest on an affirmative sensor reading, so only
     * they get to predict the door's response. OPEN_MISALIGNED is grouped
     * with OPEN because the label already renders it as "Open" (it is a
     * confident Open whose sensor dropped out for under 3 seconds, and it is
     * well tested in the field). Everything else — including a cold start
     * with no door event at all — names our own action instead: we send a
     * remote press and the garage decides whether that opens, closes, or
     * pauses the door.
     */
    @StringRes
    fun holdHint(
        doorPosition: DoorPosition,
        hasDoorData: Boolean,
    ): Int =
        when {
            !hasDoorData -> R.string.button_hint_hold_to_press_remote
            doorPosition == DoorPosition.CLOSED -> R.string.button_hint_hold_to_open
            doorPosition == DoorPosition.OPEN ||
                doorPosition == DoorPosition.OPEN_MISALIGNED ->
                R.string.button_hint_hold_to_close
            else -> R.string.button_hint_hold_to_press_remote
        }
}

/** At rest on a confidently-closed door: "Hold to open". */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun HeroScreenContentReadyPreview() {
    MaterialTheme {
        HeroScreenContent(
            doorPosition = DoorPosition.CLOSED,
            lastChangeTimeSeconds = null,
            sinceStatus = SinceStatus(sinceEpochSeconds = 0L, elapsed = ElapsedDuration.HoursMinutes(hours = 2, minutes = 14)),
            hasDoorData = true,
            freshness = DataFreshness.FRESH,
            authState = PREVIEW_USER,
            buttonState = RemoteButtonState.Ready,
            isHolding = false,
            signInError = false,
            onHoldStart = {},
            onHoldEnd = {},
            onAccessibilityArm = {},
            onAccessibilityConfirm = {},
            onVoiceClick = {},
            onSignInClick = {},
        )
    }
}

/** Mid-hold: ring sweeping, hint slot deliberately empty. */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun HeroScreenContentHoldingPreview() {
    MaterialTheme {
        HeroScreenContent(
            doorPosition = DoorPosition.CLOSED,
            lastChangeTimeSeconds = null,
            sinceStatus = null,
            hasDoorData = true,
            freshness = DataFreshness.FRESH,
            authState = PREVIEW_USER,
            buttonState = RemoteButtonState.AwaitingConfirmation,
            isHolding = true,
            signInError = false,
            onHoldStart = {},
            onHoldEnd = {},
            onAccessibilityArm = {},
            onAccessibilityConfirm = {},
            onVoiceClick = {},
            onSignInClick = {},
        )
    }
}

/** An inferred position: no affirmative sensor, so no prediction. */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun HeroScreenContentInferredPositionPreview() {
    MaterialTheme {
        HeroScreenContent(
            doorPosition = DoorPosition.OPENING,
            lastChangeTimeSeconds = null,
            sinceStatus = null,
            hasDoorData = true,
            freshness = DataFreshness.FRESH,
            authState = PREVIEW_USER,
            buttonState = RemoteButtonState.Ready,
            isHolding = false,
            signInError = false,
            onHoldStart = {},
            onHoldEnd = {},
            onAccessibilityArm = {},
            onAccessibilityConfirm = {},
            onVoiceClick = {},
            onSignInClick = {},
        )
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun HeroScreenContentSignedOutPreview() {
    MaterialTheme {
        HeroScreenContent(
            doorPosition = DoorPosition.OPEN,
            lastChangeTimeSeconds = null,
            sinceStatus = null,
            hasDoorData = true,
            freshness = DataFreshness.FRESH,
            authState = AuthState.Unauthenticated,
            buttonState = RemoteButtonState.Ready,
            isHolding = false,
            signInError = false,
            onHoldStart = {},
            onHoldEnd = {},
            onAccessibilityArm = {},
            onAccessibilityConfirm = {},
            onVoiceClick = {},
            onSignInClick = {},
        )
    }
}

private val PREVIEW_USER = AuthState.Authenticated(
    User(
        name = DisplayName("Preview User"),
        email = Email("preview@example.com"),
    ),
)

/**
 * Door size, as a fraction of the screen — the door's CEILING, not its size.
 *
 * Trimmed from 0.52 to 0.46 when the bottom block moved inward to clear the
 * ring band: the label rises with it, and the door is the one element with
 * slack to give. [DoorAndLabels] keeps the door at this size and dead centre
 * whenever the measured label block leaves it room; the block is two lines
 * even at rest (the slot keeps its line), and a taller one — a hint that
 * wraps, "Not confirmed" — used to land on the door's frame (fine over the
 * open half, not over the solid panels of a CLOSED door). Now
 * [HeroLayout.doorPlacement] moves the door up by what the block needs, and
 * shrinks it only if it must.
 */
private const val DOOR_WIDTH_FRACTION = 0.46f
private const val DOOR_WIDTH_FRACTION_SIGNED_OUT = 0.42f

/**
 * Width of the bottom label/hint column, as a fraction of the screen.
 *
 * The block's distance from the bottom edge is NOT a constant — it is derived
 * from this width by [HeroLayout.bottomInsetDp], because the two are one
 * decision: a wider block must sit higher to keep its corners inside the
 * content circle. Narrower is not automatically safer, either. Below about 0.44
 * every hint wraps to two lines, which makes the block taller and pushes it
 * further up over the door than the width saved.
 */
private const val BOTTOM_TEXT_WIDTH_FRACTION = 0.46f

/**
 * How far the finger may drift before an in-progress hold is abandoned.
 * Generous enough that a normal thumb press never trips it, tight enough
 * that a sliding sleeve does.
 */
private const val HOLD_CANCEL_SLOP_DP = 20

/**
 * Voice-demo chip geometry. Sized to clear the door (which occupies the middle
 * [DOOR_WIDTH_FRACTION] of the width) with room to spare on the smallest round
 * watch, while staying a comfortable touch target.
 */
private const val VOICE_CHIP_SIZE_DP = 32
private const val VOICE_CHIP_ICON_SIZE_DP = 18
private const val VOICE_CHIP_EDGE_PADDING_DP = 6
