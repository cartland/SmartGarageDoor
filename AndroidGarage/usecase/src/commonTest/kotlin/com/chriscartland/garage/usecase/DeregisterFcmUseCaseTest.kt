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
import com.chriscartland.garage.domain.model.DoorFcmState
import com.chriscartland.garage.domain.model.DoorFcmTopic
import com.chriscartland.garage.testcommon.FakeDoorFcmRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeregisterFcmUseCaseTest {
    @Test
    fun deregisterReturnsSuccessWhenRepositoryReportsNotRegistered() =
        runTest {
            val repo = FakeDoorFcmRepository()
            repo.setDeregisterResult(DoorFcmState.NotRegistered)
            val useCase = DeregisterFcmUseCase(repo)

            val result = useCase()

            assertIs<AppResult.Success<Unit>>(result)
        }

    @Test
    fun deregisterReturnsNetworkFailedWhenRepositoryReportsUnknown() =
        runTest {
            val repo = FakeDoorFcmRepository()
            repo.setDeregisterResult(DoorFcmState.Unknown)
            val useCase = DeregisterFcmUseCase(repo)

            val result = useCase()

            assertIs<AppResult.Error<ActionError>>(result)
            assertEquals(ActionError.NetworkFailed, (result as AppResult.Error).error)
        }

    @Test
    fun deregisterReturnsNetworkFailedWhenRepositoryStillRegistered() =
        runTest {
            // Edge case: deregister request did not take effect.
            val repo = FakeDoorFcmRepository()
            repo.setDeregisterResult(DoorFcmState.Registered(DoorFcmTopic("topic")))
            val useCase = DeregisterFcmUseCase(repo)

            val result = useCase()

            assertIs<AppResult.Error<ActionError>>(result)
            assertEquals(ActionError.NetworkFailed, (result as AppResult.Error).error)
        }
}
