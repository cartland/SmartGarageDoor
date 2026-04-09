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

import com.chriscartland.garage.domain.model.PushStatus
import com.chriscartland.garage.testcommon.FakeRemoteButtonRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObservePushButtonStatusUseCaseTest {
    @Test
    fun invokeReturnsRepositoryFlow() =
        runTest {
            val repo = FakeRemoteButtonRepository()
            val useCase = ObservePushButtonStatusUseCase(repo)

            assertEquals(PushStatus.IDLE, useCase().first())
        }

    @Test
    fun invokeReflectsRepositoryUpdates() =
        runTest {
            val repo = FakeRemoteButtonRepository()
            val useCase = ObservePushButtonStatusUseCase(repo)

            repo.setPushStatus(PushStatus.SENDING)

            assertEquals(PushStatus.SENDING, useCase().first())
        }
}
