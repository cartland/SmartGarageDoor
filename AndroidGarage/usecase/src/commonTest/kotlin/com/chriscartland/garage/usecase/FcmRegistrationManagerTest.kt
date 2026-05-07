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

import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.FcmRegistrationStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val RETRY_DELAY = 1_000L

/**
 * In-test fake of [RegisterFcmUseCase]. Implements the interface directly
 * — no subclassing of a production class. Configure outcomes via
 * [results]; calls beyond the configured length replay the last result.
 */
private class FakeRegisterFcmUseCase(
    vararg results: AppResult<Unit, ActionError>,
) : RegisterFcmUseCase {
    private val results: List<AppResult<Unit, ActionError>> = results.toList()
    var callCount: Int = 0
        private set

    override suspend fun invoke(): AppResult<Unit, ActionError> {
        val index = callCount++
        return if (index < results.size) results[index] else results.last()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FcmRegistrationManagerTest {
    @Test
    fun startRegistersSuccessfully() =
        runTest {
            val useCase = FakeRegisterFcmUseCase(AppResult.Success(Unit))
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = FcmRegistrationManager(
                registerFcmUseCase = useCase,
                scope = this,
                dispatcher = testDispatcher,
                retryDelayMillis = RETRY_DELAY,
            )

            manager.start()
            testScheduler.runCurrent()

            assertEquals(FcmRegistrationStatus.REGISTERED, manager.registrationStatus.first())
            assertEquals(1, useCase.callCount)
        }

    @Test
    fun startRetriesOnFailure() =
        runTest {
            val useCase = FakeRegisterFcmUseCase(
                AppResult.Error(ActionError.MissingData),
                AppResult.Success(Unit),
            )
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = FcmRegistrationManager(
                registerFcmUseCase = useCase,
                scope = this,
                dispatcher = testDispatcher,
                retryDelayMillis = RETRY_DELAY,
            )

            manager.start()
            testScheduler.runCurrent()

            assertEquals(FcmRegistrationStatus.NOT_REGISTERED, manager.registrationStatus.first())
            assertEquals(1, useCase.callCount)

            advanceTimeBy(RETRY_DELAY + 1)
            testScheduler.runCurrent()

            assertEquals(FcmRegistrationStatus.REGISTERED, manager.registrationStatus.first())
            assertEquals(2, useCase.callCount)
        }

    @Test
    fun startIsIdempotentWhenAlreadyRegistered() =
        runTest {
            val useCase = FakeRegisterFcmUseCase(AppResult.Success(Unit))
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = FcmRegistrationManager(
                registerFcmUseCase = useCase,
                scope = this,
                dispatcher = testDispatcher,
                retryDelayMillis = RETRY_DELAY,
            )

            manager.start()
            testScheduler.runCurrent()
            assertEquals(1, useCase.callCount)

            manager.start()
            testScheduler.runCurrent()
            assertEquals(1, useCase.callCount)
        }

    @Test
    fun startIsIdempotentWhenRetryRunning() =
        runTest {
            val useCase = FakeRegisterFcmUseCase(
                AppResult.Error(ActionError.NetworkFailed),
                AppResult.Success(Unit),
            )
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = FcmRegistrationManager(
                registerFcmUseCase = useCase,
                scope = this,
                dispatcher = testDispatcher,
                retryDelayMillis = RETRY_DELAY,
            )

            manager.start()
            testScheduler.runCurrent()
            assertEquals(1, useCase.callCount)

            manager.start()
            testScheduler.runCurrent()
            assertEquals(1, useCase.callCount)

            advanceTimeBy(RETRY_DELAY + 1)
            testScheduler.runCurrent()
            assertEquals(2, useCase.callCount)
            assertEquals(FcmRegistrationStatus.REGISTERED, manager.registrationStatus.first())
        }

    @Test
    fun retriesMultipleTimesUntilSuccess() =
        runTest {
            val useCase = FakeRegisterFcmUseCase(
                AppResult.Error(ActionError.MissingData),
                AppResult.Error(ActionError.NetworkFailed),
                AppResult.Error(ActionError.NetworkFailed),
                AppResult.Success(Unit),
            )
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = FcmRegistrationManager(
                registerFcmUseCase = useCase,
                scope = this,
                dispatcher = testDispatcher,
                retryDelayMillis = RETRY_DELAY,
            )

            manager.start()
            advanceTimeBy(RETRY_DELAY * 3 + 1)
            testScheduler.runCurrent()

            assertEquals(4, useCase.callCount)
            assertEquals(FcmRegistrationStatus.REGISTERED, manager.registrationStatus.first())
        }

    @Test
    fun initialStatusIsUnknown() =
        runTest {
            val useCase = FakeRegisterFcmUseCase(AppResult.Success(Unit))
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = FcmRegistrationManager(
                registerFcmUseCase = useCase,
                scope = this,
                dispatcher = testDispatcher,
                retryDelayMillis = RETRY_DELAY,
            )

            assertEquals(FcmRegistrationStatus.UNKNOWN, manager.registrationStatus.first())
        }

    @Test
    fun restartReRegistersAfterSuccess() =
        runTest {
            val useCase = FakeRegisterFcmUseCase(
                AppResult.Success(Unit),
                AppResult.Success(Unit),
            )
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = FcmRegistrationManager(
                registerFcmUseCase = useCase,
                scope = this,
                dispatcher = testDispatcher,
                retryDelayMillis = RETRY_DELAY,
            )

            manager.start()
            testScheduler.runCurrent()
            assertEquals(FcmRegistrationStatus.REGISTERED, manager.registrationStatus.first())
            assertEquals(1, useCase.callCount)

            // restart() forces re-registration even though already registered
            manager.restart()
            testScheduler.runCurrent()
            assertEquals(FcmRegistrationStatus.REGISTERED, manager.registrationStatus.first())
            assertEquals(2, useCase.callCount)
        }
}
