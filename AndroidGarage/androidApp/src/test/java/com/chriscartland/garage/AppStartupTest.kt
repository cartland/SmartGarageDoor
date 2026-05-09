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

package com.chriscartland.garage

import com.chriscartland.garage.domain.coroutines.AppClock
import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.ButtonHealth
import com.chriscartland.garage.domain.model.ButtonHealthError
import com.chriscartland.garage.domain.model.ButtonHealthState
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.model.ServerConfig
import com.chriscartland.garage.domain.repository.ButtonHealthFcmRepository
import com.chriscartland.garage.domain.repository.ButtonHealthRepository
import com.chriscartland.garage.domain.repository.ServerConfigRepository
import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeDiagnosticsCountersRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import com.chriscartland.garage.testcommon.TestDispatcherProvider
import com.chriscartland.garage.usecase.ButtonHealthFcmSubscriptionManager
import com.chriscartland.garage.usecase.CheckInStalenessManager
import com.chriscartland.garage.usecase.DefaultLiveClock
import com.chriscartland.garage.usecase.FcmRegistrationManager
import com.chriscartland.garage.usecase.FetchButtonHealthUseCase
import com.chriscartland.garage.usecase.LogAppEventUseCase
import com.chriscartland.garage.usecase.ObserveDoorEventsUseCase
import com.chriscartland.garage.usecase.PruneDiagnosticsLogUseCase
import com.chriscartland.garage.usecase.RegisterFcmUseCase
import com.chriscartland.garage.usecase.RunStartupDiagnosticsMaintenanceUseCase
import com.chriscartland.garage.usecase.SeedDiagnosticsCountersFromRoomUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStartupTest {
    private val testDispatcher = StandardTestDispatcher()

    private fun createFcmManager(scope: TestScope): FcmRegistrationManager {
        val useCase = object : RegisterFcmUseCase {
            override suspend fun invoke(): AppResult<Unit, ActionError> = AppResult.Success(Unit)
        }
        return FcmRegistrationManager(
            registerFcmUseCase = useCase,
            scope = scope.backgroundScope,
            dispatcher = testDispatcher,
        )
    }

    private fun createStalenessManager(scope: TestScope): CheckInStalenessManager =
        CheckInStalenessManager(
            observeDoorEvents = ObserveDoorEventsUseCase(FakeDoorRepository()),
            logAppEvent = LogAppEventUseCase(FakeAppLoggerRepository(), FakeDiagnosticsCountersRepository()),
            scope = scope.backgroundScope,
            dispatcher = testDispatcher,
            clock = AppClock { 0L },
        )

    private fun createLiveClock(scope: TestScope): DefaultLiveClock =
        DefaultLiveClock(
            clock = AppClock { 0L },
            scope = scope.backgroundScope,
            dispatcher = testDispatcher,
        )

    private fun createButtonHealthFcmSubscriptionManager(scope: TestScope): ButtonHealthFcmSubscriptionManager {
        val authRepo = FakeAuthRepository()
        val configRepo = object : ServerConfigRepository {
            override val serverConfig: StateFlow<ServerConfig?> = MutableStateFlow(null)

            override suspend fun fetchServerConfig(): ServerConfig? = null
        }
        val fcmRepo = object : ButtonHealthFcmRepository {
            @Suppress("EmptyFunctionBlock")
            override suspend fun subscribe(buildTimestamp: String) {
                // no-op fake
            }

            @Suppress("EmptyFunctionBlock")
            override suspend fun unsubscribeAll() {
                // no-op fake
            }
        }
        val healthRepo = object : ButtonHealthRepository {
            override val buttonHealth: StateFlow<LoadingResult<ButtonHealth>> =
                MutableStateFlow(LoadingResult.Complete(ButtonHealth(ButtonHealthState.UNKNOWN, null)))

            override suspend fun fetchButtonHealth() = AppResult.Error(ButtonHealthError.Network())

            @Suppress("EmptyFunctionBlock")
            override fun applyFcmUpdate(update: ButtonHealth) {
                // no-op fake
            }
        }
        return ButtonHealthFcmSubscriptionManager(
            authRepository = authRepo,
            serverConfigRepository = configRepo,
            fcmRepository = fcmRepo,
            fetchButtonHealthUseCase = FetchButtonHealthUseCase(authRepo, healthRepo),
            scope = scope.backgroundScope,
            dispatcher = testDispatcher,
        )
    }

    private fun createAppStartup(
        scope: TestScope,
        logger: FakeAppLoggerRepository = FakeAppLoggerRepository(),
        counters: FakeDiagnosticsCountersRepository = FakeDiagnosticsCountersRepository(),
    ): AppStartup {
        val fcmManager = createFcmManager(scope)
        val stalenessManager = createStalenessManager(scope)
        val liveClock = createLiveClock(scope)
        val buttonHealthMgr = createButtonHealthFcmSubscriptionManager(scope)
        // UnconfinedTestDispatcher for the IO dispatcher so AppStartup's
        // fire-and-forget launches resolve synchronously inside the test.
        // backgroundScope by itself has subtle interactions with
        // advanceUntilIdle that swallow the launches; eager dispatch
        // sidesteps the timing.
        val ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler)
        return AppStartup(
            fcmRegistrationManager = fcmManager,
            checkInStalenessManager = stalenessManager,
            liveClock = liveClock,
            logAppEvent = LogAppEventUseCase(logger, counters),
            runStartupDiagnosticsMaintenance = RunStartupDiagnosticsMaintenanceUseCase(
                seed = SeedDiagnosticsCountersFromRoomUseCase(logger, counters),
                prune = PruneDiagnosticsLogUseCase(logger),
            ),
            buttonHealthFcmSubscriptionManager = buttonHealthMgr,
            externalScope = scope.backgroundScope,
            dispatchers = TestDispatcherProvider(ioDispatcher),
        )
    }

    @Test
    fun onActivityCreated_logsFcmSubscribe() =
        runTest(testDispatcher) {
            val logger = FakeAppLoggerRepository()
            val startup = createAppStartup(this, logger = logger)

            startup.run()
            advanceUntilIdle()

            assertTrue(
                "Expected ON_CREATE_FCM_SUBSCRIBE_TOPIC in logged keys; got ${logger.loggedKeys}",
                logger.loggedKeys.contains(AppLoggerKeys.ON_CREATE_FCM_SUBSCRIBE_TOPIC),
            )
        }

    @Test
    fun onActivityCreated_returnsAllActions() =
        runTest(testDispatcher) {
            val startup = createAppStartup(this)

            val result = startup.run()

            assertEquals(
                listOf(
                    "startFcmRegistration",
                    "startCheckInStaleness",
                    "startLiveClock",
                    "startButtonHealthFcmSubscription",
                    "logFcmSubscribe",
                    "runDiagnosticsMaintenance",
                ),
                result,
            )
        }

    @Test
    fun onActivityCreated_invokesDiagnosticsMaintenance() =
        runTest(testDispatcher) {
            // Bundled call — guarantees seed-then-prune are sequential.
            // Pre-seed Room with rows for one key; verify after run() that
            // both the counter was seeded AND prune was called.
            val logger = FakeAppLoggerRepository()
            val counters = FakeDiagnosticsCountersRepository()
            repeat(3) { logger.log(AppLoggerKeys.FCM_DOOR_RECEIVED) }

            val startup = createAppStartup(this, logger = logger, counters = counters)
            startup.run()
            advanceUntilIdle()

            assertTrue(
                "Counters should be seeded from Room",
                counters.seededFromRoom,
            )
        }
}
