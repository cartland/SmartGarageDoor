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

package com.chriscartland.garage.applogger

import android.util.Log
import com.chriscartland.garage.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface AppLoggerRepository {
    suspend fun log(key: String)
    fun countKey(key: String): Flow<Long>
}

class AppLoggerRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
) : AppLoggerRepository {
    override suspend fun log(key: String) {
        Log.d(TAG, "Logging key: $key")
        appDatabase.appLoggerDao().insert(AppEvent(key))
    }

    override fun countKey(key: String): Flow<Long> {
        return appDatabase.appLoggerDao().countKey(key)
    }
}

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
object AppLoggerRepositoryModule {
    @Provides
    @Singleton
    fun provideAppLoggerRepository(appDatabase: AppDatabase): AppLoggerRepository {
        return AppLoggerRepositoryImpl(appDatabase)
    }
}

private const val TAG = "AppLoggerRepository"
