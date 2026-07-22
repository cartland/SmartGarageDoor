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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import com.chriscartland.garage.wear.di.WearComponent

/**
 * Compose root for the Wear app: theme + app scaffold (time text) + the
 * single hero screen. The ViewModel is resolved from the kotlin-inject
 * component via the `viewModel { }` initializer, mirroring the phone's
 * `viewModel { component.<x>ViewModel }` pattern.
 */
@Composable
fun WearApp(component: WearComponent) {
    val wearHomeViewModel: WearHomeViewModel = viewModel { component.wearHomeViewModel }
    MaterialTheme {
        AppScaffold {
            HeroScreen(
                viewModel = wearHomeViewModel,
                signInConfig = component.signInConfig,
            )
        }
    }
}
