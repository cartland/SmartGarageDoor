/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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

package com.chriscartland.garage.wear.logging

import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.model.AppLogEvent
import com.chriscartland.garage.domain.repository.AppLoggerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * [AppLoggerRepository] that forwards event keys to logcat (kermit) without
 * persisting anything.
 *
 * The Wear app has no diagnostics screen or CSV export, so the Room-backed
 * logger from `:data-local` is unnecessary — but shared repositories
 * (`FirebaseAuthRepository`) still expect an [AppLoggerRepository], and the
 * events remain useful in `adb logcat` during development.
 */
class LogcatAppLoggerRepository : AppLoggerRepository {
    override suspend fun log(key: String) {
        Logger.d { "AppLogEvent: $key" }
    }

    override fun countKey(key: String): Flow<Long> = flowOf(0L)

    override fun getAll(): Flow<List<AppLogEvent>> = flowOf(emptyList())

    override suspend fun pruneToLimit(perKeyLimit: Int) {
        require(perKeyLimit > 0) { "perKeyLimit must be positive, was $perKeyLimit" }
    }

    override suspend fun deleteAll() = Unit
}
