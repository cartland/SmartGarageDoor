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

import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.testcommon.FakeSnoozeRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveSnoozeStateUseCaseTest {
    @Test
    fun invokeReturnsRepositoryState() {
        val repo = FakeSnoozeRepository()
        val useCase = ObserveSnoozeStateUseCase(repo)

        assertEquals(SnoozeState.Loading, useCase().value)
    }

    @Test
    fun invokeReflectsRepositoryUpdates() {
        val repo = FakeSnoozeRepository()
        val useCase = ObserveSnoozeStateUseCase(repo)

        repo.setSnoozeState(SnoozeState.Snoozing(12345L))

        assertEquals(SnoozeState.Snoozing(12345L), useCase().value)
    }
}
