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

class SnoozeNotificationsUseCaseTest {
    private lateinit var useCase: SnoozeNotificationsUseCase
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var fakePush: FakePushRepository

    @Before
    fun setup() {
        useCase = SnoozeNotificationsUseCase(EnsureFreshIdTokenUseCase())
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
    fun snoozeSucceedsWhenAuthenticatedWithTimestamp() =
        runTest {
            authenticateUser()
            val result = useCase(fakeAuth, fakePush, "1h", 1000L)
            assertTrue(result)
            assertEquals(1, fakePush.snoozeCount)
        }

    @Test
    fun snoozeFailsWhenUnauthenticated() =
        runTest {
            fakeAuth.setAuthState(AuthState.Unauthenticated)
            val result = useCase(fakeAuth, fakePush, "1h", 1000L)
            assertFalse(result)
            assertEquals(0, fakePush.snoozeCount)
        }

    @Test
    fun snoozeFailsWhenAuthUnknown() =
        runTest {
            val result = useCase(fakeAuth, fakePush, "1h", 1000L)
            assertFalse(result)
            assertEquals(0, fakePush.snoozeCount)
        }

    @Test
    fun snoozeFailsWhenTimestampIsNull() =
        runTest {
            authenticateUser()
            val result = useCase(fakeAuth, fakePush, "1h", null)
            assertFalse(result)
            assertEquals(0, fakePush.snoozeCount)
        }

    @Test
    fun snoozeDoesNotRefreshTokenWhenTimestampNull() =
        runTest {
            authenticateUser(exp = 1000L)
            useCase(fakeAuth, fakePush, "1h", null)
            assertEquals(0, fakeAuth.refreshCount)
        }

    @Test
    fun snoozeRefreshesExpiredToken() =
        runTest {
            authenticateUser(token = "old", exp = 1000L)
            fakeAuth.refreshResult = AuthState.Authenticated(
                user = User(
                    name = DisplayName("Test"),
                    email = Email("test@test.com"),
                    idToken = FirebaseIdToken(idToken = "fresh", exp = Long.MAX_VALUE),
                ),
            )
            useCase(fakeAuth, fakePush, "4h", 2000L)
            assertEquals(1, fakeAuth.refreshCount)
        }
}
