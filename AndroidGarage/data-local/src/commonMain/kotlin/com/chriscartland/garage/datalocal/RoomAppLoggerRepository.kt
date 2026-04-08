package com.chriscartland.garage.datalocal

import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.model.AppLogEvent
import com.chriscartland.garage.domain.repository.AppLoggerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * KMP-compatible [AppLoggerRepository] backed by Room.
 *
 * Platform-specific concerns (CSV export, Android Context) stay in androidApp.
 * This class handles only storage: log, count, and getAll.
 */
class RoomAppLoggerRepository(
    private val appDatabase: AppDatabase,
    private val appVersion: String,
) : AppLoggerRepository {
    override suspend fun log(key: String) {
        Logger.d { "Logging key: $key" }
        appDatabase.appLoggerDao().insert(
            AppEvent(
                eventKey = key,
                appVersion = appVersion,
            ),
        )
    }

    override fun countKey(key: String): Flow<Long> = appDatabase.appLoggerDao().countKey(key)

    override fun getAll(): Flow<List<AppLogEvent>> =
        appDatabase.appLoggerDao().getAll().map { events ->
            events.map { it.toAppLogEvent() }
        }
}

private fun AppEvent.toAppLogEvent() =
    AppLogEvent(
        eventKey = eventKey,
        timestampMillis = timestamp,
        appVersion = appVersion,
    )
