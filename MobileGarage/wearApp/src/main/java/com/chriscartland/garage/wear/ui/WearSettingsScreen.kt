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
import androidx.wear.compose.material3.ListHeader
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
            // ScreenScaffold's own padding, unmodified. It already insets about
            // 15dp horizontally, which is more than the Material 3 list spec
            // asks for, so a full-width row is not actually running out of
            // screen — the angled ends it grows near the top and bottom are the
            // surface transformation shaping it to the circle, which is the
            // intended look rather than clipping.
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ListHeader(
                    transformation = SurfaceTransformation(transformSpec),
                    modifier = Modifier.transformedHeight(this, transformSpec),
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
            // why a pre-check gives the wrong answer on Android 11+. No
            // reserved slot is needed here (unlike the old centred column):
            // appending to a top-anchored list moves nothing above it.
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
        )
    }
}

private val PREVIEW_SETTINGS_USER = AuthState.Authenticated(
    User(
        name = DisplayName("Preview User"),
        email = Email("preview@example.com"),
    ),
)
