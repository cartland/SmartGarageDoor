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

import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeDiagnosticsCountersRepository
import com.chriscartland.garage.testcommon.TestDispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val logger = FakeAppLoggerRepository()
    private val counters = FakeDiagnosticsCountersRepository()
    private val dispatchers = TestDispatcherProvider(testDispatcher)
    private lateinit var viewModel: DefaultDiagnosticsViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = DefaultDiagnosticsViewModel(
            observeAppLogCount = ObserveDiagnosticsCountUseCase(counters),
            clearDiagnosticsUseCase = ClearDiagnosticsUseCase(logger, counters),
            dispatchers = dispatchers,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun countsUpdateAfterCounterIncrements() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            counters.increment(AppLoggerKeys.USER_FETCH_CURRENT_DOOR)
            advanceUntilIdle()

            assertTrue(viewModel.userFetchCurrentDoorCount.value >= 1L)
        }

    @Test
    fun clearDiagnosticsClearsBothStores() =
        runTest(testDispatcher) {
            counters.increment(AppLoggerKeys.FCM_DOOR_RECEIVED)
            advanceUntilIdle()
            check(viewModel.fcmReceivedDoorCount.value > 0L)

            viewModel.clearDiagnostics()
            advanceUntilIdle()

            assertEquals(1, logger.deleteAllCallCount)
            assertEquals(1, counters.resetCallCount)
            // The screen-facing StateFlow must observe the reset, not just
            // the underlying repos.
            assertEquals(0L, viewModel.fcmReceivedDoorCount.value)
        }

    @Test
    fun clearInFlightIsFalseAfterClearCompletes() =
        runTest(testDispatcher) {
            // Verifies the safety property: the finally block always
            // resets the flag, so the button can never get stuck in a
            // "Clearing…" state. The mid-action `true` transition is
            // implementation detail (and conflated by MutableStateFlow
            // when fakes don't suspend) — the screenshot test pins the
            // visual; manual smoke verifies the UX.
            assertEquals(false, viewModel.clearInFlight.value)

            viewModel.clearDiagnostics()
            advanceUntilIdle()

            assertEquals(false, viewModel.clearInFlight.value)
        }
}
