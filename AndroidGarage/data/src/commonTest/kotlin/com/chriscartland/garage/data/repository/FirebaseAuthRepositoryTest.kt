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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseAuthRepositoryTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authBridge: FakeAuthBridge
    private lateinit var loggerRepo: FakeAppLoggerRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        authBridge = FakeAuthBridge()
        loggerRepo = FakeAppLoggerRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createRepository(): FirebaseAuthRepository {
        val testScope = CoroutineScope(testDispatcher)
        val repo = FirebaseAuthRepository(authBridge, loggerRepo, testScope)
        testDispatcher.scheduler.runCurrent()
        return repo
    }

    // --- refreshFirebaseAuthState ---

    @Test
    fun refreshReturnsAuthenticatedWhenUserAndTokenExist() =
        runTest {
            authBridge.setAuthUser(AuthUserInfo(displayName = "Test User", email = "test@test.com"))
            authBridge.setIdTokenResult(FirebaseIdToken(idToken = "token-123", exp = Long.MAX_VALUE))

            val repo = createRepository()
            val result = repo.refreshFirebaseAuthState()

            assertTrue(result is AuthState.Authenticated)
            val user = (result as AuthState.Authenticated).user
            assertEquals("Test User", user.name.asString())
            assertEquals("test@test.com", user.email.asString())
            assertEquals("token-123", user.idToken.idToken)
        }

    @Test
    fun refreshReturnsUnauthenticatedWhenNoUser() =
        runTest {
            authBridge.setAuthUser(null)

            val repo = createRepository()
            val result = repo.refreshFirebaseAuthState()

            assertEquals(AuthState.Unauthenticated, result)
        }

    @Test
    fun refreshReturnsUnauthenticatedWhenTokenRefreshFails() =
        runTest {
            authBridge.setAuthUser(AuthUserInfo(displayName = "Test", email = "test@test.com"))
            authBridge.setIdTokenResult(null)

            val repo = createRepository()
            val result = repo.refreshFirebaseAuthState()

            assertEquals(AuthState.Unauthenticated, result)
        }

    // --- signInWithGoogle ---

    @Test
    fun signInDelegatesAndRefreshes() =
        runTest {
            authBridge.setSignInResult(true)
            authBridge.setAuthUser(AuthUserInfo(displayName = "Signed In", email = "user@test.com"))
            authBridge.setIdTokenResult(FirebaseIdToken(idToken = "new-token", exp = Long.MAX_VALUE))

            val repo = createRepository()
            val result = repo.signInWithGoogle(GoogleIdToken("google-token"))

            assertEquals(1, authBridge.signInCount)
            assertTrue(result is AuthState.Authenticated)
            assertEquals("new-token", (result as AuthState.Authenticated).user.idToken.idToken)
        }

    // --- signOut ---

    @Test
    fun signOutDelegatesToBridgeAndUpdatesState() =
        runTest {
            authBridge.setAuthUser(AuthUserInfo(displayName = "Test", email = "test@test.com"))
            authBridge.setIdTokenResult(FirebaseIdToken(idToken = "token", exp = Long.MAX_VALUE))

            val repo = createRepository()
            repo.signOut()

            assertEquals(1, authBridge.signOutCount)
            assertEquals(AuthState.Unauthenticated, repo.getAuthState())
        }

    // --- authState flow ---

    @Test
    fun authStateUpdatesOnRefresh() =
        runTest {
            // Start with a signed-in user so init refresh produces Authenticated
            authBridge.setAuthUser(AuthUserInfo(displayName = "User", email = "user@test.com"))
            authBridge.setIdTokenResult(FirebaseIdToken(idToken = "token", exp = Long.MAX_VALUE))

            val repo = createRepository()

            // Change bridge to return different user
            authBridge.setAuthUser(AuthUserInfo(displayName = "New User", email = "new@test.com"))
            authBridge.setIdTokenResult(FirebaseIdToken(idToken = "fresh", exp = Long.MAX_VALUE))
            repo.refreshFirebaseAuthState()

            val state = repo.getAuthState()
            assertTrue(state is AuthState.Authenticated)
            assertEquals("New User", (state as AuthState.Authenticated).user.name.asString())
        }
}
