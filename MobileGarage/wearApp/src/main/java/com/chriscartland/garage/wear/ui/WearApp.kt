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

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.PagerScaffoldDefaults
import androidx.wear.compose.material3.SwipeToDismissBox
import co.touchlab.kermit.Logger
import com.chriscartland.garage.wear.BuildConfig
import com.chriscartland.garage.wear.di.WearComponent
import com.chriscartland.garage.wear.di.WearSignInConfig

/** Where the app is: home (the door and its pages), or the one leaf. */
internal enum class WearDestination {
    Home,
    VoiceDemo,
}

/**
 * Compose root for the Wear app: theme + app scaffold (time text) + home, with
 * the voice demo layered over it.
 *
 * ## Two axes, deliberately
 *
 * **Sideways** are peers. The door and settings are pages of one
 * [HorizontalPagerScaffold], so settings is a swipe away from the door and the
 * platform's own page indicator says so. Neither is "inside" the other, which
 * is right: settings is not a place you finish and come back from, it is the
 * other half of the app.
 *
 * **Forward** is a leaf. The voice demo is pushed by
 * [SwipeToDismissBox] and dismissed by swiping right, the standard Wear
 * go-back. It is a leaf rather than a third page on purpose: arriving on it
 * opens a live microphone, and a surface with that effect must be entered
 * deliberately, never brushed into by a stray horizontal swipe.
 *
 * This replaced a flat "which screen is on top" enum whose settings entry point
 * was a floating overflow chip on the hero screen. Two floating chips at the
 * screen's edges is a phone idiom; Wear has no overflow-menu convention for the
 * `⋮` glyph to refer to, and the chip spent hero pixels advertising a
 * destination the platform already has a gesture and an indicator for.
 *
 * ViewModels are resolved from the kotlin-inject component via the
 * `viewModel { }` initializer, mirroring the phone's
 * `viewModel { component.<x>ViewModel }` pattern. Both are resolved here and
 * outlive every destination, so nothing is re-fetched on the way back.
 */
@Composable
fun WearApp(component: WearComponent) {
    val wearHomeViewModel: WearHomeViewModel = viewModel { component.wearHomeViewModel }
    val wearVoiceViewModel: WearVoiceViewModel = viewModel { component.wearVoiceViewModel }
    var destination by rememberSaveable { mutableStateOf(WearDestination.Home) }
    val swipeState = rememberSwipeToDismissBoxState()
    val openStore = rememberStoreLauncher()

    DoorSurfaceEffects(wearHomeViewModel)

    MaterialTheme {
        AppScaffold {
            SwipeToDismissBox(
                onDismissed = { destination = WearDestination.Home },
                state = swipeState,
                // Nothing to dismiss when home is already showing; without
                // this, swiping there would try to pop a destination that is
                // not there. Home's own pages handle their swipes internally —
                // see HomePages for how page 0 hands the gesture back.
                userSwipeEnabled = destination != WearDestination.Home,
                backgroundKey = WearDestination.Home.name,
                contentKey = destination.name,
            ) { isBackground ->
                // The background layer is always home: it is what the leaf
                // reveals as it is swiped away.
                val shown = if (isBackground) WearDestination.Home else destination
                when (shown) {
                    WearDestination.Home ->
                        HomePages(
                            homeViewModel = wearHomeViewModel,
                            signInConfig = component.signInConfig,
                            onVoiceDemoClick = { destination = WearDestination.VoiceDemo },
                            onOpenStore = openStore,
                        )

                    WearDestination.VoiceDemo ->
                        VoiceDemoScreen(viewModel = wearVoiceViewModel)
                }
            }
        }
    }
}

/**
 * Home: the door and settings as peer pages, with the platform's page
 * indicator between them.
 *
 * The pager's `gestureInclusion` default reserves the screen's left edge on the
 * first page, so a swipe that starts there is handed to the enclosing
 * swipe-to-dismiss instead of being eaten as a page change. That is what keeps
 * "swipe right from the door" meaning "leave the app" rather than nothing at
 * all.
 *
 * Rotary is explicitly OFF for the pager. The crown belongs to whatever is
 * scrolling — which on the settings page is its list — and a crown that changed
 * pages instead would make the app's one genuinely scrollable surface
 * unreachable by the watch's main input. This is a real default in
 * [HorizontalPager], not a hypothetical, so it is pinned rather than assumed.
 */
@Composable
private fun HomePages(
    homeViewModel: WearHomeViewModel,
    signInConfig: WearSignInConfig,
    onVoiceDemoClick: () -> Unit,
    onOpenStore: () -> Boolean,
) {
    val pagerState = rememberPagerState(pageCount = { HOME_PAGE_COUNT })
    val authState by homeViewModel.authState.collectAsStateWithLifecycle()

    HorizontalPagerScaffold(pagerState = pagerState) {
        HorizontalPager(
            state = pagerState,
            flingBehavior = PagerScaffoldDefaults.snapWithSpringFlingBehavior(pagerState),
            rotaryScrollableBehavior = null,
        ) { page ->
            AnimatedPage(pageIndex = page, pagerState = pagerState) {
                when (page) {
                    HOME_PAGE_DOOR ->
                        HeroScreen(
                            viewModel = homeViewModel,
                            signInConfig = signInConfig,
                            onVoiceDemoClick = onVoiceDemoClick,
                        )

                    else ->
                        WearSettingsScreen(
                            versionName = BuildConfig.VERSION_NAME,
                            tagNumber = BuildConfig.WEAR_TAG_NUMBER,
                            authState = authState,
                            onOpenStore = onOpenStore,
                        )
                }
            }
        }
    }
}

/**
 * The door is page 0 because it is why the app exists: opening it must never
 * cost a swipe, and a cold launch has to land on it every time.
 */
private const val HOME_PAGE_DOOR = 0
private const val HOME_PAGE_COUNT = 2

/**
 * Opens this app's listing in the watch's own Play Store, reporting whether it
 * found one.
 *
 * The irreducible platform write for the menu: the decision of *where* to go is
 * [WearStoreLink]'s (and unit-tested there), while `startActivity` can only
 * happen here. Failure is caught rather than pre-checked — see [WearStoreLink]
 * for why asking first would give the wrong answer on Android 11+.
 */
@Composable
private fun rememberStoreLauncher(): () -> Boolean {
    val context = LocalContext.current
    return remember(context) {
        {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, WearStoreLink.listingUri().toUri())
                        .addCategory(Intent.CATEGORY_BROWSABLE),
                )
                true
            } catch (e: ActivityNotFoundException) {
                Logger.w { "WearMenu: no Play Store on this watch: $e" }
                false
            }
        }
    }
}

/**
 * The door's app-scoped side effects: foreground polling, the screen-wake
 * window, and the press-outcome haptics.
 *
 * These live at the root rather than inside [HeroScreen] because none of them
 * is about the hero screen being *visible* — they are about the app being in
 * the foreground with a press or a moving door outstanding. Hosting them in
 * the hero screen made all three quietly dependent on whether
 * [SwipeToDismissBox] keeps its background composed while the voice demo is on
 * top, which is an implementation detail of the navigation container and not
 * something this app should have an opinion about. If it does not, opening the
 * demo mid-press would have stopped the very polling that detects the door
 * moving, and deferred the outcome buzz until the user came back.
 *
 * Anchoring them here makes the behaviour identical either way: a press you
 * started still completes, still wakes the screen, and still buzzes, whichever
 * screen you happen to be looking at.
 */
@Composable
private fun DoorSurfaceEffects(viewModel: WearHomeViewModel) {
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()

    // Hold the screen awake only while the ViewModel says something worth
    // watching is happening (press in flight / door moving, 15s cap). The
    // window flag is the irreducible platform write; the decision is the VM's.
    val view = LocalView.current
    LaunchedEffect(view, keepScreenOn) { view.keepScreenOn = keepScreenOn }
    DisposableEffect(view) {
        onDispose { view.keepScreenOn = false }
    }

    // Haptics: the ViewModel decides WHEN and WHAT (testable); this performs
    // the platform write. View.performHapticFeedback needs no VIBRATE
    // permission and respects the watch's own touch-feedback setting — which
    // is why the ring, not the buzz, stays the authoritative channel.
    LaunchedEffect(view, viewModel) {
        viewModel.hapticCues.collect { cue ->
            view.performHapticFeedback(WearHaptics.constantFor(cue))
        }
    }

    // Foreground-only refresh: poll while the app is visible, stop when hidden.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onVisible()
                Lifecycle.Event.ON_STOP -> viewModel.onHidden()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onHidden()
        }
    }
}
