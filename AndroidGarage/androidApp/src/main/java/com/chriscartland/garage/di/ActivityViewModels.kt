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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Create a ViewModel scoped to the given [ViewModelStoreOwner] (typically an Activity),
 * using the kotlin-inject component as a factory.
 *
 * This ensures the same ViewModel instance is returned across multiple calls,
 * which is critical for ViewModels with instance state (e.g., SignInClient in AuthViewModel).
 */
inline fun <reified T : ViewModel> activityViewModel(
    owner: ViewModelStoreOwner,
    crossinline factory: () -> T,
): T =
    ViewModelProvider(
        owner,
        object : ViewModelProvider.Factory {
            override fun <V : ViewModel> create(modelClass: Class<V>): V {
                @Suppress("UNCHECKED_CAST")
                return factory() as V
            }
        },
    )[T::class.java]
