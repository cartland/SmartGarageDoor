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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwipeToDismissBox
import com.chriscartland.garage.wear.di.WearComponent

/**
 * Compose root for the Wear app: theme + app scaffold (time text) + the hero
 * screen, with the simulated voice demo layered over it.
 *
 * Navigation is one boolean rather than a nav library: there are exactly two
 * destinations and the second is a leaf. [SwipeToDismissBox] supplies the
 * standard Wear swipe-right-to-go-back gesture and its reveal animation, so
 * the demo behaves like any other watch screen without pulling in
 * wear-compose-navigation for a single edge.
 *
 * ViewModels are resolved from the kotlin-inject component via the
 * `viewModel { }` initializer, mirroring the phone's
 * `viewModel { component.<x>ViewModel }` pattern. Both ViewModels are resolved
 * here and outlive either destination, so nothing is re-fetched on the way
 * back from the demo.
 */
@Composable
fun WearApp(component: WearComponent) {
    val wearHomeViewModel: WearHomeViewModel = viewModel { component.wearHomeViewModel }
    val wearVoiceViewModel: WearVoiceViewModel = viewModel { component.wearVoiceViewModel }
    var showVoiceDemo by rememberSaveable { mutableStateOf(false) }
    val swipeState = rememberSwipeToDismissBoxState()

    DoorSurfaceEffects(wearHomeViewModel)

    MaterialTheme {
        AppScaffold {
            SwipeToDismissBox(
                onDismissed = { showVoiceDemo = false },
                state = swipeState,
                // Nothing to dismiss when the hero screen is already showing;
                // without this, swiping on the hero screen would try to pop a
                // destination that is not there.
                userSwipeEnabled = showVoiceDemo,
                backgroundKey = HERO_KEY,
                contentKey = if (showVoiceDemo) VOICE_DEMO_KEY else HERO_KEY,
            ) { isBackground ->
                if (isBackground || !showVoiceDemo) {
                    HeroScreen(
                        viewModel = wearHomeViewModel,
                        signInConfig = component.signInConfig,
                        onVoiceDemoClick = { showVoiceDemo = true },
                    )
                } else {
                    VoiceDemoScreen(viewModel = wearVoiceViewModel)
                }
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

private const val HERO_KEY = "hero"
private const val VOICE_DEMO_KEY = "voice-demo"
