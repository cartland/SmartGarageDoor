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
)
