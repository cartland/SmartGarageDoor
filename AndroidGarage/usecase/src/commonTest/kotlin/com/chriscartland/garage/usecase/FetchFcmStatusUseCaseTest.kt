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

import com.chriscartland.garage.domain.model.DoorFcmState
import com.chriscartland.garage.domain.model.DoorFcmTopic
import com.chriscartland.garage.domain.model.FcmRegistrationStatus
import com.chriscartland.garage.testcommon.FakeDoorFcmRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FetchFcmStatusUseCaseTest {
    @Test
    fun returnsRegisteredWhenRepoReturnsRegistered() =
        runTest {
            val repo = FakeDoorFcmRepository()
            repo.fetchStatusResult = DoorFcmState.Registered(DoorFcmTopic("test"))
            val useCase = FetchFcmStatusUseCase(repo)

            assertEquals(FcmRegistrationStatus.REGISTERED, useCase())
        }

    @Test
    fun returnsNotRegisteredWhenRepoReturnsNotRegistered() =
        runTest {
            val repo = FakeDoorFcmRepository()
            repo.fetchStatusResult = DoorFcmState.NotRegistered
            val useCase = FetchFcmStatusUseCase(repo)

            assertEquals(FcmRegistrationStatus.NOT_REGISTERED, useCase())
        }

    @Test
    fun returnsUnknownWhenRepoReturnsUnknown() =
        runTest {
            val repo = FakeDoorFcmRepository()
            repo.fetchStatusResult = DoorFcmState.Unknown
            val useCase = FetchFcmStatusUseCase(repo)

            assertEquals(FcmRegistrationStatus.UNKNOWN, useCase())
        }
}
