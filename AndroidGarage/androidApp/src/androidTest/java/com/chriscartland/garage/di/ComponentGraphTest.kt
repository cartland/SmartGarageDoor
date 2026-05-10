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
    fun homeViewModelResolves() {
        assertNotNull("HomeViewModel should resolve", component.homeViewModel)
    }

    @Test
    fun doorHistoryViewModelResolves() {
        assertNotNull("DoorHistoryViewModel should resolve", component.doorHistoryViewModel)
    }

    @Test
    fun profileViewModelResolves() {
        assertNotNull("ProfileViewModel should resolve", component.profileViewModel)
    }

    @Test
    fun functionListViewModelResolves() {
        assertNotNull("FunctionListViewModel should resolve", component.functionListViewModel)
    }

    @Test
    fun diagnosticsViewModelResolves() {
        assertNotNull("DiagnosticsViewModel should resolve", component.diagnosticsViewModel)
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
    fun buttonHealthRepositoryIsSingleton() {
        val c = component
        assertSame("ButtonHealthRepository must be singleton", c.buttonHealthRepository, c.buttonHealthRepository)
    }

    @Test
    fun featureAllowlistRepositoryIsSingleton() {
        val c = component
        assertSame(
            "FeatureAllowlistRepository must be singleton",
            c.featureAllowlistRepository,
            c.featureAllowlistRepository,
        )
    }

    @Test
    fun fcmRegistrationManagerIsSingleton() {
        val c = component
        assertSame("FcmRegistrationManager must be singleton", c.fcmRegistrationManager, c.fcmRegistrationManager)
    }

    @Test
    fun computeButtonHealthDisplayUseCaseIsSingleton() {
        // Singleton-scoped because the use case maintains an Eagerly-started
        // `stateIn` over a `combine(authState, buttonHealth, clock)` flow.
        // A non-singleton would spin up multiple eager combine collectors
        // (one per VM construction), each with its own initial `Loading`
        // value — defeating the buttonHealthDisplay flicker fix.
        val c = component
        assertSame(
            "ComputeButtonHealthDisplayUseCase must be singleton",
            c.computeButtonHealthDisplayUseCase,
            c.computeButtonHealthDisplayUseCase,
        )
    }

    @Test
    fun initialDoorFetchManagerIsSingleton() {
        // Singleton-scoped so the cold-start fetch fires exactly once per
        // process even when MainActivity.onCreate fires multiple times
        // (rotation, app resume after Activity destroy). A non-singleton
        // here would re-fetch on every config change — exactly the
        // VM-init-fetch problem we just removed, in a different layer.
        val c = component
        assertSame("InitialDoorFetchManager must be singleton", c.initialDoorFetchManager, c.initialDoorFetchManager)
    }

    @Test
    fun liveClockIsSingleton() {
        val c = component
        assertSame("LiveClock must be singleton", c.liveClock, c.liveClock)
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
    fun diagnosticsCountersIsSingleton() {
        // Lives on its own DataStore file; second instance for the same
        // path throws IllegalStateException at runtime. See KDoc on
        // DataStoreFactory.
        val c = component
        assertSame(
            "DiagnosticsCountersRepository must be singleton",
            c.diagnosticsCountersRepository,
            c.diagnosticsCountersRepository,
        )
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
    fun homeViewModelIsNotSingleton() {
        val c = component
        assertNotSame(
            "HomeViewModel must be per-nav-entry, not singleton",
            c.homeViewModel,
            c.homeViewModel,
        )
    }

    @Test
    fun doorHistoryViewModelIsNotSingleton() {
        val c = component
        assertNotSame(
            "DoorHistoryViewModel must be per-nav-entry, not singleton",
            c.doorHistoryViewModel,
            c.doorHistoryViewModel,
        )
    }
}
