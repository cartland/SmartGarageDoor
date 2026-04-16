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
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeSnoozeRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SnoozeNotificationsUseCaseTest {
    private lateinit var useCase: SnoozeNotificationsUseCase
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var fakeSnooze: FakeSnoozeRepository

    @BeforeTest
    fun setup() {
        fakeAuth = FakeAuthRepository()
        fakeSnooze = FakeSnoozeRepository()
        useCase = SnoozeNotificationsUseCase(EnsureFreshIdTokenUseCase(fakeAuth), fakeAuth, fakeSnooze)
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
            val result = useCase("1h", 1000L)
            assertTrue(result is AppResult.Success, "Should succeed")
            assertEquals(1, fakeSnooze.snoozeCount)
        }

    @Test
    fun snoozeFailsWhenUnauthenticated() =
        runTest {
            fakeAuth.setAuthState(AuthState.Unauthenticated)
            val result = useCase("1h", 1000L)
            assertTrue(result is AppResult.Error, "Should be error")
            assertEquals(ActionError.NotAuthenticated, (result as AppResult.Error).error)
            assertEquals(0, fakeSnooze.snoozeCount)
        }

    @Test
    fun snoozeFailsWhenAuthUnknown() =
        runTest {
            val result = useCase("1h", 1000L)
            assertTrue(result is AppResult.Error, "Should be error")
            assertEquals(ActionError.NotAuthenticated, (result as AppResult.Error).error)
            assertEquals(0, fakeSnooze.snoozeCount)
        }

    @Test
    fun snoozeFailsWhenTimestampIsNull() =
        runTest {
            authenticateUser()
            val result = useCase("1h", null)
            assertTrue(result is AppResult.Error, "Should be MissingData error")
            assertEquals(ActionError.MissingData, (result as AppResult.Error).error)
            assertEquals(0, fakeSnooze.snoozeCount)
        }

    @Test
    fun snoozeDoesNotRefreshTokenWhenTimestampNull() =
        runTest {
            authenticateUser(exp = 1000L)
            useCase("1h", null)
            // MissingData error returned before token refresh
            assertEquals(0, fakeAuth.refreshCount)
        }

    @Test
    fun snoozeRefreshesExpiredToken() =
        runTest {
            authenticateUser(token = "old", exp = 1000L)
            fakeAuth.setRefreshResult(
                AuthState.Authenticated(
                    user = User(
                        name = DisplayName("Test"),
                        email = Email("test@test.com"),
                        idToken = FirebaseIdToken(idToken = "fresh", exp = Long.MAX_VALUE),
                    ),
                ),
            )
            useCase("4h", 2000L)
            assertEquals(1, fakeAuth.refreshCount)
        }

    @Test
    fun snoozeReturnsNetworkFailedWhenRepositoryReturnsFalse() =
        runTest {
            authenticateUser()
            fakeSnooze.setSnoozeResult(false)

            val result = useCase("1h", 1000L)

            assertTrue(result is AppResult.Error, "Should be error")
            assertEquals(ActionError.NetworkFailed, (result as AppResult.Error).error)
        }
}
