package com.chriscartland.garage.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Monotonic lifetime counters for the Diagnostics screen, keyed by
 * `AppLoggerKeys` strings. Independent of the Room app-event log
 * (which is a capped rolling buffer for CSV export, see
 * [AppLoggerRepository]) — counts here keep climbing past the per-key
 * row cap so users can see lifetime totals like "FCM received: 47,231".
 *
 * Reset by the user via "Clear all diagnostics" on the Diagnostics
 * screen. Not backed up across reinstalls — uninstall = reset = 0.
 *
 * Implementation note: lives in its own platform store (DataStore on
 * Android, equivalent on iOS) so that [resetAll] cannot accidentally
 * clear unrelated app preferences.
 */
interface DiagnosticsCountersRepository {
    /** Lifetime count for [key]. Reactive via [Flow]. */
    fun observeCount(key: String): Flow<Long>

    /** Atomically increment the lifetime count for [key] by 1. */
    suspend fun increment(key: String)

    /** Zero every counter in this store. Does not touch other stores. */
    suspend fun resetAll()
}
