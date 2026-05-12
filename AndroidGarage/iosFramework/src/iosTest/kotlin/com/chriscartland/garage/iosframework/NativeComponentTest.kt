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

package com.chriscartland.garage.iosframework

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Runtime identity guards for the iOS DI graph.
 *
 * Mirrors `ComponentGraphTest` on the Android side. Catches the
 * android/170 regression class — `@SharedSingleton` providers that
 * silently bypass the `_scoped` cache and construct fresh instances
 * per access. On Android the bug manifested as the snooze repository
 * having two coexisting instances; the iOS analog would silently
 * shred the same way if `NativeComponent` ever drifted.
 *
 * Runs locally on macOS via:
 *
 * ```
 * ./gradlew :iosFramework:iosSimulatorArm64Test
 * ```
 *
 * The single shared `component` is constructed via lazy initialization
 * inside a companion `object`, so all tests reuse one DI graph instance
 * — avoiding repeated Room database opens at the same path.
 */
class NativeComponentTest {
    companion object {
        private val component: NativeComponent by lazy {
            IosNativeHelper().createComponent(
                authBridge = NoOpAuthBridge,
                messagingBridge = NoOpMessagingBridge,
                appConfig = IosNativeHelper.defaultDevAppConfig,
            )
        }
    }

    // ---- Component construction ----

    @Test
    fun componentInstantiates() {
        assertNotNull(component, "NativeComponent should instantiate with NoOp bridges")
    }

    // ---- ViewModel resolution (compile + runtime) ----

    @Test
    fun homeViewModelResolves() {
        assertNotNull(component.homeViewModel)
    }

    @Test
    fun doorHistoryViewModelResolves() {
        assertNotNull(component.doorHistoryViewModel)
    }

    @Test
    fun profileViewModelResolves() {
        assertNotNull(component.profileViewModel)
    }

    @Test
    fun functionListViewModelResolves() {
        assertNotNull(component.functionListViewModel)
    }

    @Test
    fun diagnosticsViewModelResolves() {
        assertNotNull(component.diagnosticsViewModel)
    }

    // ---- @SharedSingleton identity guards ----
    //
    // Every state-owning @SharedSingleton provider MUST return the same
    // instance on repeated access. If any of these fail, the iOS DI
    // graph has drifted off the cache pattern — fix per the rules in
    // NativeComponent.kt's KDoc (also documented in CLAUDE.md and
    // AndroidGarage/docs/DI_SINGLETON_REQUIREMENTS.md).

    @Test
    fun applicationScopeIsSingleton() = assertSame(component.applicationScope, component.applicationScope, "applicationScope")

    @Test
    fun httpClientIsSingleton() = assertSame(component.httpClient, component.httpClient, "httpClient")

    @Test
    fun appDatabaseIsSingleton() = assertSame(component.appDatabase, component.appDatabase, "appDatabase")

    @Test
    fun appSettingsIsSingleton() = assertSame(component.appSettings, component.appSettings, "appSettings")

    @Test
    fun appLoggerRepositoryIsSingleton() = assertSame(component.appLoggerRepository, component.appLoggerRepository, "appLoggerRepository")

    @Test
    fun diagnosticsCountersRepositoryIsSingleton() =
        assertSame(
            component.diagnosticsCountersRepository,
            component.diagnosticsCountersRepository,
            "diagnosticsCountersRepository",
        )

    @Test
    fun authRepositoryIsSingleton() = assertSame(component.authRepository, component.authRepository, "authRepository")

    @Test
    fun doorRepositoryIsSingleton() = assertSame(component.doorRepository, component.doorRepository, "doorRepository")

    @Test
    fun serverConfigRepositoryIsSingleton() =
        assertSame(component.serverConfigRepository, component.serverConfigRepository, "serverConfigRepository")

    @Test
    fun snoozeRepositoryIsSingleton() = assertSame(component.snoozeRepository, component.snoozeRepository, "snoozeRepository")

    @Test
    fun remoteButtonRepositoryIsSingleton() =
        assertSame(component.remoteButtonRepository, component.remoteButtonRepository, "remoteButtonRepository")

    @Test
    fun doorFcmRepositoryIsSingleton() = assertSame(component.doorFcmRepository, component.doorFcmRepository, "doorFcmRepository")

    @Test
    fun featureAllowlistRepositoryIsSingleton() =
        assertSame(
            component.featureAllowlistRepository,
            component.featureAllowlistRepository,
            "featureAllowlistRepository",
        )

    @Test
    fun fcmRegistrationManagerIsSingleton() =
        assertSame(component.fcmRegistrationManager, component.fcmRegistrationManager, "fcmRegistrationManager")

    @Test
    fun checkInStalenessManagerIsSingleton() =
        assertSame(component.checkInStalenessManager, component.checkInStalenessManager, "checkInStalenessManager")

    @Test
    fun liveClockIsSingleton() = assertSame(component.liveClock, component.liveClock, "liveClock")

    @Test
    fun dispatcherProviderIsSingleton() = assertSame(component.dispatcherProvider, component.dispatcherProvider, "dispatcherProvider")

    @Test
    fun appClockIsSingleton() = assertSame(component.appClock, component.appClock, "appClock")

    @Test
    fun networkDoorDataSourceIsSingleton() =
        assertSame(component.networkDoorDataSource, component.networkDoorDataSource, "networkDoorDataSource")

    @Test
    fun networkConfigDataSourceIsSingleton() =
        assertSame(component.networkConfigDataSource, component.networkConfigDataSource, "networkConfigDataSource")

    @Test
    fun networkButtonDataSourceIsSingleton() =
        assertSame(component.networkButtonDataSource, component.networkButtonDataSource, "networkButtonDataSource")

    @Test
    fun networkButtonHealthDataSourceIsSingleton() =
        assertSame(
            component.networkButtonHealthDataSource,
            component.networkButtonHealthDataSource,
            "networkButtonHealthDataSource",
        )

    @Test
    fun networkFeatureAllowlistDataSourceIsSingleton() =
        assertSame(
            component.networkFeatureAllowlistDataSource,
            component.networkFeatureAllowlistDataSource,
            "networkFeatureAllowlistDataSource",
        )

    @Test
    fun localDoorDataSourceIsSingleton() = assertSame(component.localDoorDataSource, component.localDoorDataSource, "localDoorDataSource")

    @Test
    fun buttonHealthRepositoryIsSingleton() =
        assertSame(component.buttonHealthRepository, component.buttonHealthRepository, "buttonHealthRepository")

    @Test
    fun buttonHealthFcmRepositoryIsSingleton() =
        assertSame(
            component.buttonHealthFcmRepository,
            component.buttonHealthFcmRepository,
            "buttonHealthFcmRepository",
        )

    @Test
    fun buttonHealthFcmSubscriptionManagerIsSingleton() =
        assertSame(
            component.buttonHealthFcmSubscriptionManager,
            component.buttonHealthFcmSubscriptionManager,
            "buttonHealthFcmSubscriptionManager",
        )

    @Test
    fun initialDoorFetchManagerIsSingleton() =
        assertSame(component.initialDoorFetchManager, component.initialDoorFetchManager, "initialDoorFetchManager")

    @Test
    fun computeButtonHealthDisplayUseCaseIsSingleton() =
        // Singleton-scoped because the use case maintains an Eagerly-started
        // `stateIn` over a `combine(authState, buttonHealth, clock)` flow.
        // A non-singleton would spin up multiple eager combine collectors,
        // each with its own initial Loading value — defeating the
        // buttonHealthDisplay flicker fix.
        assertSame(
            component.computeButtonHealthDisplayUseCase,
            component.computeButtonHealthDisplayUseCase,
            "computeButtonHealthDisplayUseCase",
        )

    // ---- ViewModels are NOT singletons (by design) ----
    //
    // Each call site gets a fresh VM. On Android this is scoped to the
    // nav entry (ADR-021 Rule 4); on iOS the SwiftUI consumer takes a
    // fresh instance via `IosNativeHelper.createComponent`'s entry-point
    // getters. Correctness comes from the shared singleton repositories
    // they all receive — assertNotSame here guards against an accidental
    // `@SharedSingleton` annotation on a VM provider.

    @Test
    fun homeViewModelIsNotSingleton() = assertNotSame(component.homeViewModel, component.homeViewModel, "homeViewModel must be per-screen")

    @Test
    fun doorHistoryViewModelIsNotSingleton() =
        assertNotSame(
            component.doorHistoryViewModel,
            component.doorHistoryViewModel,
            "doorHistoryViewModel must be per-screen",
        )

    @Test
    fun profileViewModelIsNotSingleton() =
        assertNotSame(component.profileViewModel, component.profileViewModel, "profileViewModel must be per-screen")

    @Test
    fun functionListViewModelIsNotSingleton() =
        assertNotSame(
            component.functionListViewModel,
            component.functionListViewModel,
            "functionListViewModel must be per-screen",
        )

    @Test
    fun diagnosticsViewModelIsNotSingleton() =
        assertNotSame(
            component.diagnosticsViewModel,
            component.diagnosticsViewModel,
            "diagnosticsViewModel must be per-screen",
        )
}
