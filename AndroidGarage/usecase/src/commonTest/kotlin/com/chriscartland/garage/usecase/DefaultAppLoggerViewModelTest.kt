package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.AppLoggerLimits
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
class DefaultAppLoggerViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val logger = FakeAppLoggerRepository()
    private val counters = FakeDiagnosticsCountersRepository()
    private val dispatchers = TestDispatcherProvider(testDispatcher)
    private lateinit var viewModel: DefaultAppLoggerViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = DefaultAppLoggerViewModel(
            logAppEvent = LogAppEventUseCase(logger, counters),
            observeAppLogCount = ObserveAppLogCountUseCase(counters),
            pruneAppLog = PruneAppLogUseCase(logger),
            resetDiagnosticsUseCase = ResetDiagnosticsUseCase(logger, counters),
            dispatchers = dispatchers,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun logDelegatesToRepository() =
        runTest(testDispatcher) {
            viewModel.log(AppLoggerKeys.USER_FETCH_CURRENT_DOOR)
            advanceUntilIdle()

            assertTrue(logger.loggedKeys.contains(AppLoggerKeys.USER_FETCH_CURRENT_DOOR))
        }

    @Test
    fun logMultipleKeysDelegatesToRepository() =
        runTest(testDispatcher) {
            viewModel.log(AppLoggerKeys.INIT_CURRENT_DOOR)
            viewModel.log(AppLoggerKeys.FCM_DOOR_RECEIVED)
            advanceUntilIdle()

            assertTrue(logger.loggedKeys.contains(AppLoggerKeys.INIT_CURRENT_DOOR))
            assertTrue(logger.loggedKeys.contains(AppLoggerKeys.FCM_DOOR_RECEIVED))
        }

    @Test
    fun countsUpdateAfterLog() =
        runTest(testDispatcher) {
            // Init collects counts — advance to let them start
            advanceUntilIdle()

            // Log a key
            viewModel.log(AppLoggerKeys.USER_FETCH_CURRENT_DOOR)
            advanceUntilIdle()

            // The init block collects countKey flows from the diagnostics
            // counters; logging increments those, so the StateFlow updates.
            assertTrue(viewModel.userFetchCurrentDoorCount.value >= 1L)
        }

    @Test
    fun pruneOldEntriesDelegatesToRepository() =
        runTest(testDispatcher) {
            viewModel.pruneOldEntries(perKeyLimit = 500)
            advanceUntilIdle()

            assertEquals(listOf(500), logger.pruneCalls)
        }

    @Test
    fun pruneOldEntriesUsesDefaultLimit() =
        runTest(testDispatcher) {
            viewModel.pruneOldEntries()
            advanceUntilIdle()

            assertEquals(
                listOf(AppLoggerLimits.DEFAULT_PER_KEY_LIMIT),
                logger.pruneCalls,
            )
        }

    @Test
    fun resetDiagnosticsClearsBothStores() =
        runTest(testDispatcher) {
            viewModel.log(AppLoggerKeys.FCM_DOOR_RECEIVED)
            advanceUntilIdle()
            check(counters.incrementCalls.isNotEmpty())

            viewModel.resetDiagnostics()
            advanceUntilIdle()

            assertEquals(1, logger.deleteAllCallCount)
            assertEquals(1, counters.resetCallCount)
        }
}
