package com.chriscartland.garage.datalocal

import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.model.AppLogEvent
import com.chriscartland.garage.domain.model.AppLoggerLimits
import com.chriscartland.garage.domain.repository.AppLoggerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * KMP-compatible [AppLoggerRepository] backed by Room.
 *
 * Platform-specific concerns (CSV export, Android Context) stay in
 * androidApp. This class handles only storage: log, count, getAll, and
 * the per-write retention cap.
 *
 * Each [log] call inserts a row and immediately prunes the per-key
 * row set to at most [perKeyLimit] entries (single Room transaction),
 * so the table stays bounded in steady state. To clean up databases
 * that pre-date the cap, call [pruneToLimit] once on app startup.
 */
class RoomAppLoggerRepository(
    private val appDatabase: AppDatabase,
    private val appVersion: String,
    private val perKeyLimit: Int = AppLoggerLimits.DEFAULT_PER_KEY_LIMIT,
) : AppLoggerRepository {
    override suspend fun log(key: String) {
        Logger.d { "Logging key: $key" }
        appDatabase.appLoggerDao().insertAndPruneKey(
            appEvent = AppEvent(
                eventKey = key,
                appVersion = appVersion,
            ),
            limit = perKeyLimit,
        )
    }

    override fun countKey(key: String): Flow<Long> = appDatabase.appLoggerDao().countKey(key)

    override fun getAll(): Flow<List<AppLogEvent>> =
        appDatabase.appLoggerDao().getAll().map { events ->
            events.map { it.toAppLogEvent() }
        }

    override suspend fun pruneToLimit(perKeyLimit: Int) {
        require(perKeyLimit > 0) { "perKeyLimit must be > 0; got $perKeyLimit" }
        appDatabase.appLoggerDao().pruneAllKeys(perKeyLimit)
    }

    override suspend fun deleteAll() {
        appDatabase.appLoggerDao().deleteAllAppEvents()
    }
}

private fun AppEvent.toAppLogEvent() =
    AppLogEvent(
        eventKey = eventKey,
        timestampMillis = timestamp,
        appVersion = appVersion,
    )
