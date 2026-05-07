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

    /**
     * One-shot recovery for users upgrading from a version where
     * Diagnostics counts came from the Room app-event aggregate query
     * (anything before 2.11.0). On first call, for each `(key, count)`
     * pair: set `counter[key] = max(counter[key], count)`. On every
     * subsequent call: no-op. Returns `true` on the seeding call,
     * `false` thereafter.
     *
     * The `max()` semantics mean the call is also safe to fire on a
     * fresh install (counters already 0, Room rows 0 → no-op) or after
     * a Clear (counters at 0, Room rows back at 0 by design — same
     * no-op). The persistence of the "already seeded" flag prevents
     * the seed from undoing a future Clear if the user upgrades, logs
     * some events, then clears.
     */
    suspend fun seedFromCountsOnce(counts: Map<String, Long>): Boolean
}
