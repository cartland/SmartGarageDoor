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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chriscartland.garage.GarageApplication
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the full kotlin-inject dependency graph resolves at runtime.
 *
 * This catches missing @Provides methods, constructor wiring errors, or
 * R8 stripping that only surface on a real device. If AppComponent can
 * create all ViewModels, the entire DI graph is valid.
 */
@RunWith(AndroidJUnit4::class)
class ComponentGraphTest {
    private val component: AppComponent
        get() {
            val app = InstrumentationRegistry
                .getInstrumentation()
                .targetContext.applicationContext as GarageApplication
            return app.component
        }

    @Test
    fun componentCreates() {
        assertNotNull("AppComponent should create", component)
    }

    @Test
    fun doorViewModelResolves() {
        assertNotNull("DoorViewModel should resolve", component.doorViewModel)
    }

    @Test
    fun authViewModelResolves() {
        assertNotNull("AuthViewModel should resolve", component.authViewModel)
    }

    @Test
    fun remoteButtonViewModelResolves() {
        assertNotNull("RemoteButtonViewModel should resolve", component.remoteButtonViewModel)
    }

    @Test
    fun appSettingsViewModelResolves() {
        assertNotNull("AppSettingsViewModel should resolve", component.appSettingsViewModel)
    }

    @Test
    fun appLoggerViewModelResolves() {
        assertNotNull("AppLoggerViewModel should resolve", component.appLoggerViewModel)
    }
}
