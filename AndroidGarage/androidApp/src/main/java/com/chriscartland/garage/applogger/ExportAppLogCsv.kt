package com.chriscartland.garage.applogger

import android.content.Context
import android.net.Uri
import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.model.AppLogCsv
import com.chriscartland.garage.domain.repository.AppLoggerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Export all app log events to a CSV file at the given [Uri].
 *
 * This is the only Android-specific code for log export — it uses
 * [Context.contentResolver] to write to the user-chosen file. The CSV content
 * (header + rows) is the shared KMP [AppLogCsv] builder, so Android and iOS
 * produce byte-identical files.
 */
suspend fun exportAppLogCsvToUri(
    appLoggerRepository: AppLoggerRepository,
    context: Context,
    uri: Uri,
) {
    withContext(Dispatchers.IO) {
        try {
            val events = appLoggerRepository.getAll().firstOrNull() ?: emptyList()
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(AppLogCsv.build(events).toByteArray())
            }
        } catch (e: IOException) {
            Logger.d { "Error writing log CSV: ${e.message}" }
        }
    }
}
