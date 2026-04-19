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
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
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

    // --- @Singleton identity guards ---
    //
    // Every state-owning @Singleton provider MUST return the same instance
    // on repeated access. Absent this guarantee, multiple repository
    // instances coexist and VMs that observe one while a command writes
    // to another silently fail to propagate (the android/170 snooze
    // regression). See docs/DI_SINGLETON_REQUIREMENTS.md.
    //
    // If any of these fail: the DI graph is not cached — AppComponent
    // has drifted back to concrete `val x: T @Provides get()` providers
    // or a transitive path is bypassing `_scoped`. Fix by ensuring each
    // @Singleton provider is reachable through an abstract entry point
    // and that every `@Provides fun` takes its deps as parameters.

    @Test
    fun snoozeRepositoryIsSingleton() {
        val c = component
        assertSame("SnoozeRepository must be singleton", c.snoozeRepository, c.snoozeRepository)
    }

    @Test
    fun authRepositoryIsSingleton() {
        val c = component
        assertSame("AuthRepository must be singleton", c.authRepository, c.authRepository)
    }

    @Test
    fun doorRepositoryIsSingleton() {
        val c = component
        assertSame("DoorRepository must be singleton", c.doorRepository, c.doorRepository)
    }

    @Test
    fun serverConfigRepositoryIsSingleton() {
        val c = component
        assertSame("ServerConfigRepository must be singleton", c.serverConfigRepository, c.serverConfigRepository)
    }

    @Test
    fun doorFcmRepositoryIsSingleton() {
        val c = component
        assertSame("DoorFcmRepository must be singleton", c.doorFcmRepository, c.doorFcmRepository)
    }

    @Test
    fun remoteButtonRepositoryIsSingleton() {
        val c = component
        assertSame("RemoteButtonRepository must be singleton", c.remoteButtonRepository, c.remoteButtonRepository)
    }

    @Test
    fun fcmRegistrationManagerIsSingleton() {
        val c = component
        assertSame("FcmRegistrationManager must be singleton", c.fcmRegistrationManager, c.fcmRegistrationManager)
    }

    @Test
    fun appDatabaseIsSingleton() {
        val c = component
        assertSame("AppDatabase must be singleton", c.appDatabase, c.appDatabase)
    }

    @Test
    fun appSettingsIsSingleton() {
        val c = component
        assertSame("AppSettings must be singleton", c.appSettings, c.appSettings)
    }

    @Test
    fun httpClientIsSingleton() {
        val c = component
        assertSame("HttpClient must be singleton", c.httpClient, c.httpClient)
    }

    @Test
    fun applicationScopeIsSingleton() {
        val c = component
        assertSame("ApplicationScope must be singleton", c.applicationScope, c.applicationScope)
    }

    // --- ViewModels are NOT singletons (by design) ---
    //
    // Each call site gets a fresh VM scoped to its nav entry (ADR-021
    // Rule 4). Correctness comes from the shared singleton repositories
    // they all receive.

    @Test
    fun remoteButtonViewModelIsNotSingleton() {
        val c = component
        assertNotSame(
            "RemoteButtonViewModel must be per-nav-entry, not singleton",
            c.remoteButtonViewModel,
            c.remoteButtonViewModel,
        )
    }

    @Test
    fun doorViewModelIsNotSingleton() {
        val c = component
        assertNotSame(
            "DoorViewModel must be per-nav-entry, not singleton",
            c.doorViewModel,
            c.doorViewModel,
        )
    }
}
