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

package com.chriscartland.garage.testcommon

import com.chriscartland.garage.domain.model.WatchAppStatus
import com.chriscartland.garage.domain.model.WatchInstallResult
import com.chriscartland.garage.domain.repository.WearCompanionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield

/**
 * Fake [WearCompanionRepository] for unit testing.
 *
 * Configure with `setX()` methods; observe calls via read-only counters.
 */
class FakeWearCompanionRepository : WearCompanionRepository {
    private val status = MutableStateFlow<WatchAppStatus>(WatchAppStatus.Unknown)
    private var installResult: WatchInstallResult = WatchInstallResult.OpenedOnWatch
    private var coldSource: Boolean = false

    var installRequestCount: Int = 0
        private set

    /**
     * How many times the cold source has been collected. Only meaningful
     * after [useColdSource]; lets a test assert that a `WhileSubscribed`
     * cache actually stopped and restarted the upstream.
     */
    var observeStartCount: Int = 0
        private set

    fun setWatchAppStatus(value: WatchAppStatus) {
        status.value = value
    }

    fun setInstallResult(value: WatchInstallResult) {
        installResult = value
    }

    /**
     * Switch [observeWatchAppStatus] from the default hot [MutableStateFlow]
     * to a COLD flow whose first value is not available synchronously —
     * the shape `PlayServicesWearCompanionRepository` actually has (it
     * polls Play Services only while collected, and the first result
     * costs an IPC round-trip).
     *
     * The default hot flow cannot reproduce the class of bug that
     * `ObserveWatchAppStatusUseCase` exists to fix, because a
     * `MutableStateFlow` always replays its current value immediately.
     */
    fun useColdSource() {
        coldSource = true
    }

    override fun observeWatchAppStatus(): Flow<WatchAppStatus> =
        if (!coldSource) {
            status
        } else {
            flow {
                observeStartCount++
                // The first poll result is never synchronous.
                yield()
                emitAll(status)
            }
        }

    override suspend fun requestInstallOnWatch(): WatchInstallResult {
        installRequestCount++
        return installResult
    }
}
