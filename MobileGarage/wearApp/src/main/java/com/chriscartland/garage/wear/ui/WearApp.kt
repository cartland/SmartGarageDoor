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
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwipeToDismissBox
import co.touchlab.kermit.Logger
import com.chriscartland.garage.wear.BuildConfig
import com.chriscartland.garage.wear.di.WearComponent

/** Where the app is: the hero screen, or one of its two leaves. */
internal enum class WearDestination {
    Hero,
    VoiceDemo,
    Menu,
}

/**
 * Compose root for the Wear app: theme + app scaffold (time text) + the hero
 * screen, with the voice demo and the menu layered over it.
 *
 * Navigation is one enum rather than a nav library: every destination other
 * than the hero screen is a leaf reached from it, so the whole graph is "which
 * one is on top". [SwipeToDismissBox] supplies the standard Wear
 * swipe-right-to-go-back gesture and its reveal animation, so a leaf behaves
 * like any other watch screen without pulling in wear-compose-navigation for a
 * one-level tree. It was a boolean while there was exactly one leaf; the enum
 * is what keeps the `when` below exhaustive, so adding a third leaf is a
 * compile error at each site that has to handle it rather than a silently
 * unreachable screen.
 *
 * ViewModels are resolved from the kotlin-inject component via the
 * `viewModel { }` initializer, mirroring the phone's
 * `viewModel { component.<x>ViewModel }` pattern. Both ViewModels are resolved
 * here and outlive every destination, so nothing is re-fetched on the way back
 * from a leaf.
 */
@Composable
fun WearApp(component: WearComponent) {
    val wearHomeViewModel: WearHomeViewModel = viewModel { component.wearHomeViewModel }
    val wearVoiceViewModel: WearVoiceViewModel = viewModel { component.wearVoiceViewModel }
    var destination by rememberSaveable { mutableStateOf(WearDestination.Hero) }
    val swipeState = rememberSwipeToDismissBoxState()
    val openStore = rememberStoreLauncher()

    DoorSurfaceEffects(wearHomeViewModel)

    MaterialTheme {
        AppScaffold {
            SwipeToDismissBox(
                onDismissed = { destination = WearDestination.Hero },
                state = swipeState,
                // Nothing to dismiss when the hero screen is already showing;
                // without this, swiping on the hero screen would try to pop a
                // destination that is not there.
                userSwipeEnabled = destination != WearDestination.Hero,
                backgroundKey = WearDestination.Hero.name,
                contentKey = destination.name,
            ) { isBackground ->
                // The background layer is always the hero screen: it is what a
                // leaf reveals as it is swiped away.
                val shown = if (isBackground) WearDestination.Hero else destination
                when (shown) {
                    WearDestination.Hero ->
                        HeroScreen(
                            viewModel = wearHomeViewModel,
                            signInConfig = component.signInConfig,
                            onVoiceDemoClick = { destination = WearDestination.VoiceDemo },
                            onMenuClick = { destination = WearDestination.Menu },
                        )

                    WearDestination.VoiceDemo ->
                        VoiceDemoScreen(viewModel = wearVoiceViewModel)

                    WearDestination.Menu ->
                        WearMenuScreen(
                            versionName = BuildConfig.VERSION_NAME,
                            tagNumber = BuildConfig.WEAR_TAG_NUMBER,
                            onOpenStore = openStore,
                        )
                }
            }
        }
    }
}

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
