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

package com.chriscartland.garage

import android.app.Application
import com.chriscartland.garage.di.AppComponent
import com.chriscartland.garage.di.create

class GarageApplication : Application() {
    /** kotlin-inject component for dependency injection. */
    val component: AppComponent by lazy {
        AppComponent::class.create(this)
    }

    override fun onCreate() {
        super.onCreate()
        // Warm the DataStore cache so settings are available before first composition.
        // DataStore reads are local file I/O — typically <10ms.
        // This ensures card expand/collapse state is correct on first render.
        component
    }
}
