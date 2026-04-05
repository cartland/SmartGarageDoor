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
import com.chriscartland.garage.applogger.AppLoggerRepository
import com.chriscartland.garage.applogger.AppLoggerRepositoryImpl
import com.chriscartland.garage.applogger.AppLoggerViewModelImpl
import com.chriscartland.garage.auth.AuthRepositoryImpl
import com.chriscartland.garage.auth.AuthViewModelImpl
import com.chriscartland.garage.config.ServerConfigRepository
import com.chriscartland.garage.config.ServerConfigRepositoryImpl
import com.chriscartland.garage.coroutines.DefaultDispatcherProvider
import com.chriscartland.garage.coroutines.DispatcherProvider
import com.chriscartland.garage.data.LocalDoorDataSource
import com.chriscartland.garage.data.NetworkButtonDataSource
import com.chriscartland.garage.data.NetworkConfigDataSource
import com.chriscartland.garage.data.NetworkDoorDataSource
import com.chriscartland.garage.db.AppDatabase
import com.chriscartland.garage.db.DatabaseLocalDoorDataSource
import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.domain.repository.DoorRepository
import com.chriscartland.garage.domain.repository.PushRepository
import com.chriscartland.garage.door.DoorRepositoryImpl
import com.chriscartland.garage.door.DoorViewModelImpl
import com.chriscartland.garage.fcm.DoorFcmRepository
import com.chriscartland.garage.fcm.DoorFcmRepositoryImpl
import com.chriscartland.garage.internet.KtorNetworkButtonDataSource
import com.chriscartland.garage.internet.KtorNetworkConfigDataSource
import com.chriscartland.garage.internet.KtorNetworkDoorDataSource
import com.chriscartland.garage.internet.provideKtorHttpClient
import com.chriscartland.garage.remotebutton.PushRepositoryImpl
import com.chriscartland.garage.remotebutton.RemoteButtonViewModelImpl
import com.chriscartland.garage.settings.AppSettings
import com.chriscartland.garage.settings.AppSettingsImpl
import com.chriscartland.garage.settings.AppSettingsViewModelImpl
import com.chriscartland.garage.usecase.DeregisterFcmUseCase
import com.chriscartland.garage.usecase.EnsureFreshIdTokenUseCase
import com.chriscartland.garage.usecase.FetchCurrentDoorEventUseCase
import com.chriscartland.garage.usecase.FetchFcmStatusUseCase
import com.chriscartland.garage.usecase.FetchRecentDoorEventsUseCase
import com.chriscartland.garage.usecase.PushRemoteButtonUseCase
import com.chriscartland.garage.usecase.RegisterFcmUseCase
import com.chriscartland.garage.usecase.SnoozeNotificationsUseCase
import io.ktor.client.HttpClient
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
    abstract val appSettingsViewModel: AppSettingsViewModelImpl
    abstract val authViewModel: AuthViewModelImpl
    abstract val doorViewModel: DoorViewModelImpl
    abstract val remoteButtonViewModel: RemoteButtonViewModelImpl
    abstract val appLoggerViewModel: AppLoggerViewModelImpl

    // Settings
    @Provides
    @Singleton
    fun provideAppSettings(): AppSettings = AppSettingsImpl(application)

    // Database
    @Provides
    @Singleton
    fun provideAppDatabase(): AppDatabase = AppDatabase.getDatabase(application)

    // Network — Ktor HTTP client (replacing Retrofit — Phase 4)
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = provideKtorHttpClient()

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
    fun provideAuthRepository(): AuthRepository = AuthRepositoryImpl(provideAppLoggerRepository())

    @Provides
    @Singleton
    fun provideDoorRepository(): DoorRepository =
        DoorRepositoryImpl(provideLocalDoorDataSource(), provideNetworkDoorDataSource(), provideServerConfigRepository())

    @Provides
    @Singleton
    fun provideServerConfigRepository(): ServerConfigRepository = ServerConfigRepositoryImpl(provideNetworkConfigDataSource())

    @Provides
    @Singleton
    fun provideDoorFcmRepository(): DoorFcmRepository = DoorFcmRepositoryImpl(provideAppSettings(), provideAppLoggerRepository())

    @Provides
    @Singleton
    fun provideAppLoggerRepository(): AppLoggerRepository = AppLoggerRepositoryImpl(application.applicationContext, provideAppDatabase())

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
    fun provideEnsureFreshIdTokenUseCase(): EnsureFreshIdTokenUseCase = EnsureFreshIdTokenUseCase()

    @Provides
    fun providePushRemoteButtonUseCase(): PushRemoteButtonUseCase = PushRemoteButtonUseCase(provideEnsureFreshIdTokenUseCase())

    @Provides
    fun provideSnoozeNotificationsUseCase(): SnoozeNotificationsUseCase = SnoozeNotificationsUseCase(provideEnsureFreshIdTokenUseCase())

    // Network — button
    @Provides
    @Singleton
    fun provideNetworkButtonDataSource(): NetworkButtonDataSource = KtorNetworkButtonDataSource(provideHttpClient())

    // Repositories — push
    @Provides
    @Singleton
    fun providePushRepository(): PushRepository = PushRepositoryImpl(provideNetworkButtonDataSource(), provideServerConfigRepository())

    // Coroutines
    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()
}
