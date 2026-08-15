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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.tooling.preview.devices.WearDevices
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.wear.R

/**
 * The watch's settings: who you are signed in as, which build is running, and a
 * way to the Play Store.
 *
 * ## The order of the sections is deliberate
 *
 * Account, then app version and its update button, then everything else. It
 * runs from the things a person opens settings *needing* to the things they
 * open settings *curious about*: on a screen this small, "which account is
 * operating my garage" and "am I on the current build" are the two questions
 * worth a swipe, and both are answered without scrolling.
 *
 * The simulated-voice rehearsal is deliberately last. It is the only item here
 * that is a toy rather than an answer, and the bottom of the list is both the
 * least prominent slot and the furthest one from the door screen's real mic —
 * which is the property that matters most about where it sits. Anything added
 * later that is a diagnostic or a demo belongs beside it, below the update
 * section, not above.
 *
 * ## Why this is a scrolling list and not a centred column
 *
 * The previous version was a `Column` with `Arrangement.Center`, which is the
 * wrong growth model for something meant to accumulate items: a centred column
 * grows from the middle outward, so every item added moves every item already
 * there, and once the column exceeds the screen the first item is off the top
 * with nothing to suggest it. It also got nothing from the rotating crown —
 * `Modifier.verticalScroll` has no rotary support, so on the app's ONLY
 * scrollable surface the crown did nothing at all. That is invisible in
 * testing, because touch scrolling works fine.
 *
 * [TransformingLazyColumn] fixes both by construction: it is the Wear list
 * primitive, it wires rotary input itself (including the hierarchical focus the
 * crown needs to reach it), and it grows downward. Adding a setting here is now
 * one `item { }` and nothing else moves.
 *
 * ## Why the update button is a list row and not an `EdgeButton`
 *
 * `EdgeButton` is the Wear Material 3 component for a screen's single action,
 * and it was the obvious choice here — but on a page inside a pager the bottom
 * arc is already spoken for. The pager's page indicator lives there and does
 * NOT fade out while the page is idle (checked on a 454px round emulator: still
 * drawn nine seconds after the swipe), so the two simply overlap: the dots land
 * on the button's lower edge.
 *
 * A list row loses the edge button's shape and its pinning, and gains the thing
 * that matters more — the bottom of the screen stays the indicator's, which is
 * what tells you the door is one swipe back. It also keeps the screen uniform:
 * every item here is a list item, so "add a setting" stays one `item { }`.
 *
 * That trade has one cost, and [initialAnchorItemIndex] pays it: an edge button
 * is always on screen, whereas a row at the end of a list is below the fold. A
 * settle-then-capture fixture only ever sees scroll position 0, so the gallery
 * would show a settings screen whose only action does not appear in it. The
 * parameter lets a fixture open the list already scrolled. Production never
 * passes it — the default is the top, which is where a screen you just swiped to
 * has to start.
 */
@Composable
fun WearSettingsScreen(
    versionName: String,
    tagNumber: Int,
    authState: AuthState,
    onOpenStore: () -> Boolean,
    onSimulatedVoiceClick: () -> Unit,
    modifier: Modifier = Modifier,
    initialAnchorItemIndex: Int = 0,
) {
    val listState = rememberTransformingLazyColumnState(
        initialAnchorItemIndex = initialAnchorItemIndex,
    )
    // Every surface in the list carries this. It is what makes an item shrink
    // and round off as it approaches the top or bottom of a ROUND screen; a
    // surface without it stays rectangular and is simply cut by the mask, which
    // on a full-width button means two sliced corners. Wear Material 3 exposes
    // it per-item rather than applying it for you, so forgetting it is a silent
    // visual bug, not a compile error.
    val transformSpec = rememberTransformationSpec()
    var storeUnavailable by remember { mutableStateOf(false) }

    ScreenScaffold(
        scrollState = listState,
        modifier = modifier,
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            // ScreenScaffold's own values. The room the LAST item needs to clear
            // the bottom arc is not requested here but by that item itself, via
            // `minimumVerticalContentPadding` — see the button below.
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ListHeader(
                    transformation = SurfaceTransformation(transformSpec),
                    modifier = Modifier
                        .minimumVerticalContentPadding(
                            top = ListHeaderDefaults.minimumTopListContentPadding,
                            bottom = ListHeaderDefaults.minimumBottomListContentPadding,
                        ).transformedHeight(this, transformSpec),
                ) {
                    Text(text = stringResource(R.string.settings_title))
                }
            }
            item {
                ListSubHeader(
                    transformation = SurfaceTransformation(transformSpec),
                    modifier = Modifier.transformedHeight(this, transformSpec),
                ) {
                    Text(text = stringResource(R.string.settings_account))
                }
            }
            item {
                AccountValue(account = WearSettingsMappers.account(authState))
            }
            item {
                ListSubHeader(
                    transformation = SurfaceTransformation(transformSpec),
                    modifier = Modifier.transformedHeight(this, transformSpec),
                ) {
                    Text(text = stringResource(R.string.settings_version))
                }
            }
            item {
                Text(
                    text = versionName,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            // Only a build that never came from a release says anything here.
            // A released build's version number above is the whole answer; the
            // tag it was cut from is internal plumbing and means nothing to
            // someone wearing the watch. "Not a release" is a genuinely
            // different thing from "an old release", though, and the version
            // number alone cannot tell you which one you have.
            if (!WearStoreLink.isReleaseBuild(tagNumber)) {
                item {
                    Text(
                        text = stringResource(R.string.settings_local_build),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                Button(
                    onClick = { storeUnavailable = !onOpenStore() },
                    transformation = SurfaceTransformation(transformSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformSpec),
                    label = {
                        Text(text = stringResource(R.string.settings_check_for_update))
                    },
                )
            }
            // Discovered by trying, not by asking first — see WearStoreLink for
            // why a pre-check gives the wrong answer on Android 11+. It stays
            // directly under the button that produced it: it is that button's
            // answer, not a section of its own, and a reader who has to scroll
            // past an unrelated heading to find out why nothing happened has
            // been told too late.
            if (storeUnavailable) {
                item {
                    Text(
                        text = stringResource(R.string.settings_store_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                ListSubHeader(
                    transformation = SurfaceTransformation(transformSpec),
                    modifier = Modifier.transformedHeight(this, transformSpec),
                ) {
                    Text(text = stringResource(R.string.settings_voice))
                }
            }
            // The rehearsal lives HERE, not next to the door, and that placement
            // is the point: the mic on the door screen is the real one, so the
            // pretend one must never sit where a hand reaching for the real
            // control might find it. Settings is a place you go on purpose, and
            // the bottom of settings is the furthest from the door it can be.
            item {
                Button(
                    onClick = onSimulatedVoiceClick,
                    transformation = SurfaceTransformation(transformSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        // The fix for "the last row is clipped by the round
                        // corners", in the library's own words: this modifier
                        // exists "to ensure that, when a list item is at the top
                        // or bottom of the list, the distance from the item to
                        // the screen edge is sufficient (such as to avoid the
                        // item being clipped by edges of a round screen)".
                        //
                        // The list takes the MAXIMUM of its own contentPadding
                        // and what its first/last item asks for here, so this
                        // costs nothing for the items in between — which is why
                        // it is per-item rather than a bottom inset on the whole
                        // list. The value is Material 3's recommendation for a
                        // Button in this position (0.23 of screen height), not a
                        // number of ours.
                        //
                        // It belongs to whichever item is LAST, so it moved here
                        // when this section did. Leaving it on the update button
                        // would have been inert — a middle item's request is
                        // ignored — and this row would be the one clipped.
                        .minimumVerticalContentPadding(
                            ButtonDefaults.minimumVerticalListContentPadding,
                        ).transformedHeight(this, transformSpec),
                    label = {
                        Text(text = stringResource(R.string.settings_simulated_voice))
                    },
                    secondaryLabel = {
                        Text(text = stringResource(R.string.voice_sim_subtitle))
                    },
                )
            }
        }
    }
}

/**
 * The account value line.
 *
 * A signed-in address is *data*, so it renders verbatim; the other two cases
 * are copy. Keeping them apart is what stops the screen claiming "Not signed
 * in" during the moment auth has not resolved yet.
 */
@Composable
private fun AccountValue(account: AccountDisplay) {
    when (account) {
        is AccountDisplay.SignedIn -> Text(
            text = account.email,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        AccountDisplay.SignedOut,
        AccountDisplay.Resolving,
        -> Text(
            text = stringResource(WearSettingsMappers.placeholderFor(account)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** What the account row has to say, before anyone words it. */
internal sealed interface AccountDisplay {
    data class SignedIn(
        val email: String,
    ) : AccountDisplay

    data object SignedOut : AccountDisplay

    data object Resolving : AccountDisplay
}

/** Settings mappers, kept pure so the three-way auth distinction is testable. */
internal object WearSettingsMappers {
    /**
     * Auth has THREE states and the difference between two of them is the whole
     * point of this mapper: `Unknown` means "we have not found out yet", which
     * on a watch that pulls its session from the paired phone can take a
     * visible moment at every cold start. Collapsing it into `Unauthenticated`
     * would make the screen assert, for that moment, that you are signed out —
     * which is both wrong and alarming on the surface whose job is to tell you
     * which account is operating your garage. The hero screen already draws
     * this distinction ("Checking sign-in…"); settings has no business
     * disagreeing with it.
     */
    fun account(authState: AuthState): AccountDisplay =
        when (authState) {
            is AuthState.Authenticated -> AccountDisplay.SignedIn(authState.user.email.asString())
            AuthState.Unauthenticated -> AccountDisplay.SignedOut
            AuthState.Unknown -> AccountDisplay.Resolving
        }

    /** Only the two copy cases have a string; a signed-in address is data. */
    @StringRes
    fun placeholderFor(account: AccountDisplay): Int =
        when (account) {
            AccountDisplay.Resolving -> R.string.settings_account_resolving
            // A real address is rendered verbatim by the caller. Reaching here
            // with one would mean the caller stopped distinguishing data from
            // copy, and "Not signed in" is the safe thing to say — it never
            // leaks an address through a path that could translate it.
            AccountDisplay.SignedOut,
            is AccountDisplay.SignedIn,
            -> R.string.settings_account_signed_out
        }
}

/** Signed in, on a released build: version alone, no plumbing. */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun WearSettingsScreenReleasePreview() {
    MaterialTheme {
        WearSettingsScreen(
            versionName = "0.5.0",
            tagNumber = 18,
            authState = PREVIEW_SETTINGS_USER,
            onOpenStore = { true },
            onSimulatedVoiceClick = {},
        )
    }
}

/** A build that never came from a release tag, while signed out. */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun WearSettingsScreenLocalBuildPreview() {
    MaterialTheme {
        WearSettingsScreen(
            versionName = "0.5.0",
            tagNumber = 0,
            authState = AuthState.Unauthenticated,
            onOpenStore = { true },
            onSimulatedVoiceClick = {},
        )
    }
}

private val PREVIEW_SETTINGS_USER = AuthState.Authenticated(
    User(
        name = DisplayName("Preview User"),
        email = Email("preview@example.com"),
    ),
)
