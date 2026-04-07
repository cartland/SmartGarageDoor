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

import android.app.Application
import com.chriscartland.garage.BuildConfig
import com.chriscartland.garage.applogger.AndroidAppLoggerRepository
import com.chriscartland.garage.applogger.RoomAppLoggerRepository
import com.chriscartland.garage.auth.FirebaseAuthBridge
import com.chriscartland.garage.config.APP_CONFIG
import com.chriscartland.garage.data.AuthBridge
import com.chriscartland.garage.data.LocalDoorDataSource
import com.chriscartland.garage.data.MessagingBridge
import com.chriscartland.garage.data.NetworkButtonDataSource
import com.chriscartland.garage.data.NetworkConfigDataSource
import com.chriscartland.garage.data.NetworkDoorDataSource
import com.chriscartland.garage.data.coroutines.DefaultDispatcherProvider
import com.chriscartland.garage.data.ktor.KtorNetworkButtonDataSource
import com.chriscartland.garage.data.ktor.KtorNetworkConfigDataSource
import com.chriscartland.garage.data.ktor.KtorNetworkDoorDataSource
import com.chriscartland.garage.data.ktor.createHttpClient
import com.chriscartland.garage.data.repository.CachedServerConfigRepository
import com.chriscartland.garage.data.repository.FirebaseAuthRepository
import com.chriscartland.garage.data.repository.FirebaseDoorFcmRepository
import com.chriscartland.garage.data.repository.NetworkDoorRepository
import com.chriscartland.garage.data.repository.NetworkRemoteButtonRepository
import com.chriscartland.garage.data.repository.NetworkSnoozeRepository
import com.chriscartland.garage.datalocal.AppDatabase
import com.chriscartland.garage.datalocal.DataStoreSettingsFactory
import com.chriscartland.garage.datalocal.DatabaseFactory
import com.chriscartland.garage.datalocal.DatabaseLocalDoorDataSource
import com.chriscartland.garage.domain.coroutines.DispatcherProvider
import com.chriscartland.garage.domain.repository.AppLoggerRepository
import com.chriscartland.garage.domain.repository.AppSettingsRepository
import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.domain.repository.DoorFcmRepository
import com.chriscartland.garage.domain.repository.DoorRepository
import com.chriscartland.garage.domain.repository.RemoteButtonRepository
import com.chriscartland.garage.domain.repository.ServerConfigRepository
import com.chriscartland.garage.domain.repository.SnoozeRepository
import com.chriscartland.garage.fcm.FirebaseMessagingBridge
import com.chriscartland.garage.usecase.DefaultAppLoggerViewModel
import com.chriscartland.garage.usecase.DefaultAppSettingsViewModel
import com.chriscartland.garage.usecase.DefaultAuthViewModel
import com.chriscartland.garage.usecase.DefaultDoorViewModel
import com.chriscartland.garage.usecase.DefaultRemoteButtonViewModel
import com.chriscartland.garage.usecase.DeregisterFcmUseCase
import com.chriscartland.garage.usecase.EnsureFreshIdTokenUseCase
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import com.chriscartland.garage.usecase.FetchFcmStatusUseCase
import com.chriscartland.garage.usecase.FetchRecentDoorEventsUseCase
import com.chriscartland.garage.usecase.PushRemoteButtonUseCase
import com.chriscartland.garage.usecase.RegisterFcmUseCase
import com.chriscartland.garage.usecase.SnoozeNotificationsUseCase
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides

/**
 * Root kotlin-inject component.
 *
 * Dependencies are added here as ViewModels and Repositories
 * are migrated from Hilt. See docs/DI-MIGRATION.md for the plan.
 */
@Component
@Singleton
abstract class AppComponent(
    @get:Provides val application: Application,
) {
    // ViewModels
    val authViewModel: DefaultAuthViewModel
        @Provides get() = DefaultAuthViewModel(
            provideAuthRepository(),
            provideAppLoggerRepository(),
            provideDispatcherProvider(),
        )

    val appLoggerViewModel: DefaultAppLoggerViewModel
        @Provides get() = DefaultAppLoggerViewModel(
            provideAppLoggerRepository(),
            provideDispatcherProvider(),
        )

    val appSettingsViewModel: DefaultAppSettingsViewModel
        @Provides get() = DefaultAppSettingsViewModel(provideAppSettings())

    val doorViewModel: DefaultDoorViewModel
        @Provides get() = DefaultDoorViewModel(
            provideAppLoggerRepository(),
            provideDoorRepository(),
            provideDispatcherProvider(),
            provideFetchCurrentDoorEventUseCase(),
            provideFetchRecentDoorEventsUseCase(),
            provideFetchFcmStatusUseCase(),
            provideRegisterFcmUseCase(),
            provideDeregisterFcmUseCase(),
        )

    val remoteButtonViewModel: DefaultRemoteButtonViewModel
        @Provides get() = DefaultRemoteButtonViewModel(
            provideRemoteButtonRepository(),
            provideSnoozeRepository(),
            provideDoorRepository(),
            provideDispatcherProvider(),
            providePushRemoteButtonUseCase(),
            provideSnoozeNotificationsUseCase(),
        )

    // Settings
    @Provides
    @Singleton
    fun provideAppSettings(): AppSettingsRepository = DataStoreSettingsFactory.create(application)

    // Database
    @Provides
    @Singleton
    fun provideAppDatabase(): AppDatabase = DatabaseFactory.getDatabase(application)

    // Network — Ktor HTTP client
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient =
        createHttpClient(
            baseUrl = APP_CONFIG.baseUrl,
            debug = BuildConfig.DEBUG,
        )

    @Provides
    @Singleton
    fun provideNetworkDoorDataSource(): NetworkDoorDataSource = KtorNetworkDoorDataSource(provideHttpClient())

    @Provides
    @Singleton
    fun provideNetworkConfigDataSource(): NetworkConfigDataSource = KtorNetworkConfigDataSource(provideHttpClient())

    // Local data
    @Provides
    @Singleton
    fun provideLocalDoorDataSource(): LocalDoorDataSource = DatabaseLocalDoorDataSource(provideAppDatabase())

    // Repositories
    @Provides
    @Singleton
    fun provideAuthBridge(): AuthBridge = FirebaseAuthBridge()

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideAuthRepository(): AuthRepository =
        FirebaseAuthRepository(provideAuthBridge(), provideAppLoggerRepository(), provideApplicationScope())

    @Provides
    @Singleton
    fun provideDoorRepository(): DoorRepository =
        NetworkDoorRepository(
            provideLocalDoorDataSource(),
            provideNetworkDoorDataSource(),
            provideServerConfigRepository(),
            APP_CONFIG.recentEventCount,
        )

    @Provides
    @Singleton
    fun provideServerConfigRepository(): ServerConfigRepository =
        CachedServerConfigRepository(provideNetworkConfigDataSource(), APP_CONFIG.serverConfigKey)

    @Provides
    @Singleton
    fun provideMessagingBridge(): MessagingBridge = FirebaseMessagingBridge()

    @Provides
    @Singleton
    fun provideDoorFcmRepository(): DoorFcmRepository =
        FirebaseDoorFcmRepository(provideMessagingBridge(), provideAppSettings(), provideAppLoggerRepository())

    @Provides
    @Singleton
    fun provideAndroidAppLoggerRepository(): AndroidAppLoggerRepository =
        RoomAppLoggerRepository(application.applicationContext, provideAppDatabase())

    @Provides
    @Singleton
    fun provideAppLoggerRepository(): AppLoggerRepository = provideAndroidAppLoggerRepository()

    // UseCases
    @Provides
    fun provideFetchCurrentDoorEventUseCase(): FetchCurrentDoorEventUseCase = FetchCurrentDoorEventUseCase(provideDoorRepository())

    @Provides
    fun provideFetchRecentDoorEventsUseCase(): FetchRecentDoorEventsUseCase = FetchRecentDoorEventsUseCase(provideDoorRepository())

    @Provides
    fun provideFetchFcmStatusUseCase(): FetchFcmStatusUseCase = FetchFcmStatusUseCase(provideDoorFcmRepository())

    @Provides
    fun provideRegisterFcmUseCase(): RegisterFcmUseCase = RegisterFcmUseCase(provideDoorRepository(), provideDoorFcmRepository())

    @Provides
    fun provideDeregisterFcmUseCase(): DeregisterFcmUseCase = DeregisterFcmUseCase(provideDoorFcmRepository())

    @Provides
    fun provideEnsureFreshIdTokenUseCase(): EnsureFreshIdTokenUseCase = EnsureFreshIdTokenUseCase(provideAuthRepository())

    @Provides
    fun providePushRemoteButtonUseCase(): PushRemoteButtonUseCase =
        PushRemoteButtonUseCase(provideEnsureFreshIdTokenUseCase(), provideAuthRepository(), provideRemoteButtonRepository())

    @Provides
    fun provideSnoozeNotificationsUseCase(): SnoozeNotificationsUseCase =
        SnoozeNotificationsUseCase(provideEnsureFreshIdTokenUseCase(), provideAuthRepository(), provideSnoozeRepository())

    // Network — button + snooze data source
    @Provides
    @Singleton
    fun provideNetworkButtonDataSource(): NetworkButtonDataSource = KtorNetworkButtonDataSource(provideHttpClient())

    // Repositories — remote button
    @Provides
    @Singleton
    fun provideRemoteButtonRepository(): RemoteButtonRepository =
        NetworkRemoteButtonRepository(
            provideNetworkButtonDataSource(),
            provideServerConfigRepository(),
            APP_CONFIG.remoteButtonPushEnabled,
        )

    // Repositories — snooze
    @Provides
    @Singleton
    fun provideSnoozeRepository(): SnoozeRepository =
        NetworkSnoozeRepository(
            provideNetworkButtonDataSource(),
            provideServerConfigRepository(),
            APP_CONFIG.snoozeNotificationsOption,
        )

    // Coroutines
    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()
}
