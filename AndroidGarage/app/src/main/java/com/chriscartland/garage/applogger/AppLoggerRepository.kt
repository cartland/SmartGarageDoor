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

import android.content.Context
import android.net.Uri
import android.util.Log
import com.chriscartland.garage.db.AppDatabase
import com.chriscartland.garage.version.AppVersion
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

interface AppLoggerRepository {
    suspend fun log(key: String)
    suspend fun writeFileToUri(context: Context, uri: Uri)
    fun countKey(key: String): Flow<Long>
}

class AppLoggerRepositoryImpl @Inject constructor(
    private val context: Context,
    private val appDatabase: AppDatabase,
) : AppLoggerRepository {
    override suspend fun log(key: String) {
        Log.d(TAG, "Logging key: $key")
        appDatabase.appLoggerDao().insert(
            AppEvent(
                eventKey = key,
                appVersion = context.AppVersion().toString(),
            )
        )
    }

    override fun countKey(key: String): Flow<Long> {
        return appDatabase.appLoggerDao().countKey(key)
    }

    override suspend fun writeFileToUri(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write("Timestamp,Key\n".toByteArray())
                    appDatabase.appLoggerDao().getAll().first().forEach {
                        outputStream.write(
                            (
                                    "${it.timestamp}" +
                                            ",${it.timestamp.readableTime()}" +
                                            ",${it.appVersion}" +
                                            ",${it.eventKey}" +
                                            "\n"
                                    ).toByteArray()
                        )
                    }
                }
            } catch (e: Exception) {
                // Handle exceptions (e.g., file I/O errors)
                Log.d("CreateTxtFile", "Error writing to file: ${e.message}")
            }
        }
    }

    private fun Long.readableTime(): String {
        val instant = Instant.ofEpochMilli(this)
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss.SSS")
            .withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }
}

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
object AppLoggerRepositoryModule {
    @Provides
    @Singleton
    fun provideAppLoggerRepository(
        context: Context,
        appDatabase: AppDatabase,
    ): AppLoggerRepository {
        return AppLoggerRepositoryImpl(context, appDatabase)
    }
}

private const val TAG = "AppLoggerRepository"
