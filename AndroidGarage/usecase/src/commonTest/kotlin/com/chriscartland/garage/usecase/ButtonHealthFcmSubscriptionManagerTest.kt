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
import com.chriscartland.garage.domain.model.ServerConfig
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.domain.repository.ButtonHealthFcmRepository
import com.chriscartland.garage.domain.repository.ButtonHealthRepository
import com.chriscartland.garage.domain.repository.ServerConfigRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ButtonHealthFcmSubscriptionManagerTest {
    private val signedInUser = AuthState.Authenticated(
        user = User(
            name = DisplayName("Test"),
            email = Email("test@example.com"),
            idToken = FirebaseIdToken(idToken = "token-xyz", exp = 0L),
        ),
    )

    private val configWithButton = ServerConfig(
        buildTimestamp = "door",
        remoteButtonBuildTimestamp = "Sat Apr 10 23:57:32 2021",
        remoteButtonPushKey = "key",
    )

    @Test
    fun signedOut_signedIn_signedOut_subscribesThenUnsubscribes() =
        runTest {
            val auth = FakeAuthRepository(_authState = MutableStateFlow(AuthState.Unauthenticated))
            val config = FakeServerConfigRepository(MutableStateFlow(configWithButton))
            val fcm = FakeButtonHealthFcmRepository()
            val repo = FakeButtonHealthRepository().apply {
                setFetchResult(AppResult.Success(ButtonHealth(ButtonHealthState.ONLINE, 100L)))
            }
            val manager = ButtonHealthFcmSubscriptionManager(
                authRepository = auth,
                serverConfigRepository = config,
                fcmRepository = fcm,
                fetchButtonHealthUseCase = FetchButtonHealthUseCase(repo),
                scope = backgroundScope,
                dispatcher = StandardTestDispatcher(testScheduler),
            )

            manager.start()
            testScheduler.runCurrent()
            testScheduler.advanceUntilIdle()
            // First emission: signed-out + config — unsubscribeAll only.
            assertEquals(0, fcm.subscribeCount)
            assertEquals(1, fcm.unsubscribeAllCount)

            auth.setAuthState(signedInUser)
            testScheduler.runCurrent()
            testScheduler.advanceUntilIdle()
            // Now signed in: unsubscribeAll then subscribe + fetch.
            assertEquals(1, fcm.subscribeCount)
            assertEquals("Sat Apr 10 23:57:32 2021", fcm.lastSubscribeBuildTimestamp)
            assertEquals(1, repo.fetchCount)
            assertEquals("token-xyz", repo.lastFetchIdToken)

            auth.setAuthState(AuthState.Unauthenticated)
            testScheduler.runCurrent()
            testScheduler.advanceUntilIdle()
            // Sign out: another unsubscribeAll, no new subscribe.
            assertEquals(1, fcm.subscribeCount)
            assertEquals(3, fcm.unsubscribeAllCount) // initial + transition out
        }

    @Test
    fun forbiddenColdStartFetch_unsubscribesAfterSubscribing() =
        runTest {
            val auth = FakeAuthRepository(_authState = MutableStateFlow(signedInUser))
            val config = FakeServerConfigRepository(MutableStateFlow(configWithButton))
            val fcm = FakeButtonHealthFcmRepository()
            val repo = FakeButtonHealthRepository().apply {
                setFetchResult(AppResult.Error(ButtonHealthError.Forbidden()))
            }
            val manager = ButtonHealthFcmSubscriptionManager(
                authRepository = auth,
                serverConfigRepository = config,
                fcmRepository = fcm,
                fetchButtonHealthUseCase = FetchButtonHealthUseCase(repo),
                scope = backgroundScope,
                dispatcher = StandardTestDispatcher(testScheduler),
            )

            manager.start()
            testScheduler.runCurrent()
            testScheduler.advanceUntilIdle()

            assertEquals(1, fcm.subscribeCount)
            assertEquals(1, repo.fetchCount)
            // Fetch returned Forbidden → manager unsubscribes again.
            assertEquals(2, fcm.unsubscribeAllCount)
        }

    @Test
    fun networkErrorColdStartFetch_keepsSubscription() =
        runTest {
            val auth = FakeAuthRepository(_authState = MutableStateFlow(signedInUser))
            val config = FakeServerConfigRepository(MutableStateFlow(configWithButton))
            val fcm = FakeButtonHealthFcmRepository()
            val repo = FakeButtonHealthRepository().apply {
                setFetchResult(AppResult.Error(ButtonHealthError.Network()))
            }
            val manager = ButtonHealthFcmSubscriptionManager(
                authRepository = auth,
                serverConfigRepository = config,
                fcmRepository = fcm,
                fetchButtonHealthUseCase = FetchButtonHealthUseCase(repo),
                scope = backgroundScope,
                dispatcher = StandardTestDispatcher(testScheduler),
            )

            manager.start()
            testScheduler.runCurrent()
            testScheduler.advanceUntilIdle()

            assertEquals(1, fcm.subscribeCount)
            // Network error — keep subscription, only the initial unsubscribeAll fired.
            assertEquals(1, fcm.unsubscribeAllCount)
        }

    @Test
    fun buildTimestampRotation_unsubscribesOldThenSubscribesNew() =
        runTest {
            val auth = FakeAuthRepository(_authState = MutableStateFlow(signedInUser))
            val configFlow = MutableStateFlow(configWithButton)
            val config = FakeServerConfigRepository(configFlow)
            val fcm = FakeButtonHealthFcmRepository()
            val repo = FakeButtonHealthRepository().apply {
                setFetchResult(AppResult.Success(ButtonHealth(ButtonHealthState.ONLINE, 100L)))
            }
            val manager = ButtonHealthFcmSubscriptionManager(
                authRepository = auth,
                serverConfigRepository = config,
                fcmRepository = fcm,
                fetchButtonHealthUseCase = FetchButtonHealthUseCase(repo),
                scope = backgroundScope,
                dispatcher = StandardTestDispatcher(testScheduler),
            )

            manager.start()
            testScheduler.runCurrent()
            testScheduler.advanceUntilIdle()
            assertEquals(1, fcm.subscribeCount)
            assertEquals("Sat Apr 10 23:57:32 2021", fcm.lastSubscribeBuildTimestamp)

            // Rotate buildTimestamp (firmware reflash).
            configFlow.value = configWithButton.copy(remoteButtonBuildTimestamp = "Mon Jan 01 00:00:00 2024")
            testScheduler.runCurrent()
            testScheduler.advanceUntilIdle()

            assertEquals(2, fcm.subscribeCount)
            assertEquals("Mon Jan 01 00:00:00 2024", fcm.lastSubscribeBuildTimestamp)
        }

    @Test
    fun startIsIdempotent() =
        runTest {
            val auth = FakeAuthRepository(_authState = MutableStateFlow(signedInUser))
            val config = FakeServerConfigRepository(MutableStateFlow(configWithButton))
            val fcm = FakeButtonHealthFcmRepository()
            val repo = FakeButtonHealthRepository().apply {
                setFetchResult(AppResult.Success(ButtonHealth(ButtonHealthState.ONLINE, 100L)))
            }
            val manager = ButtonHealthFcmSubscriptionManager(
                authRepository = auth,
                serverConfigRepository = config,
                fcmRepository = fcm,
                fetchButtonHealthUseCase = FetchButtonHealthUseCase(repo),
                scope = backgroundScope,
                dispatcher = StandardTestDispatcher(testScheduler),
            )

            manager.start()
            manager.start()
            testScheduler.runCurrent()
            testScheduler.advanceUntilIdle()

            // Second start() did not start a second collector.
            assertEquals(1, fcm.subscribeCount)
            assertEquals(1, repo.fetchCount)
        }
}

// ---- Inline fakes (only this test uses them; promote to test-common when re-used) ----

private class FakeAuthRepository(
    private val _authState: MutableStateFlow<AuthState>,
) : AuthRepository {
    override val authState: StateFlow<AuthState> = _authState

    fun setAuthState(state: AuthState) {
        _authState.value = state
    }

    override suspend fun signInWithGoogle(idToken: com.chriscartland.garage.domain.model.GoogleIdToken): AuthState = _authState.value

    override suspend fun refreshIdToken(): FirebaseIdToken? = null

    @Suppress("DEPRECATION")
    @Deprecated("legacy")
    override suspend fun refreshFirebaseAuthState(): AuthState = _authState.value

    override suspend fun signOut() {
        _authState.value = AuthState.Unauthenticated
    }
}

private class FakeServerConfigRepository(
    private val _flow: StateFlow<ServerConfig?>,
) : ServerConfigRepository {
    override val serverConfig: StateFlow<ServerConfig?> = _flow

    override suspend fun fetchServerConfig(): ServerConfig? = _flow.value
}

private class FakeButtonHealthRepository : ButtonHealthRepository {
    private val state = MutableStateFlow<LoadingResult<ButtonHealth>>(LoadingResult.Loading(null))
    override val buttonHealth: StateFlow<LoadingResult<ButtonHealth>> = state

    private var fetchResult: AppResult<ButtonHealth, ButtonHealthError> =
        AppResult.Success(ButtonHealth(ButtonHealthState.UNKNOWN, null))

    private val fetchTokens = mutableListOf<String>()
    val fetchCount: Int get() = fetchTokens.size
    val lastFetchIdToken: String? get() = fetchTokens.lastOrNull()

    fun setFetchResult(value: AppResult<ButtonHealth, ButtonHealthError>) {
        fetchResult = value
    }

    override suspend fun fetchButtonHealth(idToken: String): AppResult<ButtonHealth, ButtonHealthError> {
        fetchTokens.add(idToken)
        return fetchResult
    }

    override fun applyFcmUpdate(update: ButtonHealth) {
        state.value = LoadingResult.Complete(update)
    }
}

private class FakeButtonHealthFcmRepository : ButtonHealthFcmRepository {
    private val subscribeCalls = mutableListOf<String>()
    val subscribeCount: Int get() = subscribeCalls.size
    val lastSubscribeBuildTimestamp: String? get() = subscribeCalls.lastOrNull()

    var unsubscribeAllCount: Int = 0

    override suspend fun subscribe(buildTimestamp: String) {
        subscribeCalls.add(buildTimestamp)
    }

    override suspend fun unsubscribeAll() {
        unsubscribeAllCount++
    }
}
