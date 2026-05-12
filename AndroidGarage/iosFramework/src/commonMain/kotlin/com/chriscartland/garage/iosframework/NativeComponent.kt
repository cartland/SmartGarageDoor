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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.chriscartland.garage.data.AuthBridge
import com.chriscartland.garage.data.LocalDoorDataSource
import com.chriscartland.garage.data.MessagingBridge
import com.chriscartland.garage.data.NetworkButtonDataSource
import com.chriscartland.garage.data.NetworkButtonHealthDataSource
import com.chriscartland.garage.data.NetworkConfigDataSource
import com.chriscartland.garage.data.NetworkDoorDataSource
import com.chriscartland.garage.data.NetworkFeatureAllowlistDataSource
import com.chriscartland.garage.data.coroutines.DefaultDispatcherProvider
import com.chriscartland.garage.data.ktor.KtorHttpClientFactory
import com.chriscartland.garage.data.ktor.KtorNetworkButtonDataSource
import com.chriscartland.garage.data.ktor.KtorNetworkButtonHealthDataSource
import com.chriscartland.garage.data.ktor.KtorNetworkConfigDataSource
import com.chriscartland.garage.data.ktor.KtorNetworkDoorDataSource
import com.chriscartland.garage.data.ktor.KtorNetworkFeatureAllowlistDataSource
import com.chriscartland.garage.data.repository.CachedFeatureAllowlistRepository
import com.chriscartland.garage.data.repository.CachedServerConfigRepository
import com.chriscartland.garage.data.repository.FirebaseAuthRepository
import com.chriscartland.garage.data.repository.FirebaseButtonHealthFcmRepository
import com.chriscartland.garage.data.repository.FirebaseDoorFcmRepository
import com.chriscartland.garage.data.repository.NetworkButtonHealthRepository
import com.chriscartland.garage.data.repository.NetworkDoorRepository
import com.chriscartland.garage.data.repository.NetworkRemoteButtonRepository
import com.chriscartland.garage.data.repository.NetworkSnoozeRepository
import com.chriscartland.garage.datalocal.AppDatabase
import com.chriscartland.garage.datalocal.DataStoreAppSettings
import com.chriscartland.garage.datalocal.DataStoreDiagnosticsCounters
import com.chriscartland.garage.datalocal.DataStoreFactory
import com.chriscartland.garage.datalocal.DatabaseFactory
import com.chriscartland.garage.datalocal.DatabaseLocalDoorDataSource
import com.chriscartland.garage.datalocal.RoomAppLoggerRepository
import com.chriscartland.garage.domain.coroutines.AppClock
import com.chriscartland.garage.domain.coroutines.DispatcherProvider
import com.chriscartland.garage.domain.model.AppConfig
import com.chriscartland.garage.domain.repository.AppLoggerRepository
import com.chriscartland.garage.domain.repository.AppSettingsRepository
import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.domain.repository.ButtonHealthFcmRepository
import com.chriscartland.garage.domain.repository.ButtonHealthRepository
import com.chriscartland.garage.domain.repository.DiagnosticsCountersRepository
import com.chriscartland.garage.domain.repository.DoorFcmRepository
import com.chriscartland.garage.domain.repository.DoorRepository
import com.chriscartland.garage.domain.repository.FeatureAllowlistRepository
import com.chriscartland.garage.domain.repository.RemoteButtonRepository
import com.chriscartland.garage.domain.repository.ServerConfigRepository
import com.chriscartland.garage.domain.repository.SnoozeRepository
import com.chriscartland.garage.usecase.AppSettingsUseCase
import com.chriscartland.garage.usecase.AppStartup
import com.chriscartland.garage.usecase.ApplyButtonHealthFcmUseCase
import com.chriscartland.garage.usecase.ButtonHealthFcmSubscriptionManager
import com.chriscartland.garage.usecase.CheckInStalenessManager
import com.chriscartland.garage.usecase.ClearDiagnosticsUseCase
import com.chriscartland.garage.usecase.ComputeButtonHealthDisplayUseCase
import com.chriscartland.garage.usecase.DefaultLiveClock
import com.chriscartland.garage.usecase.DefaultReceiveFcmDoorEventUseCase
import com.chriscartland.garage.usecase.DefaultRegisterFcmUseCase
import com.chriscartland.garage.usecase.DeregisterFcmUseCase
import com.chriscartland.garage.usecase.FcmRegistrationManager
import com.chriscartland.garage.usecase.FetchButtonHealthUseCase
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import com.chriscartland.garage.usecase.FetchFcmStatusUseCase
import com.chriscartland.garage.usecase.FetchRecentDoorEventsUseCase
import com.chriscartland.garage.usecase.FetchSnoozeStatusUseCase
import com.chriscartland.garage.usecase.GetAuthTokenForCopyUseCase
import com.chriscartland.garage.usecase.InitialDoorFetchManager
import com.chriscartland.garage.usecase.LiveClock
import com.chriscartland.garage.usecase.LogAppEventUseCase
import com.chriscartland.garage.usecase.ObserveAuthStateUseCase
import com.chriscartland.garage.usecase.ObserveDiagnosticsCountUseCase
import com.chriscartland.garage.usecase.ObserveDoorEventsUseCase
import com.chriscartland.garage.usecase.ObserveFeatureAccessUseCase
import com.chriscartland.garage.usecase.ObserveSnoozeStateUseCase
import com.chriscartland.garage.usecase.PruneDiagnosticsLogUseCase
import com.chriscartland.garage.usecase.PushRemoteButtonUseCase
import com.chriscartland.garage.usecase.ReceiveFcmDoorEventUseCase
import com.chriscartland.garage.usecase.RegisterFcmUseCase
import com.chriscartland.garage.usecase.RunStartupDiagnosticsMaintenanceUseCase
import com.chriscartland.garage.usecase.SeedDiagnosticsCountersFromRoomUseCase
import com.chriscartland.garage.usecase.SignInWithGoogleUseCase
import com.chriscartland.garage.usecase.SignOutUseCase
import com.chriscartland.garage.usecase.SnoozeNotificationsUseCase
import com.chriscartland.garage.viewmodel.DefaultDiagnosticsViewModel
import com.chriscartland.garage.viewmodel.DefaultDoorHistoryViewModel
import com.chriscartland.garage.viewmodel.DefaultFunctionListViewModel
import com.chriscartland.garage.viewmodel.DefaultHomeViewModel
import com.chriscartland.garage.viewmodel.DefaultProfileViewModel
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.Clock
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.annotations.Scope

@Scope
annotation class SharedSingleton

/**
 * Root kotlin-inject component for iOS.
 *
 * Mirrors the Android `:androidApp`'s `AppComponent` shape — same
 * dependency graph, same `@SharedSingleton`-scoped repositories /
 * managers — but with platform deps (`AuthBridge`, `MessagingBridge`,
 * `DatabaseFactory`, `DataStoreFactory`, `AppConfig`, `appVersion`)
 * supplied via constructor instead of read from `Application` +
 * `BuildConfig`.
 *
 * Construction happens in [IosNativeHelper] (iosShared), which is the
 * single entry point Swift calls to materialize the DI graph.
 *
 * **Singleton discipline (mirrors `AppComponent.kt` rules):**
 *   1. Every `@SharedSingleton` provider must be reachable via an
 *      abstract entry point — otherwise kotlin-inject skips the cache
 *      and constructs fresh instances per access (the android/170
 *      regression class of bug).
 *   2. Every `@Provides fun` body declares its deps as parameters —
 *      never call sibling `provide*()` inside the body.
 *   3. ViewModels are non-singleton (per-screen-instance by design).
 */
@Component
@SharedSingleton
abstract class NativeComponent(
    @get:Provides val authBridge: AuthBridge,
    @get:Provides val messagingBridge: MessagingBridge,
    @get:Provides val databaseFactory: DatabaseFactory,
    @get:Provides val dataStoreFactory: DataStoreFactory,
    @get:Provides val appConfig: AppConfig,
    @get:Provides val appVersion: String,
) {
    // --- Entry points: ViewModels (per-screen, NOT singleton) ---
    abstract val diagnosticsViewModel: DefaultDiagnosticsViewModel
    abstract val functionListViewModel: DefaultFunctionListViewModel
    abstract val homeViewModel: DefaultHomeViewModel
    abstract val doorHistoryViewModel: DefaultDoorHistoryViewModel
    abstract val profileViewModel: DefaultProfileViewModel

    // --- Entry points: @SharedSingleton state owners ---
    abstract val appStartup: AppStartup
    abstract val applicationScope: CoroutineScope
    abstract val httpClient: HttpClient
    abstract val appDatabase: AppDatabase
    abstract val appSettings: AppSettingsRepository
    abstract val appLoggerRepository: AppLoggerRepository
    abstract val diagnosticsCountersRepository: DiagnosticsCountersRepository
    abstract val authRepository: AuthRepository
    abstract val doorRepository: DoorRepository
    abstract val serverConfigRepository: ServerConfigRepository
    abstract val snoozeRepository: SnoozeRepository
    abstract val remoteButtonRepository: RemoteButtonRepository
    abstract val doorFcmRepository: DoorFcmRepository
    abstract val featureAllowlistRepository: FeatureAllowlistRepository
    abstract val fcmRegistrationManager: FcmRegistrationManager
    abstract val checkInStalenessManager: CheckInStalenessManager
    abstract val liveClock: LiveClock
    abstract val receiveFcmDoorEventUseCase: ReceiveFcmDoorEventUseCase
    abstract val appClock: AppClock
    abstract val dispatcherProvider: DispatcherProvider
    abstract val networkButtonDataSource: NetworkButtonDataSource
    abstract val networkButtonHealthDataSource: NetworkButtonHealthDataSource
    abstract val networkConfigDataSource: NetworkConfigDataSource
    abstract val networkDoorDataSource: NetworkDoorDataSource
    abstract val networkFeatureAllowlistDataSource: NetworkFeatureAllowlistDataSource
    abstract val localDoorDataSource: LocalDoorDataSource
    abstract val buttonHealthRepository: ButtonHealthRepository
    abstract val buttonHealthFcmRepository: ButtonHealthFcmRepository
    abstract val buttonHealthFcmSubscriptionManager: ButtonHealthFcmSubscriptionManager
    abstract val applyButtonHealthFcmUseCase: ApplyButtonHealthFcmUseCase
    abstract val initialDoorFetchManager: InitialDoorFetchManager
    abstract val computeButtonHealthDisplayUseCase: ComputeButtonHealthDisplayUseCase
    abstract val getAuthTokenForCopyUseCase: GetAuthTokenForCopyUseCase

    // --- ViewModels ---

    @Provides
    fun provideDiagnosticsViewModel(
        observeAppLogCount: ObserveDiagnosticsCountUseCase,
        clearDiagnostics: ClearDiagnosticsUseCase,
        dispatchers: DispatcherProvider,
    ): DefaultDiagnosticsViewModel = DefaultDiagnosticsViewModel(observeAppLogCount, clearDiagnostics, dispatchers)

    @Provides
    fun provideFunctionListViewModel(
        pushRemoteButton: PushRemoteButtonUseCase,
        fetchCurrentDoorEvent: FetchCurrentDoorEventUseCase,
        fetchRecentDoorEvents: FetchRecentDoorEventsUseCase,
        fetchSnoozeStatus: FetchSnoozeStatusUseCase,
        fetchButtonHealth: FetchButtonHealthUseCase,
        snoozeNotifications: SnoozeNotificationsUseCase,
        signInWithGoogle: SignInWithGoogleUseCase,
        signOut: SignOutUseCase,
        observeDoorEvents: ObserveDoorEventsUseCase,
        observeFeatureAccess: ObserveFeatureAccessUseCase,
        clearDiagnostics: ClearDiagnosticsUseCase,
        pruneDiagnosticsLog: PruneDiagnosticsLogUseCase,
        registerFcm: RegisterFcmUseCase,
        deregisterFcm: DeregisterFcmUseCase,
        dispatchers: DispatcherProvider,
        appVersion: String,
    ): DefaultFunctionListViewModel =
        DefaultFunctionListViewModel(
            pushRemoteButtonUseCase = pushRemoteButton,
            fetchCurrentDoorEventUseCase = fetchCurrentDoorEvent,
            fetchRecentDoorEventsUseCase = fetchRecentDoorEvents,
            fetchSnoozeStatusUseCase = fetchSnoozeStatus,
            fetchButtonHealthUseCase = fetchButtonHealth,
            snoozeNotificationsUseCase = snoozeNotifications,
            signInWithGoogleUseCase = signInWithGoogle,
            signOutUseCase = signOut,
            observeDoorEventsUseCase = observeDoorEvents,
            observeFeatureAccessUseCase = observeFeatureAccess,
            clearDiagnosticsUseCase = clearDiagnostics,
            pruneDiagnosticsLogUseCase = pruneDiagnosticsLog,
            registerFcmUseCase = registerFcm,
            deregisterFcmUseCase = deregisterFcm,
            dispatchers = dispatchers,
            appVersion = appVersion,
        )

    @Provides
    fun provideHomeViewModel(
        observeDoorEvents: ObserveDoorEventsUseCase,
        observeAuthState: ObserveAuthStateUseCase,
        logAppEvent: LogAppEventUseCase,
        dispatchers: DispatcherProvider,
        fetchCurrentDoorEvent: FetchCurrentDoorEventUseCase,
        fetchButtonHealth: FetchButtonHealthUseCase,
        deregisterFcm: DeregisterFcmUseCase,
        signInWithGoogle: SignInWithGoogleUseCase,
        pushRemoteButton: PushRemoteButtonUseCase,
        checkInStalenessManager: CheckInStalenessManager,
        liveClock: LiveClock,
        computeButtonHealthDisplay: ComputeButtonHealthDisplayUseCase,
        appVersion: String,
    ): DefaultHomeViewModel =
        DefaultHomeViewModel(
            observeDoorEvents = observeDoorEvents,
            observeAuthState = observeAuthState,
            logAppEvent = logAppEvent,
            dispatchers = dispatchers,
            fetchCurrentDoorEventUseCase = fetchCurrentDoorEvent,
            fetchButtonHealthUseCase = fetchButtonHealth,
            deregisterFcmUseCase = deregisterFcm,
            signInWithGoogleUseCase = signInWithGoogle,
            pushRemoteButtonUseCase = pushRemoteButton,
            checkInStalenessManager = checkInStalenessManager,
            liveClock = liveClock,
            buttonHealthDisplay = computeButtonHealthDisplay(),
            appVersion = appVersion,
        )

    @Provides
    fun provideProfileViewModel(
        observeAuthState: ObserveAuthStateUseCase,
        observeSnoozeState: ObserveSnoozeStateUseCase,
        observeDoorEvents: ObserveDoorEventsUseCase,
        observeFeatureAccess: ObserveFeatureAccessUseCase,
        signInWithGoogle: SignInWithGoogleUseCase,
        signOut: SignOutUseCase,
        fetchSnoozeStatus: FetchSnoozeStatusUseCase,
        snoozeNotifications: SnoozeNotificationsUseCase,
        logAppEvent: LogAppEventUseCase,
        appSettings: AppSettingsUseCase,
        dispatchers: DispatcherProvider,
    ): DefaultProfileViewModel =
        DefaultProfileViewModel(
            observeAuthState = observeAuthState,
            observeSnoozeState = observeSnoozeState,
            observeDoorEvents = observeDoorEvents,
            observeFeatureAccessUseCase = observeFeatureAccess,
            signInWithGoogleUseCase = signInWithGoogle,
            signOutUseCase = signOut,
            fetchSnoozeStatusUseCase = fetchSnoozeStatus,
            snoozeNotificationsUseCase = snoozeNotifications,
            logAppEvent = logAppEvent,
            appSettings = appSettings,
            dispatchers = dispatchers,
        )

    @Provides
    fun provideDoorHistoryViewModel(
        observeDoorEvents: ObserveDoorEventsUseCase,
        logAppEvent: LogAppEventUseCase,
        dispatchers: DispatcherProvider,
        fetchRecentDoorEvents: FetchRecentDoorEventsUseCase,
        deregisterFcm: DeregisterFcmUseCase,
        checkInStalenessManager: CheckInStalenessManager,
        liveClock: LiveClock,
    ): DefaultDoorHistoryViewModel =
        DefaultDoorHistoryViewModel(
            observeDoorEvents = observeDoorEvents,
            logAppEvent = logAppEvent,
            dispatchers = dispatchers,
            fetchRecentDoorEventsUseCase = fetchRecentDoorEvents,
            deregisterFcmUseCase = deregisterFcm,
            checkInStalenessManager = checkInStalenessManager,
            liveClock = liveClock,
        )

    // --- UseCases ---

    @Provides
    fun providePruneDiagnosticsLogUseCase(appLoggerRepository: AppLoggerRepository): PruneDiagnosticsLogUseCase =
        PruneDiagnosticsLogUseCase(appLoggerRepository)

    @Provides
    fun provideRunStartupDiagnosticsMaintenanceUseCase(
        seed: SeedDiagnosticsCountersFromRoomUseCase,
        prune: PruneDiagnosticsLogUseCase,
    ): RunStartupDiagnosticsMaintenanceUseCase = RunStartupDiagnosticsMaintenanceUseCase(seed, prune)

    @Provides
    fun provideGetAuthTokenForCopyUseCase(authRepository: AuthRepository): GetAuthTokenForCopyUseCase =
        GetAuthTokenForCopyUseCase(authRepository)

    @Provides
    fun provideClearDiagnosticsUseCase(
        appLoggerRepository: AppLoggerRepository,
        diagnosticsCounters: DiagnosticsCountersRepository,
    ): ClearDiagnosticsUseCase = ClearDiagnosticsUseCase(appLoggerRepository, diagnosticsCounters)

    @Provides
    fun provideSeedDiagnosticsCountersFromRoomUseCase(
        appLoggerRepository: AppLoggerRepository,
        diagnosticsCounters: DiagnosticsCountersRepository,
    ): SeedDiagnosticsCountersFromRoomUseCase = SeedDiagnosticsCountersFromRoomUseCase(appLoggerRepository, diagnosticsCounters)

    @Provides
    fun provideObserveAuthStateUseCase(authRepository: AuthRepository): ObserveAuthStateUseCase = ObserveAuthStateUseCase(authRepository)

    @Provides
    fun provideSignInWithGoogleUseCase(authRepository: AuthRepository): SignInWithGoogleUseCase = SignInWithGoogleUseCase(authRepository)

    @Provides
    fun provideSignOutUseCase(authRepository: AuthRepository): SignOutUseCase = SignOutUseCase(authRepository)

    @Provides
    fun provideLogAppEventUseCase(
        appLoggerRepository: AppLoggerRepository,
        diagnosticsCounters: DiagnosticsCountersRepository,
    ): LogAppEventUseCase = LogAppEventUseCase(appLoggerRepository, diagnosticsCounters)

    @Provides
    fun provideObserveDiagnosticsCountUseCase(diagnosticsCounters: DiagnosticsCountersRepository): ObserveDiagnosticsCountUseCase =
        ObserveDiagnosticsCountUseCase(diagnosticsCounters)

    @Provides
    fun provideObserveDoorEventsUseCase(doorRepository: DoorRepository): ObserveDoorEventsUseCase = ObserveDoorEventsUseCase(doorRepository)

    @Provides
    fun provideAppSettingsUseCase(appSettings: AppSettingsRepository): AppSettingsUseCase = AppSettingsUseCase(appSettings)

    @Provides
    fun provideFetchCurrentDoorEventUseCase(doorRepository: DoorRepository): FetchCurrentDoorEventUseCase =
        FetchCurrentDoorEventUseCase(doorRepository)

    @Provides
    fun provideFetchRecentDoorEventsUseCase(doorRepository: DoorRepository): FetchRecentDoorEventsUseCase =
        FetchRecentDoorEventsUseCase(doorRepository)

    @Provides
    fun provideFetchFcmStatusUseCase(doorFcmRepository: DoorFcmRepository): FetchFcmStatusUseCase = FetchFcmStatusUseCase(doorFcmRepository)

    @Provides
    fun provideRegisterFcmUseCase(
        doorRepository: DoorRepository,
        doorFcmRepository: DoorFcmRepository,
    ): RegisterFcmUseCase = DefaultRegisterFcmUseCase(doorRepository, doorFcmRepository)

    @Provides
    fun provideReceiveFcmDoorEventUseCase(
        doorRepository: DoorRepository,
        appLoggerRepository: AppLoggerRepository,
        applicationScope: CoroutineScope,
    ): ReceiveFcmDoorEventUseCase =
        DefaultReceiveFcmDoorEventUseCase(
            doorRepository = doorRepository,
            appLoggerRepository = appLoggerRepository,
            externalScope = applicationScope,
        )

    @Provides
    fun provideDeregisterFcmUseCase(doorFcmRepository: DoorFcmRepository): DeregisterFcmUseCase = DeregisterFcmUseCase(doorFcmRepository)

    @Provides
    fun providePushRemoteButtonUseCase(
        authRepository: AuthRepository,
        remoteButtonRepository: RemoteButtonRepository,
    ): PushRemoteButtonUseCase = PushRemoteButtonUseCase(authRepository, remoteButtonRepository)

    @Provides
    fun provideSnoozeNotificationsUseCase(
        authRepository: AuthRepository,
        snoozeRepository: SnoozeRepository,
    ): SnoozeNotificationsUseCase = SnoozeNotificationsUseCase(authRepository, snoozeRepository)

    @Provides
    fun provideFetchSnoozeStatusUseCase(snoozeRepository: SnoozeRepository): FetchSnoozeStatusUseCase =
        FetchSnoozeStatusUseCase(snoozeRepository)

    @Provides
    fun provideObserveSnoozeStateUseCase(snoozeRepository: SnoozeRepository): ObserveSnoozeStateUseCase =
        ObserveSnoozeStateUseCase(snoozeRepository)

    @Provides
    fun provideObserveFeatureAccessUseCase(featureAllowlistRepository: FeatureAllowlistRepository): ObserveFeatureAccessUseCase =
        ObserveFeatureAccessUseCase(featureAllowlistRepository)

    @Provides
    fun provideFetchButtonHealthUseCase(
        authRepository: AuthRepository,
        buttonHealthRepository: ButtonHealthRepository,
    ): FetchButtonHealthUseCase = FetchButtonHealthUseCase(authRepository, buttonHealthRepository)

    @Provides
    fun provideApplyButtonHealthFcmUseCase(buttonHealthRepository: ButtonHealthRepository): ApplyButtonHealthFcmUseCase =
        ApplyButtonHealthFcmUseCase(buttonHealthRepository)

    @Provides
    @SharedSingleton
    fun provideComputeButtonHealthDisplayUseCase(
        authRepository: AuthRepository,
        buttonHealthRepository: ButtonHealthRepository,
        liveClock: LiveClock,
        applicationScope: CoroutineScope,
    ): ComputeButtonHealthDisplayUseCase =
        ComputeButtonHealthDisplayUseCase(
            authRepository = authRepository,
            buttonHealthRepository = buttonHealthRepository,
            liveClock = liveClock,
            applicationScope = applicationScope,
        )

    // --- @SharedSingleton providers (bodies take parameters so caching is honored) ---

    @Provides
    @SharedSingleton
    fun providePreferencesDataStore(factory: DataStoreFactory): DataStore<Preferences> = factory.createPreferencesDataStore()

    @Provides
    @SharedSingleton
    fun provideAppSettings(dataStore: DataStore<Preferences>): AppSettingsRepository = DataStoreAppSettings(dataStore)

    @Provides
    @SharedSingleton
    fun provideDiagnosticsCountersRepository(factory: DataStoreFactory): DiagnosticsCountersRepository =
        DataStoreDiagnosticsCounters(factory.createDiagnosticsCountersDataStore())

    @Provides
    @SharedSingleton
    fun provideAppDatabase(factory: DatabaseFactory): AppDatabase = factory.createDatabase()

    @Provides
    @SharedSingleton
    fun provideHttpClient(appConfig: AppConfig): HttpClient = KtorHttpClientFactory.create(baseUrl = appConfig.baseUrl, debug = false)

    @Provides
    @SharedSingleton
    fun provideNetworkDoorDataSource(httpClient: HttpClient): NetworkDoorDataSource = KtorNetworkDoorDataSource(httpClient)

    @Provides
    @SharedSingleton
    fun provideNetworkConfigDataSource(httpClient: HttpClient): NetworkConfigDataSource = KtorNetworkConfigDataSource(httpClient)

    @Provides
    @SharedSingleton
    fun provideNetworkButtonDataSource(httpClient: HttpClient): NetworkButtonDataSource = KtorNetworkButtonDataSource(httpClient)

    @Provides
    @SharedSingleton
    fun provideNetworkButtonHealthDataSource(httpClient: HttpClient): NetworkButtonHealthDataSource =
        KtorNetworkButtonHealthDataSource(httpClient)

    @Provides
    @SharedSingleton
    fun provideNetworkFeatureAllowlistDataSource(httpClient: HttpClient): NetworkFeatureAllowlistDataSource =
        KtorNetworkFeatureAllowlistDataSource(httpClient)

    @Provides
    @SharedSingleton
    fun provideLocalDoorDataSource(appDatabase: AppDatabase): LocalDoorDataSource = DatabaseLocalDoorDataSource(appDatabase)

    @Provides
    @SharedSingleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @SharedSingleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()

    @Provides
    @SharedSingleton
    fun provideAppClock(): AppClock = AppClock { Clock.System.now().toEpochMilliseconds() / 1000 }

    @Provides
    @SharedSingleton
    fun provideAppLoggerRepository(
        appDatabase: AppDatabase,
        appVersion: String,
    ): AppLoggerRepository = RoomAppLoggerRepository(appDatabase, appVersion)

    @Provides
    @SharedSingleton
    fun provideAuthRepository(
        authBridge: AuthBridge,
        appLoggerRepository: AppLoggerRepository,
        applicationScope: CoroutineScope,
    ): AuthRepository = FirebaseAuthRepository(authBridge, appLoggerRepository, applicationScope)

    @Provides
    @SharedSingleton
    fun provideServerConfigRepository(
        networkConfigDataSource: NetworkConfigDataSource,
        appConfig: AppConfig,
        applicationScope: CoroutineScope,
    ): ServerConfigRepository = CachedServerConfigRepository(networkConfigDataSource, appConfig.serverConfigKey, applicationScope)

    @Provides
    @SharedSingleton
    fun provideFeatureAllowlistRepository(
        networkFeatureAllowlistDataSource: NetworkFeatureAllowlistDataSource,
        authRepository: AuthRepository,
        applicationScope: CoroutineScope,
    ): FeatureAllowlistRepository =
        CachedFeatureAllowlistRepository(
            networkDataSource = networkFeatureAllowlistDataSource,
            authRepository = authRepository,
            externalScope = applicationScope,
        )

    @Provides
    @SharedSingleton
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
    @SharedSingleton
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
    @SharedSingleton
    fun provideSnoozeRepository(
        networkButtonDataSource: NetworkButtonDataSource,
        serverConfigRepository: ServerConfigRepository,
        authRepository: AuthRepository,
        appConfig: AppConfig,
        applicationScope: CoroutineScope,
    ): SnoozeRepository =
        NetworkSnoozeRepository(
            networkButtonDataSource = networkButtonDataSource,
            serverConfigRepository = serverConfigRepository,
            authRepository = authRepository,
            snoozeNotificationsOption = appConfig.snoozeNotificationsOption,
            currentTimeSeconds = { Clock.System.now().toEpochMilliseconds() / 1000 },
            externalScope = applicationScope,
        )

    @Provides
    @SharedSingleton
    fun provideDoorFcmRepository(
        messagingBridge: MessagingBridge,
        appSettings: AppSettingsRepository,
        appLoggerRepository: AppLoggerRepository,
    ): DoorFcmRepository = FirebaseDoorFcmRepository(messagingBridge, appSettings, appLoggerRepository)

    @Provides
    @SharedSingleton
    fun provideButtonHealthRepository(
        networkButtonHealthDataSource: NetworkButtonHealthDataSource,
        serverConfigRepository: ServerConfigRepository,
        authRepository: AuthRepository,
        applicationScope: CoroutineScope,
    ): ButtonHealthRepository =
        NetworkButtonHealthRepository(
            networkButtonHealthDataSource = networkButtonHealthDataSource,
            serverConfigRepository = serverConfigRepository,
            authRepository = authRepository,
            externalScope = applicationScope,
        )

    @Provides
    @SharedSingleton
    fun provideButtonHealthFcmRepository(messagingBridge: MessagingBridge): ButtonHealthFcmRepository =
        FirebaseButtonHealthFcmRepository(messagingBridge)

    @Provides
    @SharedSingleton
    fun provideButtonHealthFcmSubscriptionManager(
        authRepository: AuthRepository,
        serverConfigRepository: ServerConfigRepository,
        buttonHealthFcmRepository: ButtonHealthFcmRepository,
        fetchButtonHealthUseCase: FetchButtonHealthUseCase,
        applicationScope: CoroutineScope,
        dispatchers: DispatcherProvider,
    ): ButtonHealthFcmSubscriptionManager =
        ButtonHealthFcmSubscriptionManager(
            authRepository = authRepository,
            serverConfigRepository = serverConfigRepository,
            fcmRepository = buttonHealthFcmRepository,
            fetchButtonHealthUseCase = fetchButtonHealthUseCase,
            scope = applicationScope,
            dispatcher = dispatchers.io,
        )

    @Provides
    @SharedSingleton
    fun provideFcmRegistrationManager(
        registerFcm: RegisterFcmUseCase,
        applicationScope: CoroutineScope,
        dispatchers: DispatcherProvider,
    ): FcmRegistrationManager = FcmRegistrationManager(registerFcm, applicationScope, dispatchers.io)

    @Provides
    @SharedSingleton
    fun provideCheckInStalenessManager(
        observeDoorEvents: ObserveDoorEventsUseCase,
        logAppEvent: LogAppEventUseCase,
        applicationScope: CoroutineScope,
        dispatchers: DispatcherProvider,
        appClock: AppClock,
    ): CheckInStalenessManager =
        CheckInStalenessManager(
            observeDoorEvents = observeDoorEvents,
            logAppEvent = logAppEvent,
            scope = applicationScope,
            dispatcher = dispatchers.io,
            clock = appClock,
        )

    @Provides
    @SharedSingleton
    fun provideLiveClock(
        appClock: AppClock,
        applicationScope: CoroutineScope,
        dispatchers: DispatcherProvider,
    ): LiveClock =
        DefaultLiveClock(
            clock = appClock,
            scope = applicationScope,
            dispatcher = dispatchers.io,
        )

    @Provides
    @SharedSingleton
    fun provideInitialDoorFetchManager(
        fetchCurrentDoorEvent: FetchCurrentDoorEventUseCase,
        fetchRecentDoorEvents: FetchRecentDoorEventsUseCase,
        logAppEvent: LogAppEventUseCase,
        applicationScope: CoroutineScope,
        dispatchers: DispatcherProvider,
    ): InitialDoorFetchManager =
        InitialDoorFetchManager(
            fetchCurrentDoorEvent = fetchCurrentDoorEvent,
            fetchRecentDoorEvents = fetchRecentDoorEvents,
            logAppEvent = logAppEvent,
            scope = applicationScope,
            dispatcher = dispatchers.io,
        )

    @Provides
    fun provideAppStartup(
        fcmRegistrationManager: FcmRegistrationManager,
        checkInStalenessManager: CheckInStalenessManager,
        liveClock: LiveClock,
        logAppEvent: LogAppEventUseCase,
        runStartupDiagnosticsMaintenance: RunStartupDiagnosticsMaintenanceUseCase,
        buttonHealthFcmSubscriptionManager: ButtonHealthFcmSubscriptionManager,
        initialDoorFetchManager: InitialDoorFetchManager,
        applicationScope: CoroutineScope,
        dispatchers: DispatcherProvider,
    ): AppStartup =
        AppStartup(
            fcmRegistrationManager = fcmRegistrationManager,
            checkInStalenessManager = checkInStalenessManager,
            liveClock = liveClock,
            logAppEvent = logAppEvent,
            runStartupDiagnosticsMaintenance = runStartupDiagnosticsMaintenance,
            buttonHealthFcmSubscriptionManager = buttonHealthFcmSubscriptionManager,
            initialDoorFetchManager = initialDoorFetchManager,
            externalScope = applicationScope,
            dispatchers = dispatchers,
        )
}
