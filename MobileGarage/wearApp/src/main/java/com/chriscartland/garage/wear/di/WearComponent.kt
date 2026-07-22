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

package com.chriscartland.garage.wear.di

import com.chriscartland.garage.data.AuthBridge
import com.chriscartland.garage.data.LocalDoorDataSource
import com.chriscartland.garage.data.NetworkButtonDataSource
import com.chriscartland.garage.data.NetworkConfigDataSource
import com.chriscartland.garage.data.NetworkDoorDataSource
import com.chriscartland.garage.data.coroutines.DefaultDispatcherProvider
import com.chriscartland.garage.data.ktor.KtorHttpClientFactory
import com.chriscartland.garage.data.ktor.KtorNetworkButtonDataSource
import com.chriscartland.garage.data.ktor.KtorNetworkConfigDataSource
import com.chriscartland.garage.data.ktor.KtorNetworkDoorDataSource
import com.chriscartland.garage.data.repository.CachedServerConfigRepository
import com.chriscartland.garage.data.repository.FirebaseAuthRepository
import com.chriscartland.garage.data.repository.NetworkDoorRepository
import com.chriscartland.garage.data.repository.NetworkRemoteButtonRepository
import com.chriscartland.garage.domain.coroutines.DispatcherProvider
import com.chriscartland.garage.domain.model.AppConfig
import com.chriscartland.garage.domain.repository.AppLoggerRepository
import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.domain.repository.DoorRepository
import com.chriscartland.garage.domain.repository.RemoteButtonRepository
import com.chriscartland.garage.domain.repository.ServerConfigRepository
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import com.chriscartland.garage.usecase.ObserveAuthStateUseCase
import com.chriscartland.garage.usecase.ObserveDoorEventsUseCase
import com.chriscartland.garage.usecase.PushRemoteButtonUseCase
import com.chriscartland.garage.usecase.SignInWithGoogleUseCase
import com.chriscartland.garage.wear.data.InMemoryLocalDoorDataSource
import com.chriscartland.garage.wear.logging.LogcatAppLoggerRepository
import com.chriscartland.garage.wear.ui.WearHomeViewModel
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.annotations.Scope

@Scope
annotation class WearSingleton

/**
 * Wear-only sign-in configuration. Wrapped in a data class (instead of a bare
 * `String` @Provides) so it cannot collide with the `appVersion` String binding.
 */
data class WearSignInConfig(
    val googleServerClientId: String,
)

/**
 * Root kotlin-inject component for Wear OS.
 *
 * Mirrors `iosFramework`'s `NativeComponent` shape — the same shared graph
 * (`:domain` / `:data` / `:usecase`), with platform deps supplied via the
 * constructor. Deliberately much smaller than the phone `AppComponent`:
 * the watch needs door status + the remote button + auth, nothing else
 * (no Room, no DataStore, no FCM, no snooze, no diagnostics).
 *
 * **Singleton discipline (mirrors `AppComponent.kt` rules):**
 *   1. Every `@WearSingleton` provider must be reachable via an abstract
 *      entry point — otherwise kotlin-inject skips the cache and constructs
 *      fresh instances per access (the android/170 regression class of bug).
 *      `WearComponentGraphTest` pins identity with `assertSame`.
 *   2. Every `@Provides fun` body declares its deps as parameters — never
 *      call sibling `provide*()` inside the body.
 *   3. ViewModels are non-singleton (per-screen-instance by design).
 */
@Component
@WearSingleton
abstract class WearComponent(
    @get:Provides val authBridge: AuthBridge,
    @get:Provides val appConfig: AppConfig,
    @get:Provides val signInConfig: WearSignInConfig,
    @get:Provides val appVersion: String,
) {
    // --- Entry points: ViewModels (per-screen, NOT singleton) ---
    abstract val wearHomeViewModel: WearHomeViewModel

    // --- Entry points: @WearSingleton state owners ---
    abstract val applicationScope: CoroutineScope
    abstract val dispatcherProvider: DispatcherProvider
    abstract val httpClient: HttpClient
    abstract val appLoggerRepository: AppLoggerRepository
    abstract val authRepository: AuthRepository
    abstract val serverConfigRepository: ServerConfigRepository
    abstract val doorRepository: DoorRepository
    abstract val remoteButtonRepository: RemoteButtonRepository
    abstract val localDoorDataSource: LocalDoorDataSource
    abstract val networkDoorDataSource: NetworkDoorDataSource
    abstract val networkConfigDataSource: NetworkConfigDataSource
    abstract val networkButtonDataSource: NetworkButtonDataSource

    // --- ViewModels ---

    @Provides
    fun provideWearHomeViewModel(
        observeDoorEvents: ObserveDoorEventsUseCase,
        observeAuthState: ObserveAuthStateUseCase,
        pushRemoteButton: PushRemoteButtonUseCase,
        signInWithGoogle: SignInWithGoogleUseCase,
        fetchCurrentDoorEvent: FetchCurrentDoorEventUseCase,
        dispatchers: DispatcherProvider,
        appVersion: String,
    ): WearHomeViewModel =
        WearHomeViewModel(
            observeDoorEvents = observeDoorEvents,
            observeAuthState = observeAuthState,
            pushRemoteButtonUseCase = pushRemoteButton,
            signInWithGoogleUseCase = signInWithGoogle,
            fetchCurrentDoorEventUseCase = fetchCurrentDoorEvent,
            dispatchers = dispatchers,
            appVersion = appVersion,
        )

    // --- UseCases (stateless, non-singleton) ---

    @Provides
    fun provideObserveDoorEventsUseCase(doorRepository: DoorRepository): ObserveDoorEventsUseCase = ObserveDoorEventsUseCase(doorRepository)

    @Provides
    fun provideObserveAuthStateUseCase(authRepository: AuthRepository): ObserveAuthStateUseCase = ObserveAuthStateUseCase(authRepository)

    @Provides
    fun provideSignInWithGoogleUseCase(authRepository: AuthRepository): SignInWithGoogleUseCase = SignInWithGoogleUseCase(authRepository)

    @Provides
    fun providePushRemoteButtonUseCase(
        authRepository: AuthRepository,
        remoteButtonRepository: RemoteButtonRepository,
    ): PushRemoteButtonUseCase = PushRemoteButtonUseCase(authRepository, remoteButtonRepository)

    @Provides
    fun provideFetchCurrentDoorEventUseCase(doorRepository: DoorRepository): FetchCurrentDoorEventUseCase =
        FetchCurrentDoorEventUseCase(doorRepository)

    // --- @WearSingleton providers (bodies take parameters so caching is honored) ---

    @Provides
    @WearSingleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @WearSingleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()

    @Provides
    @WearSingleton
    fun provideHttpClient(appConfig: AppConfig): HttpClient = KtorHttpClientFactory.create(baseUrl = appConfig.baseUrl, debug = false)

    @Provides
    @WearSingleton
    fun provideNetworkDoorDataSource(httpClient: HttpClient): NetworkDoorDataSource = KtorNetworkDoorDataSource(httpClient)

    @Provides
    @WearSingleton
    fun provideNetworkConfigDataSource(httpClient: HttpClient): NetworkConfigDataSource = KtorNetworkConfigDataSource(httpClient)

    @Provides
    @WearSingleton
    fun provideNetworkButtonDataSource(httpClient: HttpClient): NetworkButtonDataSource = KtorNetworkButtonDataSource(httpClient)

    @Provides
    @WearSingleton
    fun provideLocalDoorDataSource(): LocalDoorDataSource = InMemoryLocalDoorDataSource()

    @Provides
    @WearSingleton
    fun provideAppLoggerRepository(): AppLoggerRepository = LogcatAppLoggerRepository()

    @Provides
    @WearSingleton
    fun provideAuthRepository(
        authBridge: AuthBridge,
        appLoggerRepository: AppLoggerRepository,
        applicationScope: CoroutineScope,
    ): AuthRepository = FirebaseAuthRepository(authBridge, appLoggerRepository, applicationScope)

    @Provides
    @WearSingleton
    fun provideServerConfigRepository(
        networkConfigDataSource: NetworkConfigDataSource,
        appConfig: AppConfig,
        applicationScope: CoroutineScope,
    ): ServerConfigRepository = CachedServerConfigRepository(networkConfigDataSource, appConfig.serverConfigKey, applicationScope)

    @Provides
    @WearSingleton
    fun provideDoorRepository(
        localDoorDataSource: LocalDoorDataSource,
        networkDoorDataSource: NetworkDoorDataSource,
        serverConfigRepository: ServerConfigRepository,
        appConfig: AppConfig,
        applicationScope: CoroutineScope,
    ): DoorRepository =
        NetworkDoorRepository(
            localDoorDataSource,
            networkDoorDataSource,
            serverConfigRepository,
            appConfig.recentEventCount,
            applicationScope,
        )

    @Provides
    @WearSingleton
    fun provideRemoteButtonRepository(
        networkButtonDataSource: NetworkButtonDataSource,
        serverConfigRepository: ServerConfigRepository,
        authRepository: AuthRepository,
        appConfig: AppConfig,
    ): RemoteButtonRepository =
        NetworkRemoteButtonRepository(
            networkButtonDataSource = networkButtonDataSource,
            serverConfigRepository = serverConfigRepository,
            authRepository = authRepository,
            remoteButtonPushEnabled = appConfig.remoteButtonPushEnabled,
        )
}
