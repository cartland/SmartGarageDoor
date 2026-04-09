package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.TestDispatcherProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class DefaultAppLoggerViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val logger = FakeAppLoggerRepository()
    private val dispatchers = TestDispatcherProvider(testDispatcher)
    private val viewModel = DefaultAppLoggerViewModel(
        logAppEvent = LogAppEventUseCase(logger),
        observeAppLogCount = ObserveAppLogCountUseCase(logger),
        dispatchers = dispatchers,
    )

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

            // The init block collects countKey flows, so the StateFlow should update
            assertTrue(viewModel.userFetchCurrentDoorCount.value >= 1L)
        }
}
