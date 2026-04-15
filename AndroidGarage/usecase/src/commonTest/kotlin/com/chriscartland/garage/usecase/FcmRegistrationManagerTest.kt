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
import com.chriscartland.garage.testcommon.FakeDoorFcmRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val RETRY_DELAY = 1_000L

@OptIn(ExperimentalCoroutinesApi::class)
class FcmRegistrationManagerTest {
    private var useCaseCallCount = 0
    private var useCaseResults = mutableListOf<AppResult<Unit, ActionError>>()

    private fun createUseCase(): RegisterFcmUseCase {
        useCaseCallCount = 0
        return object : RegisterFcmUseCase(
            doorRepository = FakeDoorRepository(),
            doorFcmRepository = FakeDoorFcmRepository(),
        ) {
            override suspend operator fun invoke(): AppResult<Unit, ActionError> {
                val index = useCaseCallCount++
                return if (index < useCaseResults.size) {
                    useCaseResults[index]
                } else {
                    useCaseResults.last()
                }
            }
        }
    }

    @Test
    fun startRegistersSuccessfully() =
        runTest {
            useCaseResults.add(AppResult.Success(Unit))
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = FcmRegistrationManager(
                registerFcmUseCase = createUseCase(),
                scope = this,
                dispatcher = testDispatcher,
                retryDelayMillis = RETRY_DELAY,
            )

            manager.start()
            testScheduler.runCurrent()

            assertEquals(FcmRegistrationStatus.REGISTERED, manager.registrationStatus.first())
            assertEquals(1, useCaseCallCount)
        }

    @Test
    fun startRetriesOnFailure() =
        runTest {
            useCaseResults.add(AppResult.Error(ActionError.MissingData))
            useCaseResults.add(AppResult.Success(Unit))
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = FcmRegistrationManager(
                registerFcmUseCase = createUseCase(),
                scope = this,
                dispatcher = testDispatcher,
                retryDelayMillis = RETRY_DELAY,
            )

            manager.start()
            testScheduler.runCurrent()

            assertEquals(FcmRegistrationStatus.NOT_REGISTERED, manager.registrationStatus.first())
            assertEquals(1, useCaseCallCount)

            advanceTimeBy(RETRY_DELAY + 1)
            testScheduler.runCurrent()

            assertEquals(FcmRegistrationStatus.REGISTERED, manager.registrationStatus.first())
            assertEquals(2, useCaseCallCount)
        }

    @Test
    fun startIsIdempotentWhenAlreadyRegistered() =
        runTest {
            useCaseResults.add(AppResult.Success(Unit))
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = FcmRegistrationManager(
                registerFcmUseCase = createUseCase(),
                scope = this,
                dispatcher = testDispatcher,
                retryDelayMillis = RETRY_DELAY,
            )

            manager.start()
            testScheduler.runCurrent()
            assertEquals(1, useCaseCallCount)

            manager.start()
            testScheduler.runCurrent()
            assertEquals(1, useCaseCallCount)
        }

    @Test
    fun startIsIdempotentWhenRetryRunning() =
        runTest {
            useCaseResults.add(AppResult.Error(ActionError.NetworkFailed))
            useCaseResults.add(AppResult.Success(Unit))
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = FcmRegistrationManager(
                registerFcmUseCase = createUseCase(),
                scope = this,
                dispatcher = testDispatcher,
                retryDelayMillis = RETRY_DELAY,
            )

            manager.start()
            testScheduler.runCurrent()
            assertEquals(1, useCaseCallCount)

            manager.start()
            testScheduler.runCurrent()
            assertEquals(1, useCaseCallCount)

            advanceTimeBy(RETRY_DELAY + 1)
            testScheduler.runCurrent()
            assertEquals(2, useCaseCallCount)
            assertEquals(FcmRegistrationStatus.REGISTERED, manager.registrationStatus.first())
        }

    @Test
    fun retriesMultipleTimesUntilSuccess() =
        runTest {
            useCaseResults.add(AppResult.Error(ActionError.MissingData))
            useCaseResults.add(AppResult.Error(ActionError.NetworkFailed))
            useCaseResults.add(AppResult.Error(ActionError.NetworkFailed))
            useCaseResults.add(AppResult.Success(Unit))
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = FcmRegistrationManager(
                registerFcmUseCase = createUseCase(),
                scope = this,
                dispatcher = testDispatcher,
                retryDelayMillis = RETRY_DELAY,
            )

            manager.start()
            advanceTimeBy(RETRY_DELAY * 3 + 1)
            testScheduler.runCurrent()

            assertEquals(4, useCaseCallCount)
            assertEquals(FcmRegistrationStatus.REGISTERED, manager.registrationStatus.first())
        }

    @Test
    fun initialStatusIsUnknown() =
        runTest {
            useCaseResults.add(AppResult.Success(Unit))
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = FcmRegistrationManager(
                registerFcmUseCase = createUseCase(),
                scope = this,
                dispatcher = testDispatcher,
                retryDelayMillis = RETRY_DELAY,
            )

            assertEquals(FcmRegistrationStatus.UNKNOWN, manager.registrationStatus.first())
        }
}
