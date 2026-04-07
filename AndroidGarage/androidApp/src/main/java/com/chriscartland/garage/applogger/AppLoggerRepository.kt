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
import co.touchlab.kermit.Logger
import com.chriscartland.garage.applogger.model.AppEvent
import com.chriscartland.garage.db.AppDatabase
import com.chriscartland.garage.domain.repository.AppLoggerRepository
import com.chriscartland.garage.version.AppVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Android-specific logger that extends the shared [AppLoggerRepository][com.chriscartland.garage.domain.repository.AppLoggerRepository]
 * with Android-specific CSV export capability.
 */
interface AndroidAppLoggerRepository : AppLoggerRepository {
    suspend fun writeCsvToUri(
        context: Context,
        uri: Uri,
    )
}

class RoomAppLoggerRepository(
    private val context: Context,
    private val appDatabase: AppDatabase,
) : AndroidAppLoggerRepository {
    override suspend fun log(key: String) {
        Logger.d { "Logging key: $key" }
        appDatabase.appLoggerDao().insert(
            AppEvent(
                eventKey = key,
                appVersion = context.AppVersion().toString(),
            ),
        )
    }

    override fun countKey(key: String): Flow<Long> = appDatabase.appLoggerDao().countKey(key)

    override suspend fun writeCsvToUri(
        context: Context,
        uri: Uri,
    ) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write("Key,Time,Epoch,Version\n".toByteArray())
                    appDatabase.appLoggerDao().getAll().firstOrNull()?.forEach {
                        outputStream.write(
                            (
                                "${it.eventKey}" +
                                    ",${it.timestamp.readableTime()}" +
                                    ",${it.timestamp}" +
                                    ",${it.appVersion}" +
                                    "\n"
                            ).toByteArray(),
                        )
                    }
                }
            } catch (e: Exception) {
                // Handle exceptions (e.g., file I/O errors)
                Logger.d { "Error writing to file: ${e.message}" }
            }
        }
    }

    private fun Long.readableTime(): String {
        val instant = Instant.ofEpochMilli(this)
        val formatter =
            DateTimeFormatter
                .ofPattern("yyyyMMdd.HHmmss.SSS")
                .withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }
}
