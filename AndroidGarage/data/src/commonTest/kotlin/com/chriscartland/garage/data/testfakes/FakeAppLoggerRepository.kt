package com.chriscartland.garage.data.testfakes

import com.chriscartland.garage.domain.model.AppLogEvent
import com.chriscartland.garage.domain.repository.AppLoggerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppLoggerRepository : AppLoggerRepository {
    val loggedKeys = mutableListOf<String>()

    override suspend fun log(key: String) {
        loggedKeys.add(key)
    }

    override fun countKey(key: String): Flow<Long> = MutableStateFlow(loggedKeys.count { it == key }.toLong())

    override fun getAll(): Flow<List<AppLogEvent>> = MutableStateFlow(emptyList())
}
