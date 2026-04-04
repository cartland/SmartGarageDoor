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
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakePushRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PushRemoteButtonUseCaseTest {
    private lateinit var useCase: PushRemoteButtonUseCase
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var fakePush: FakePushRepository

    @Before
    fun setup() {
        useCase = PushRemoteButtonUseCase(EnsureFreshIdTokenUseCase())
        fakeAuth = FakeAuthRepository()
        fakePush = FakePushRepository()
    }

    private fun authenticateUser(
        token: String = "token",
        exp: Long = Long.MAX_VALUE,
    ) {
        fakeAuth.setAuthState(
            AuthState.Authenticated(
                user = User(
                    name = DisplayName("Test"),
                    email = Email("test@test.com"),
                    idToken = FirebaseIdToken(idToken = token, exp = exp),
                ),
            ),
        )
    }

    @Test
    fun pushSucceedsWhenAuthenticated() =
        runTest {
            authenticateUser()
            val result = useCase(fakeAuth, fakePush, "ack-123")
            assertTrue("Push should succeed when authenticated", result)
            assertEquals(1, fakePush.pushCount)
        }

    @Test
    fun pushFailsWhenUnauthenticated() =
        runTest {
            fakeAuth.setAuthState(AuthState.Unauthenticated)
            val result = useCase(fakeAuth, fakePush, "ack-123")
            assertFalse("Push should fail when unauthenticated", result)
            assertEquals(0, fakePush.pushCount)
        }

    @Test
    fun pushFailsWhenAuthUnknown() =
        runTest {
            val result = useCase(fakeAuth, fakePush, "ack-123")
            assertFalse("Push should fail when auth unknown", result)
            assertEquals(0, fakePush.pushCount)
        }

    @Test
    fun pushPassesCorrectIdToken() =
        runTest {
            authenticateUser(token = "my-token-123")
            useCase(fakeAuth, fakePush, "ack-456")
            assertEquals("my-token-123", fakePush.lastIdToken)
        }

    @Test
    fun pushPassesButtonAckToken() =
        runTest {
            authenticateUser()
            useCase(fakeAuth, fakePush, "ack-unique-789")
            assertEquals(1, fakePush.pushCount)
        }

    @Test
    fun pushRefreshesExpiredToken() =
        runTest {
            authenticateUser(token = "old-token", exp = 1000L)
            val refreshedAuth = AuthState.Authenticated(
                user = User(
                    name = DisplayName("Test"),
                    email = Email("test@test.com"),
                    idToken = FirebaseIdToken(idToken = "new-token", exp = Long.MAX_VALUE),
                ),
            )
            fakeAuth.refreshResult = refreshedAuth

            useCase(fakeAuth, fakePush, "ack-123")

            assertEquals("new-token", fakePush.lastIdToken)
            assertEquals(1, fakeAuth.refreshCount)
        }
}
