package com.chriscartland.garage.domain.repository

import com.chriscartland.garage.domain.model.AppLogEvent
import kotlinx.coroutines.flow.Flow

/**
 * Shared interface for application event logging.
 *
 * Platform implementations handle storage (Room on Android, CoreData on iOS).
 */
interface AppLoggerRepository {
    suspend fun log(key: String)

    fun countKey(key: String): Flow<Long>

    /** All logged events, ordered by timestamp ascending. Used for CSV export. */
    fun getAll(): Flow<List<AppLogEvent>>

    /**
     * Trim the log so that no individual `eventKey` retains more than
     * [perKeyLimit] rows. Keeps the most recent rows per key. Intended
     * for one-shot startup cleanup of databases that grew past the cap
     * before the per-write cap was added; the per-write cap (inside
     * [log]) keeps steady-state size bounded going forward.
     *
     * Implementations must reject `perKeyLimit <= 0` — `0` would be an
     * unbounded delete-everything (subquery matches no rows), which is
     * never a valid retention policy. Use a dedicated clear/reset API
     * for that.
     */
    suspend fun pruneToLimit(perKeyLimit: Int)
}
