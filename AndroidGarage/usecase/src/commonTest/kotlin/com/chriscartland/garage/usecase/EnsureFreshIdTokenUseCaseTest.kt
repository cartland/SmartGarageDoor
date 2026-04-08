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
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EnsureFreshIdTokenUseCaseTest {
    private lateinit var useCase: EnsureFreshIdTokenUseCase
    private lateinit var fakeAuth: FakeAuthRepository

    @BeforeTest
    fun setup() {
        fakeAuth = FakeAuthRepository()
        useCase = EnsureFreshIdTokenUseCase(fakeAuth)
    }

    private fun makeAuth(
        token: String = "token",
        exp: Long = 5000L,
    ): AuthState.Authenticated =
        AuthState.Authenticated(
            user = User(
                name = DisplayName("Test"),
                email = Email("test@test.com"),
                idToken = FirebaseIdToken(idToken = token, exp = exp),
            ),
        )

    @Test
    fun returnsCachedTokenWhenNotExpired() =
        runTest {
            val auth = makeAuth(token = "cached-token", exp = 5000L)
            val result = useCase(auth, currentTimeMillis = 4999L)

            assertEquals("cached-token", result.asString())
            assertEquals(0, fakeAuth.refreshCount)
        }

    @Test
    fun refreshesTokenWhenExpired() =
        runTest {
            val auth = makeAuth(token = "old-token", exp = 1000L)
            val refreshedAuth = makeAuth(token = "new-token", exp = 9000L)
            fakeAuth.refreshResult = refreshedAuth

            val result = useCase(auth, currentTimeMillis = 2000L)

            assertEquals("new-token", result.asString())
            assertEquals(1, fakeAuth.refreshCount)
        }

    @Test
    fun refreshesTokenWhenExactlyExpired() =
        runTest {
            val auth = makeAuth(token = "old-token", exp = 1000L)
            val refreshedAuth = makeAuth(token = "new-token", exp = 9000L)
            fakeAuth.refreshResult = refreshedAuth

            val result = useCase(auth, currentTimeMillis = 1000L)

            assertEquals("new-token", result.asString())
            assertEquals(1, fakeAuth.refreshCount)
        }

    @Test
    fun fallsBackToCachedTokenWhenRefreshFailsWithUnauthenticated() =
        runTest {
            val auth = makeAuth(token = "old-token", exp = 1000L)
            fakeAuth.refreshResult = AuthState.Unauthenticated

            val result = useCase(auth, currentTimeMillis = 2000L)

            assertEquals("old-token", result.asString())
            assertEquals(1, fakeAuth.refreshCount)
        }

    @Test
    fun fallsBackToCachedTokenWhenRefreshFailsWithUnknown() =
        runTest {
            val auth = makeAuth(token = "old-token", exp = 1000L)
            fakeAuth.refreshResult = AuthState.Unknown

            val result = useCase(auth, currentTimeMillis = 2000L)

            assertEquals("old-token", result.asString())
            assertEquals(1, fakeAuth.refreshCount)
        }

    @Test
    fun doesNotRefreshWhenTokenHasLargeMargin() =
        runTest {
            val auth = makeAuth(token = "cached-token", exp = Long.MAX_VALUE)
            val result = useCase(auth, currentTimeMillis = 1000L)

            assertEquals("cached-token", result.asString())
            assertEquals(0, fakeAuth.refreshCount)
        }
}
