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
import com.chriscartland.garage.auth.AuthRepositoryImpl
import com.chriscartland.garage.auth.AuthViewModelImpl
import com.chriscartland.garage.coroutines.DefaultDispatcherProvider
import com.chriscartland.garage.coroutines.DispatcherProvider
import com.chriscartland.garage.db.AppDatabase
import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.settings.AppSettings
import com.chriscartland.garage.settings.AppSettingsImpl
import com.chriscartland.garage.settings.AppSettingsViewModelImpl
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

    // Settings
    @Provides
    @Singleton
    fun provideAppSettings(): AppSettings = AppSettingsImpl(application)

    // Database
    @Provides
    @Singleton
    fun provideAppDatabase(): AppDatabase = AppDatabase.getDatabase(application)

    // Repositories
    @Provides
    @Singleton
    fun provideAuthRepository(): AuthRepository = AuthRepositoryImpl(provideAppLoggerRepository())

    @Provides
    @Singleton
    fun provideAppLoggerRepository(): AppLoggerRepository = AppLoggerRepositoryImpl(application.applicationContext, provideAppDatabase())

    // Coroutines
    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()
}
