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
import com.chriscartland.garage.data.NetworkDoorCommandDataSource
import com.chriscartland.garage.data.NetworkDoorDataSource
import com.chriscartland.garage.data.coroutines.DefaultDispatcherProvider
import com.chriscartland.garage.data.ktor.KtorHttpClientFactory
import com.chriscartland.garage.data.ktor.KtorNetworkButtonDataSource
import com.chriscartland.garage.data.ktor.KtorNetworkConfigDataSource
import com.chriscartland.garage.data.ktor.KtorNetworkDoorCommandDataSource
import com.chriscartland.garage.data.ktor.KtorNetworkDoorDataSource
import com.chriscartland.garage.data.repository.CachedServerConfigRepository
import com.chriscartland.garage.data.repository.FirebaseAuthRepository
import com.chriscartland.garage.data.repository.NetworkDoorCommandRepository
import com.chriscartland.garage.data.repository.NetworkDoorRepository
import com.chriscartland.garage.data.repository.NetworkRemoteButtonRepository
import com.chriscartland.garage.data.statuscache.DefaultStatusSnapshotStore
import com.chriscartland.garage.data.statuscache.StatusCacheStorage
import com.chriscartland.garage.data.statuscache.StatusSnapshotStore
import com.chriscartland.garage.domain.coroutines.AppClock
import com.chriscartland.garage.domain.coroutines.DispatcherProvider
import com.chriscartland.garage.domain.model.AppConfig
import com.chriscartland.garage.domain.model.VoiceIntentClassifier
import com.chriscartland.garage.domain.repository.AppLoggerRepository
import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.domain.repository.DoorCommandRepository
import com.chriscartland.garage.domain.repository.DoorRepository
import com.chriscartland.garage.domain.repository.RemoteButtonRepository
import com.chriscartland.garage.domain.repository.ServerConfigRepository
import com.chriscartland.garage.usecase.AppSettleWindow
import com.chriscartland.garage.usecase.AppVisibilityState
import com.chriscartland.garage.usecase.CheckDoorCommandUseCase
import com.chriscartland.garage.usecase.ClassifyVoiceIntentUseCase
import com.chriscartland.garage.usecase.DefaultAppSettleWindow
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import com.chriscartland.garage.usecase.ObserveAuthStateUseCase
import com.chriscartland.garage.usecase.ObserveDoorEventsUseCase
import com.chriscartland.garage.usecase.PushRemoteButtonUseCase
import com.chriscartland.garage.usecase.RuleBasedVoiceIntentClassifier
import com.chriscartland.garage.usecase.SignInWithGoogleUseCase
import com.chriscartland.garage.wear.data.DoorSnapshotHydration
import com.chriscartland.garage.wear.data.PersistedLocalDoorDataSource
import com.chriscartland.garage.wear.glance.WearGlanceStatus
import com.chriscartland.garage.wear.logging.LogcatAppLoggerRepository
import com.chriscartland.garage.wear.tile.WearTilePresenter
import com.chriscartland.garage.wear.ui.WearHomeViewModel
import com.chriscartland.garage.wear.ui.WearLiveVoiceViewModel
import com.chriscartland.garage.wear.ui.WearSimulatedVoiceViewModel
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
 * (no Room, no FCM, no snooze, no diagnostics).
 *
 * The one piece of persistence is the door snapshot — a single
 * `preferences_pb` entry holding the last-known door event, so a surface
 * that renders while the app is NOT running has something true to show.
 * Still no Room: a one-entry cache does not need a database, and the watch
 * has no history screen to put one behind.
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
    /**
     * The door snapshot's raw persistence, built by `GarageWearApplication`
     * from [com.chriscartland.garage.wear.data.WearStatusCache].
     *
     * A constructor dependency rather than a `@Provides` chain, for the same
     * reason as [authBridge]: it is the platform leaf. That also keeps
     * `android.content.Context` out of this component, which is what lets
     * `WearComponentGraphTest` construct the real graph on the JVM, and it
     * puts the no-two-DataStores-per-file guarantee in a process-wide
     * `object` where the system's own entry points (a tile service) are
     * covered by it too.
     */
    @get:Provides val statusCacheStorage: StatusCacheStorage,
) {
    // --- Entry points: ViewModels (per-screen, NOT singleton) ---
    abstract val wearHomeViewModel: WearHomeViewModel

    /**
     * The two voice surfaces are two TYPES, not one type configured two ways.
     *
     * That is the whole safety design: there is no `VoiceCommandEnvironment`
     * binding in this graph to point at the wrong door, because each surface
     * builds its own and only [WearLiveVoiceViewModel] has the ingredients for
     * a real one. Swapping them would mean changing a declared type at the call
     * site, not flipping a provider — see [WearSimulatedVoiceViewModel].
     */
    abstract val wearLiveVoiceViewModel: WearLiveVoiceViewModel
    abstract val wearSimulatedVoiceViewModel: WearSimulatedVoiceViewModel

    // --- Entry points: @WearSingleton state owners ---
    abstract val applicationScope: CoroutineScope
    abstract val dispatcherProvider: DispatcherProvider
    abstract val httpClient: HttpClient
    abstract val appLoggerRepository: AppLoggerRepository
    abstract val authRepository: AuthRepository
    abstract val serverConfigRepository: ServerConfigRepository
    abstract val doorRepository: DoorRepository
    abstract val remoteButtonRepository: RemoteButtonRepository
    abstract val appVisibilityState: AppVisibilityState
    abstract val appSettleWindow: AppSettleWindow
    abstract val localDoorDataSource: LocalDoorDataSource

    /**
     * The door cache as its CONCRETE type, which is what makes it one object.
     *
     * It answers to two interfaces — `LocalDoorDataSource` for the repository
     * and `DoorSnapshotHydration` for the tile — and those must be the same
     * instance or the tile would wait on a hydration that never fills the
     * cache the repository reads. Entry-pointing the concrete type is what
     * gives kotlin-inject something to cache; the two interface providers
     * below just hand it back.
     */
    abstract val persistedLocalDoorDataSource: PersistedLocalDoorDataSource

    /**
     * The typed snapshot store wrapping [statusCacheStorage], and the clock
     * the snapshot is stamped with.
     *
     * Entry-pointed because `@WearSingleton` is only honoured for providers
     * reachable from one — an uncached store would give the hydrating data
     * source and any future reader their own, and they would not agree on
     * what had been written.
     */
    abstract val statusSnapshotStore: StatusSnapshotStore
    abstract val appClock: AppClock

    /**
     * The tile's decisions. A `@WearSingleton` because it REMEMBERS across
     * requests (whether the last refresh failed, and what was last rendered)
     * and the system builds a fresh `TileService` for every request — an
     * uncached presenter would read its seed values forever, so the tile
     * could neither report a failed refresh nor notice the door had moved.
     */
    abstract val wearTilePresenter: WearTilePresenter

    /**
     * The reading both glance surfaces share.
     *
     * A singleton so "the last refresh failed" is one fact about the process
     * rather than one per surface — the tile and the complication should not
     * disagree about whether the server is reachable.
     */
    abstract val wearGlanceStatus: WearGlanceStatus
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
        appVisibilityState: AppVisibilityState,
        appSettleWindow: AppSettleWindow,
        clock: AppClock,
        appVersion: String,
    ): WearHomeViewModel =
        WearHomeViewModel(
            observeDoorEvents = observeDoorEvents,
            observeAuthState = observeAuthState,
            pushRemoteButtonUseCase = pushRemoteButton,
            signInWithGoogleUseCase = signInWithGoogle,
            fetchCurrentDoorEventUseCase = fetchCurrentDoorEvent,
            dispatchers = dispatchers,
            appVisibilityState = appVisibilityState,
            appSettleWindow = appSettleWindow,
            clock = clock,
            appVersion = appVersion,
        )

    /** Voice against the real door. Needs the button; the simulated one does not. */
    @Provides
    fun provideWearLiveVoiceViewModel(
        classifyVoiceIntent: ClassifyVoiceIntentUseCase,
        observeDoorEvents: ObserveDoorEventsUseCase,
        pushRemoteButton: PushRemoteButtonUseCase,
        checkDoorCommand: CheckDoorCommandUseCase,
        dispatchers: DispatcherProvider,
        appVersion: String,
    ): WearLiveVoiceViewModel =
        WearLiveVoiceViewModel(
            classifyVoiceIntent = classifyVoiceIntent,
            observeDoorEvents = observeDoorEvents,
            pushRemoteButton = pushRemoteButton,
            checkDoorCommand = checkDoorCommand,
            dispatchers = dispatchers,
            appVersion = appVersion,
        )

    /**
     * Voice against a pretend door. Note what is NOT a parameter here: no
     * button, no repository, no door. There is nothing to pass it that could
     * reach the garage, which is the point.
     */
    @Provides
    fun provideWearSimulatedVoiceViewModel(
        classifyVoiceIntent: ClassifyVoiceIntentUseCase,
        dispatchers: DispatcherProvider,
    ): WearSimulatedVoiceViewModel =
        WearSimulatedVoiceViewModel(
            classifyVoiceIntent = classifyVoiceIntent,
            dispatchers = dispatchers,
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
    fun provideCheckDoorCommandUseCase(
        authRepository: AuthRepository,
        doorCommandRepository: DoorCommandRepository,
    ): CheckDoorCommandUseCase = CheckDoorCommandUseCase(authRepository, doorCommandRepository)

    @Provides
    fun provideFetchCurrentDoorEventUseCase(doorRepository: DoorRepository): FetchCurrentDoorEventUseCase =
        FetchCurrentDoorEventUseCase(doorRepository)

    @Provides
    fun provideVoiceIntentClassifier(): VoiceIntentClassifier = RuleBasedVoiceIntentClassifier()

    @Provides
    fun provideClassifyVoiceIntentUseCase(classifier: VoiceIntentClassifier): ClassifyVoiceIntentUseCase =
        ClassifyVoiceIntentUseCase(classifier)

    // --- @WearSingleton providers (bodies take parameters so caching is honored) ---

    @Provides
    @WearSingleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @WearSingleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()

    @Provides
    @WearSingleton
    fun provideAppVisibilityState(): AppVisibilityState = AppVisibilityState()

    /**
     * The watch's own settle window. Same shared machinery the phone and iOS
     * use — the watch just reports its visibility from `WearHomeViewModel`'s
     * `onVisible` / `onHidden` (which are already wired to `ON_START` /
     * `ON_STOP`) instead of from an Application or a scene phase.
     *
     * Started by the ViewModel rather than an `AppStartup`: the watch has no
     * such entry point, and the window is only meaningful while a screen is
     * on anyway.
     */
    @Provides
    @WearSingleton
    fun provideAppSettleWindow(
        appVisibilityState: AppVisibilityState,
        applicationScope: CoroutineScope,
        dispatchers: DispatcherProvider,
    ): AppSettleWindow =
        DefaultAppSettleWindow(
            appVisibilityState = appVisibilityState,
            scope = applicationScope,
            dispatcher = dispatchers.io,
        )

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
    fun provideAppClock(): AppClock = AppClock { System.currentTimeMillis() / 1000 }

    @Provides
    @WearSingleton
    fun provideStatusSnapshotStore(storage: StatusCacheStorage): StatusSnapshotStore = DefaultStatusSnapshotStore(storage)

    @Provides
    @WearSingleton
    fun provideWearGlanceStatus(
        observeDoorEvents: ObserveDoorEventsUseCase,
        fetchCurrentDoorEvent: FetchCurrentDoorEventUseCase,
        hydration: DoorSnapshotHydration,
        clock: AppClock,
    ): WearGlanceStatus =
        WearGlanceStatus(
            observeDoorEvents = observeDoorEvents,
            fetchCurrentDoorEvent = fetchCurrentDoorEvent,
            hydration = hydration,
            clock = clock,
        )

    @Provides
    @WearSingleton
    fun provideWearTilePresenter(glance: WearGlanceStatus): WearTilePresenter = WearTilePresenter(glance)

    @Provides
    @WearSingleton
    fun providePersistedLocalDoorDataSource(
        snapshotStore: StatusSnapshotStore,
        clock: AppClock,
        applicationScope: CoroutineScope,
    ): PersistedLocalDoorDataSource =
        PersistedLocalDoorDataSource(
            snapshotStore = snapshotStore,
            clock = clock,
            scope = applicationScope,
        )

    // The two faces of that one object. Not @WearSingleton themselves: they
    // add no state, and the caching that matters is on the concrete provider
    // above — scoping these as well would be harmless but would suggest there
    // were three things here rather than one.
    @Provides
    fun provideLocalDoorDataSource(source: PersistedLocalDoorDataSource): LocalDoorDataSource = source

    @Provides
    fun provideDoorSnapshotHydration(source: PersistedLocalDoorDataSource): DoorSnapshotHydration = source

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

    @Provides
    @WearSingleton
    fun provideNetworkDoorCommandDataSource(httpClient: HttpClient): NetworkDoorCommandDataSource =
        KtorNetworkDoorCommandDataSource(httpClient)

    @Provides
    @WearSingleton
    fun provideDoorCommandRepository(
        networkDoorCommandDataSource: NetworkDoorCommandDataSource,
        serverConfigRepository: ServerConfigRepository,
        authRepository: AuthRepository,
    ): DoorCommandRepository =
        NetworkDoorCommandRepository(
            networkDoorCommandDataSource = networkDoorCommandDataSource,
            serverConfigRepository = serverConfigRepository,
            authRepository = authRepository,
        )
}
