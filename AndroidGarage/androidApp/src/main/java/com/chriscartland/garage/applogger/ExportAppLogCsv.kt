package com.chriscartland.garage.applogger

import android.content.Context
import android.net.Uri
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Write the already-built export CSV to the user-chosen file [Uri].
 *
 * This is the only Android-specific code for log export — the irreducible
 * platform side-effect ([Context.contentResolver]). The CSV *content* is built
 * by the shared `BuildAppLogCsvUseCase` (via `DiagnosticsViewModel.buildExportCsv`)
 * so the UI no longer reaches the repository directly (ADR-033).
 */
suspend fun writeCsvToUri(
    csv: String,
    context: Context,
    uri: Uri,
) {
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(csv.toByteArray())
            }
        } catch (e: IOException) {
            Logger.d { "Error writing log CSV: ${e.message}" }
        }
    }
}
