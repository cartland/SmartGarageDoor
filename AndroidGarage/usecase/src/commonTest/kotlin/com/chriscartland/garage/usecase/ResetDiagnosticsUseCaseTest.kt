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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeDiagnosticsCountersRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ResetDiagnosticsUseCaseTest {
    @Test
    fun invokeClearsBothStores() =
        runTest {
            val appLogger = FakeAppLoggerRepository()
            val counters = FakeDiagnosticsCountersRepository()
            val useCase = ResetDiagnosticsUseCase(appLogger, counters)

            // Seed both stores so we can prove the reset wiped them.
            appLogger.log("k")
            counters.increment("k")

            useCase()

            assertEquals(1, appLogger.deleteAllCallCount)
            assertEquals(1, counters.resetCallCount)
        }

    @Test
    fun invokeIsIdempotent() =
        runTest {
            val appLogger = FakeAppLoggerRepository()
            val counters = FakeDiagnosticsCountersRepository()
            val useCase = ResetDiagnosticsUseCase(appLogger, counters)

            useCase()
            useCase()

            assertEquals(2, appLogger.deleteAllCallCount)
            assertEquals(2, counters.resetCallCount)
        }
}
