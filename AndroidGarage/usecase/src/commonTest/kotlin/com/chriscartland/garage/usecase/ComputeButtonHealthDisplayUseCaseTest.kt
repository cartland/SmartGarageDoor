/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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

import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.ButtonHealth
import com.chriscartland.garage.domain.model.ButtonHealthError
import com.chriscartland.garage.domain.model.ButtonHealthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.domain.repository.ButtonHealthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ComputeButtonHealthDisplayUseCaseTest {
    private val signedIn = AuthState.Authenticated(
        user = User(
            name = DisplayName("Test"),
            email = Email("test@example.com"),
        ),
    )

    @Test
    fun emitsUnauthorizedWhenSignedOut() =
        runTest {
            val auth = TestAuthRepository(MutableStateFlow(AuthState.Unauthenticated))
            val repo = TestButtonHealthRepository()
            val clock = TestLiveClock(MutableStateFlow(1_700_000_000L))

            val display = ComputeButtonHealthDisplayUseCase(auth, repo, clock).invoke().first()

            assertEquals(ButtonHealthDisplay.Unauthorized, display)
        }

    @Test
    fun emitsOnlineWhenSignedInAndComplete() =
        runTest {
            val auth = TestAuthRepository(MutableStateFlow(signedIn))
            val now = 1_700_000_000L
            val repo = TestButtonHealthRepository(
                MutableStateFlow(LoadingResult.Complete(ButtonHealth(ButtonHealthState.ONLINE, now - 30))),
            )
            val clock = TestLiveClock(MutableStateFlow(now))

            val display = ComputeButtonHealthDisplayUseCase(auth, repo, clock).invoke().first()

            assertEquals(ButtonHealthDisplay.Online, display)
        }

    @Test
    fun emitsOfflineWhenSignedInAndCompleteOffline() =
        runTest {
            val auth = TestAuthRepository(MutableStateFlow(signedIn))
            val now = 1_700_000_000L
            val repo = TestButtonHealthRepository(
                MutableStateFlow(
                    LoadingResult.Complete(ButtonHealth(ButtonHealthState.OFFLINE, now - 660)),
                ),
            )
            val clock = TestLiveClock(MutableStateFlow(now))

            val display = ComputeButtonHealthDisplayUseCase(auth, repo, clock).invoke().first()

            val offline = assertIs<ButtonHealthDisplay.Offline>(display)
            assertEquals("11 min ago", offline.durationLabel)
        }
}

// ---- Inline fakes ----

private class TestAuthRepository(
    private val flow: StateFlow<AuthState>,
) : AuthRepository {
    override val authState: StateFlow<AuthState> = flow

    override suspend fun signInWithGoogle(idToken: com.chriscartland.garage.domain.model.GoogleIdToken): AuthState = flow.value

    override suspend fun getIdToken(forceRefresh: Boolean): FirebaseIdToken? = null

    override suspend fun signOut() {}
}

private class TestButtonHealthRepository(
    private val flow: StateFlow<LoadingResult<ButtonHealth>> =
        MutableStateFlow(LoadingResult.Loading(null)),
) : ButtonHealthRepository {
    override val buttonHealth: StateFlow<LoadingResult<ButtonHealth>> = flow

    override suspend fun fetchButtonHealth(): AppResult<ButtonHealth, ButtonHealthError> = AppResult.Error(ButtonHealthError.Network())

    override fun applyFcmUpdate(update: ButtonHealth) {}
}

private class TestLiveClock(
    private val flow: StateFlow<Long>,
) : LiveClock {
    override val nowEpochSeconds: StateFlow<Long> = flow

    override fun start() {}
}
