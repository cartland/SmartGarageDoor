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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
 * The hero screen stays composed underneath (it is the SwipeToDismissBox's
 * background), so returning from the demo does not re-run its cold-start
 * fetch. ViewModels are resolved from the kotlin-inject component via the
 * `viewModel { }` initializer, mirroring the phone's
 * `viewModel { component.<x>ViewModel }` pattern.
 */
@Composable
fun WearApp(component: WearComponent) {
    val wearHomeViewModel: WearHomeViewModel = viewModel { component.wearHomeViewModel }
    val wearVoiceViewModel: WearVoiceViewModel = viewModel { component.wearVoiceViewModel }
    var showVoiceDemo by rememberSaveable { mutableStateOf(false) }
    val swipeState = rememberSwipeToDismissBoxState()

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

private const val HERO_KEY = "hero"
private const val VOICE_DEMO_KEY = "voice-demo"
