package com.chriscartland.garage.usecase.testfakes

import com.chriscartland.garage.domain.model.AppLogEvent
import com.chriscartland.garage.domain.repository.AppLoggerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppLoggerRepository : AppLoggerRepository {
    val loggedKeys = mutableListOf<String>()
    private val counts = mutableMapOf<String, MutableStateFlow<Long>>()

    override suspend fun log(key: String) {
        loggedKeys.add(key)
        counts.getOrPut(key) { MutableStateFlow(0L) }.let { it.value++ }
    }

    override fun countKey(key: String): Flow<Long> = counts.getOrPut(key) { MutableStateFlow(0L) }

    override fun getAll(): Flow<List<AppLogEvent>> = MutableStateFlow(emptyList())
}
