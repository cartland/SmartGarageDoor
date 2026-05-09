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
    fun userEmitsAuthenticated() =
        runTest(UnconfinedTestDispatcher()) {
            // ADR-027: AuthState carries identity only, never a token.
            // The presence of an auth user — independent of token state —
            // is what drives Authenticated.
            val bridge = FakeAuthBridge()
            bridge.setAuthUser(AuthUserInfo(displayName = "Alice", email = "alice@test.com"))

            val (repo, _) = createRepository(authBridge = bridge)
            advanceUntilIdle()

            val state = repo.authState.value
            assertIs<AuthState.Authenticated>(state)
            assertEquals("Alice", state.user.name.asString())
            assertEquals("alice@test.com", state.user.email.asString())
        }

    @Test
    fun authUserChangeUpdatesState() =
        runTest(UnconfinedTestDispatcher()) {
            val bridge = FakeAuthBridge()
            val (repo, _) = createRepository(authBridge = bridge)
            advanceUntilIdle()

            // Initially no user → Unauthenticated
            assertEquals(AuthState.Unauthenticated, repo.authState.value)

            // User signs in (listener fires).
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

            val (repo, _) = createRepository(authBridge = bridge)
            repo.signInWithGoogle(GoogleIdToken("google-token"))

            assertEquals(1, bridge.signInCount)
            assertEquals("google-token", bridge.signInCalls.first().asString())
        }

    // --- getIdToken (ADR-027) ---

    @Test
    fun getIdTokenDelegatesToBridge() =
        runTest(UnconfinedTestDispatcher()) {
            val bridge = FakeAuthBridge()
            bridge.setIdTokenResult(FirebaseIdToken(idToken = "fresh-token", exp = 9000L))

            val (repo, _) = createRepository(authBridge = bridge)
            advanceUntilIdle()

            val token = repo.getIdToken(forceRefresh = true)
            assertEquals("fresh-token", token?.idToken)
        }

    @Test
    fun getIdTokenReturnsNullWhenBridgeFails() =
        runTest(UnconfinedTestDispatcher()) {
            val bridge = FakeAuthBridge()
            bridge.setIdTokenResult(null)

            val (repo, _) = createRepository(authBridge = bridge)
            advanceUntilIdle()

            assertNull(repo.getIdToken(forceRefresh = true))
        }

    // --- ADR-027: listener-loop purity ---

    @Test
    fun listenerLoopDoesNotCallGetIdToken() =
        runTest(UnconfinedTestDispatcher()) {
            // Structural guard for ADR-027. The reactive listener loop in
            // FirebaseAuthRepository.init must NOT call authBridge.getIdToken;
            // its responsibility is identity mapping only. Token state is a
            // private concern of AuthRepository and the network repos that
            // need it.
            //
            // This test pins the rule by setting up an auth user *before* the
            // repo is constructed, letting the listener fire, and then
            // asserting the bridge's getIdToken call counter is still zero.
            // If a future change adds `getIdToken` back into the listener
            // collect block (the regression that introduced the 2.13.2
            // sign-in failure), this test will fail with a non-zero count.
            val bridge = FakeAuthBridge()
            bridge.setAuthUser(AuthUserInfo(displayName = "Eve", email = "eve@test.com"))

            val (repo, _) = createRepository(authBridge = bridge)
            advanceUntilIdle()

            // Identity propagated through the listener — proves it ran.
            assertIs<AuthState.Authenticated>(repo.authState.value)
            // …but did NOT pull a token. That's the invariant.
            assertEquals(
                0,
                bridge.getIdTokenCount,
                "Listener loop must not call getIdToken (ADR-027). If you " +
                    "need a token, fetch it explicitly via AuthRepository.getIdToken " +
                    "from the network repo that needs it.",
            )
        }
}
