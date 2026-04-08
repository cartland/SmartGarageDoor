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
import com.chriscartland.garage.domain.model.AppLogEvent
import com.chriscartland.garage.domain.repository.AppLoggerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Android-specific logger that extends the shared [AppLoggerRepository]
 * with CSV export capability (requires Android Context and Uri).
 */
interface AndroidAppLoggerRepository : AppLoggerRepository {
    suspend fun writeCsvToUri(
        context: Context,
        uri: Uri,
    )
}

/**
 * Wraps the shared [AppLoggerRepository] and adds Android-specific CSV export.
 */
class AndroidAppLoggerRepositoryImpl(
    private val delegate: AppLoggerRepository,
) : AndroidAppLoggerRepository,
    AppLoggerRepository by delegate {
    override suspend fun writeCsvToUri(
        context: Context,
        uri: Uri,
    ) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write("Key,Time,Epoch,Version\n".toByteArray())
                    delegate.getAll().firstOrNull()?.forEach {
                        outputStream.write(it.toCsvRow().toByteArray())
                    }
                }
            } catch (e: java.io.IOException) {
                Logger.d { "Error writing to file: ${e.message}" }
            }
        }
    }
}

private fun AppLogEvent.toCsvRow(): String {
    val formatter = DateTimeFormatter
        .ofPattern("yyyyMMdd.HHmmss.SSS")
        .withZone(ZoneId.systemDefault())
    val readableTime = formatter.format(Instant.ofEpochMilli(timestampMillis))
    return "$eventKey,$readableTime,$timestampMillis,$appVersion\n"
}
