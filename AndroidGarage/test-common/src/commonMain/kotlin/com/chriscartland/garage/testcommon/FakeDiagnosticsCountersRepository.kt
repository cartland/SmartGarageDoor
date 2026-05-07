/*
 * Copyright 2024 Chris Cartland. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.chriscartland.garage.testcommon

import com.chriscartland.garage.domain.repository.DiagnosticsCountersRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake for [DiagnosticsCountersRepository]. Increment/reset
 * mutate read-only [incrementCalls] / [resetCallCount] for ADR-017
 * call-list assertions.
 */
class FakeDiagnosticsCountersRepository : DiagnosticsCountersRepository {
    private val counters = mutableMapOf<String, MutableStateFlow<Long>>()

    private val _incrementCalls = mutableListOf<String>()
    val incrementCalls: List<String> get() = _incrementCalls

    private var _resetCallCount: Int = 0
    val resetCallCount: Int get() = _resetCallCount

    override fun observeCount(key: String): Flow<Long> = counters.getOrPut(key) { MutableStateFlow(0L) }

    override suspend fun increment(key: String) {
        _incrementCalls.add(key)
        counters.getOrPut(key) { MutableStateFlow(0L) }.let { it.value += 1L }
    }

    override suspend fun resetAll() {
        _resetCallCount += 1
        counters.values.forEach { it.value = 0L }
    }
}
