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

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.testcommon.FakeAuthRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ObserveAuthStateUseCaseTest {
    @Test
    fun invokeReturnsRepositoryAuthState() {
        val repo = FakeAuthRepository()
        val useCase = ObserveAuthStateUseCase(repo)

        assertIs<AuthState.Unknown>(useCase().value)
    }

    @Test
    fun invokeReflectsRepositoryUpdates() {
        val repo = FakeAuthRepository()
        val useCase = ObserveAuthStateUseCase(repo)

        repo.setAuthState(AuthState.Unauthenticated)

        assertEquals(AuthState.Unauthenticated, useCase().value)
    }
}
