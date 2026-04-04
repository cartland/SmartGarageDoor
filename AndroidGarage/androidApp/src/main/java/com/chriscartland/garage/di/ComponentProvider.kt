/*
 * Copyright 2024 Chris Cartland. All rights reserved.
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

package com.chriscartland.garage.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.chriscartland.garage.GarageApplication

/**
 * Remember the kotlin-inject [AppComponent] in a Composable.
 *
 * Must be called in a @Composable context, then the result can be
 * used in non-composable lambdas like viewModel { }.
 */
@Composable
fun rememberAppComponent(): AppComponent {
    val context = LocalContext.current
    return remember { (context.applicationContext as GarageApplication).component }
}
