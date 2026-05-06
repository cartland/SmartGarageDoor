package com.chriscartland.garage.testcommon

import com.chriscartland.garage.domain.model.AppLogEvent
import com.chriscartland.garage.domain.repository.AppLoggerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake [AppLoggerRepository] for unit testing.
 *
 * Tracks calls via read-only `loggedKeys` list so tests cannot reset or reorder
 * them mid-test (ADR-017 Rule 5 — call-list pattern).
 */
class FakeAppLoggerRepository : AppLoggerRepository {
    private val _loggedKeys = mutableListOf<String>()
    val loggedKeys: List<String> get() = _loggedKeys

    private val _pruneCalls = mutableListOf<Int>()
    val pruneCalls: List<Int> get() = _pruneCalls

    private val counts = mutableMapOf<String, MutableStateFlow<Long>>()

    override suspend fun log(key: String) {
        _loggedKeys.add(key)
        counts.getOrPut(key) { MutableStateFlow(0L) }.let { it.value++ }
    }

    override fun countKey(key: String): Flow<Long> = counts.getOrPut(key) { MutableStateFlow(0L) }

    override fun getAll(): Flow<List<AppLogEvent>> = MutableStateFlow(emptyList())

    override suspend fun pruneToLimit(perKeyLimit: Int) {
        _pruneCalls.add(perKeyLimit)
    }
}
