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

package com.chriscartland.garage.data.repository

import com.chriscartland.garage.data.AuthUserInfo
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeAuthBridge
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Tests for [FirebaseAuthRepository] with the reactive auth state listener.
 *
 * The repository collects from [FakeAuthBridge.observeAuthUser] (a MutableStateFlow)
 * and maps each emission to [AuthState]. Tests verify the reactive chain by
 * changing the bridge's auth user and observing the repository's auth state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseAuthRepositoryTest {
    private fun TestScope.createRepository(
        authBridge: FakeAuthBridge = FakeAuthBridge(),
        loggerRepo: FakeAppLoggerRepository = FakeAppLoggerRepository(),
    ): Pair<FirebaseAuthRepository, FakeAuthBridge> {
        val repo = FirebaseAuthRepository(authBridge, loggerRepo, backgroundScope)
        return repo to authBridge
    }

    // --- Reactive listener tests ---

    @Test
    fun noUserEmitsUnauthenticated() =
        runTest(UnconfinedTestDispatcher()) {
            val (repo, _) = createRepository()
            advanceUntilIdle()

            assertEquals(AuthState.Unauthenticated, repo.authState.value)
        }

    @Test
    fun userWithTokenEmitsAuthenticated() =
        runTest(UnconfinedTestDispatcher()) {
            val bridge = FakeAuthBridge()
            bridge.setAuthUser(AuthUserInfo(displayName = "Alice", email = "alice@test.com"))
            bridge.setIdTokenResult(FirebaseIdToken(idToken = "token-123", exp = Long.MAX_VALUE))

            val (repo, _) = createRepository(authBridge = bridge)
            advanceUntilIdle()

            val state = repo.authState.value
            assertIs<AuthState.Authenticated>(state)
            assertEquals("Alice", state.user.name.asString())
            assertEquals("token-123", state.user.idToken.idToken)
        }

    @Test
    fun userWithoutTokenEmitsUnauthenticated() =
        runTest(UnconfinedTestDispatcher()) {
            val bridge = FakeAuthBridge()
            bridge.setAuthUser(AuthUserInfo(displayName = "Bob", email = "bob@test.com"))
            bridge.setIdTokenResult(null) // no token

            val (repo, _) = createRepository(authBridge = bridge)
            advanceUntilIdle()

            assertEquals(AuthState.Unauthenticated, repo.authState.value)
        }

    @Test
    fun authUserChangeUpdatesState() =
        runTest(UnconfinedTestDispatcher()) {
            val bridge = FakeAuthBridge()
            val (repo, _) = createRepository(authBridge = bridge)
            advanceUntilIdle()

            // Initially no user → Unauthenticated
            assertEquals(AuthState.Unauthenticated, repo.authState.value)

            // User signs in (listener fires) — set token BEFORE user so the
            // collector sees the token when it processes the user emission.
            bridge.setIdTokenResult(FirebaseIdToken(idToken = "carol-token", exp = Long.MAX_VALUE))
            bridge.setAuthUser(AuthUserInfo(displayName = "Carol", email = "carol@test.com"))
            advanceUntilIdle()

            val state = repo.authState.value
            assertIs<AuthState.Authenticated>(state)
            assertEquals("Carol", state.user.name.asString())
        }

    @Test
    fun signOutUpdatesStateImmediately() =
        runTest(UnconfinedTestDispatcher()) {
            val bridge = FakeAuthBridge()
            bridge.setAuthUser(AuthUserInfo(displayName = "Dave", email = "dave@test.com"))
            bridge.setIdTokenResult(FirebaseIdToken(idToken = "token", exp = Long.MAX_VALUE))

            val (repo, _) = createRepository(authBridge = bridge)
            advanceUntilIdle()
            assertIs<AuthState.Authenticated>(repo.authState.value)

            repo.signOut()

            // Eagerly set to Unauthenticated — no waiting for listener
            assertEquals(AuthState.Unauthenticated, repo.authState.value)
            assertEquals(1, bridge.signOutCount)
        }

    // --- signInWithGoogle ---

    @Test
    fun signInDelegatesToBridge() =
        runTest(UnconfinedTestDispatcher()) {
            val bridge = FakeAuthBridge()
            bridge.setSignInResult(true)
            bridge.setIdTokenResult(FirebaseIdToken(idToken = "token", exp = Long.MAX_VALUE))

            val (repo, _) = createRepository(authBridge = bridge)
            repo.signInWithGoogle(GoogleIdToken("google-token"))

            assertEquals(1, bridge.signInCount)
            assertEquals("google-token", bridge.signInCalls.first().asString())
        }

    // --- refreshIdToken ---

    @Test
    fun refreshIdTokenReturnsTokenAndUpdatesState() =
        runTest(UnconfinedTestDispatcher()) {
            val bridge = FakeAuthBridge()
            bridge.setAuthUser(AuthUserInfo(displayName = "Eve", email = "eve@test.com"))
            bridge.setIdTokenResult(FirebaseIdToken(idToken = "old-token", exp = 1000L))

            val (repo, _) = createRepository(authBridge = bridge)
            advanceUntilIdle()

            // Now force-refresh returns a new token
            bridge.setIdTokenResult(FirebaseIdToken(idToken = "fresh-token", exp = 9000L))
            val refreshed = repo.refreshIdToken()

            assertEquals("fresh-token", refreshed?.idToken)
            // _authState should also be updated with the new token
            val state = repo.authState.value
            assertIs<AuthState.Authenticated>(state)
            assertEquals("fresh-token", state.user.idToken.idToken)
        }

    @Test
    fun refreshIdTokenReturnsNullWhenBridgeFails() =
        runTest(UnconfinedTestDispatcher()) {
            val bridge = FakeAuthBridge()
            bridge.setIdTokenResult(null)

            val (repo, _) = createRepository(authBridge = bridge)
            advanceUntilIdle()

            assertNull(repo.refreshIdToken())
        }
}
