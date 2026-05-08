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
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeRemoteButtonRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PushRemoteButtonUseCaseTest {
    private lateinit var useCase: PushRemoteButtonUseCase
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var fakePush: FakeRemoteButtonRepository

    @BeforeTest
    fun setup() {
        fakeAuth = FakeAuthRepository()
        fakePush = FakeRemoteButtonRepository()
        useCase = PushRemoteButtonUseCase(fakeAuth, fakePush)
    }

    private fun authenticateUser() {
        fakeAuth.setAuthState(
            AuthState.Authenticated(
                user = User(
                    name = DisplayName("Test"),
                    email = Email("test@test.com"),
                ),
            ),
        )
    }

    @Test
    fun pushSucceedsWhenAuthenticated() =
        runTest {
            authenticateUser()
            val result = useCase("ack-123")
            assertTrue(result is AppResult.Success, "Push should succeed")
            assertEquals(1, fakePush.pushCount)
        }

    @Test
    fun pushFailsWhenUnauthenticated() =
        runTest {
            fakeAuth.setAuthState(AuthState.Unauthenticated)
            val result = useCase("ack-123")
            assertTrue(result is AppResult.Error, "Should be NotAuthenticated error")
            assertEquals(ActionError.NotAuthenticated, (result as AppResult.Error).error)
            assertEquals(0, fakePush.pushCount)
        }

    @Test
    fun pushFailsWhenAuthUnknown() =
        runTest {
            val result = useCase("ack-123")
            assertTrue(result is AppResult.Error, "Should be NotAuthenticated error")
            assertEquals(ActionError.NotAuthenticated, (result as AppResult.Error).error)
            assertEquals(0, fakePush.pushCount)
        }

    @Test
    fun pushPassesButtonAckToken() =
        runTest {
            authenticateUser()
            useCase("ack-unique-789")
            assertEquals(1, fakePush.pushCount)
            assertEquals("ack-unique-789", fakePush.pushCalls.last().buttonAckToken)
        }
}
