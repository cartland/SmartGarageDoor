package com.chriscartland.garage.domain.model

/**
 * Domain representation of an app log event.
 *
 * Decoupled from Room's [AppEvent][com.chriscartland.garage.datalocal.AppEvent] entity.
 * Used by the Android layer for CSV export and any future log viewing UI.
 */
data class AppLogEvent(
    val eventKey: String,
    val timestampMillis: Long,
    val appVersion: String,
) {
    /** CSV row for export. The header + assembly live in [AppLogCsv]. */
    fun toCsvRow(): String = "$eventKey,$timestampMillis,$appVersion\n"
}

/**
 * Builds the app-log export CSV — the **single source of truth for the export
 * format**, shared so Android (writes the bytes to a user-chosen content `Uri`)
 * and iOS (shares the file via a share sheet) produce byte-identical output.
 * Per-row formatting is [AppLogEvent.toCsvRow]; this object owns the header + the
 * row assembly that was previously duplicated in the Android writer.
 */
object AppLogCsv {
    /** Header row (trailing newline matches [AppLogEvent.toCsvRow]). */
    const val HEADER: String = "Key,Epoch,Version\n"

    /** Full CSV: [HEADER] followed by one [AppLogEvent.toCsvRow] per event. */
    fun build(events: List<AppLogEvent>): String =
        buildString {
            append(HEADER)
            events.forEach { append(it.toCsvRow()) }
        }
}
