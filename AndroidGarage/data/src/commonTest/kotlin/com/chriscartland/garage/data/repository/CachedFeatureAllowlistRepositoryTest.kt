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

import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FeatureAllowlist
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeNetworkFeatureAllowlistDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the auth-driven [CachedFeatureAllowlistRepository].
 *
 * The behavior under test that is *new* relative to
 * [CachedServerConfigRepository]: the init collector triggers a fetch on
 * `Authenticated`, clears the cache to null on `Unauthenticated`, and
 * skips the fetch entirely when not signed in. These guards are the only
 * thing preventing one user's allowlist answer from leaking into another
 * user's session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CachedFeatureAllowlistRepositoryTest {
    private val sampleAllowlist = FeatureAllowlist(functionList = true)

    private fun authenticatedState(idToken: String = "id-token"): AuthState.Authenticated =
        AuthState.Authenticated(
            user = User(
                name = DisplayName("Test User"),
                email = Email("test@example.com"),
                idToken = FirebaseIdToken(idToken = idToken, exp = Long.MAX_VALUE),
            ),
        )

    @Test
    fun signInTriggersFetch() =
        runTest {
            val ds = FakeNetworkFeatureAllowlistDataSource().apply {
                setFetchResult(NetworkResult.Success(sampleAllowlist))
            }
            val authRepo = FakeAuthRepository()
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = CachedFeatureAllowlistRepository(ds, authRepo, externalScope)
            advanceUntilIdle()
            // Initial state is Unknown — no fetch yet.
            assertEquals(0, ds.fetchCount)
            assertNull(repo.allowlist.value)

            authRepo.setAuthState(authenticatedState(idToken = "fresh-token"))
            advanceUntilIdle()

            assertEquals(1, ds.fetchCount)
            assertEquals(sampleAllowlist, repo.allowlist.value)
            assertEquals("fresh-token", ds.fetchIdTokens.last())

            externalScope.cancel()
        }

    @Test
    fun signOutClearsCache() =
        runTest {
            val ds = FakeNetworkFeatureAllowlistDataSource().apply {
                setFetchResult(NetworkResult.Success(sampleAllowlist))
            }
            val authRepo = FakeAuthRepository().apply {
                setAuthState(authenticatedState())
            }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = CachedFeatureAllowlistRepository(ds, authRepo, externalScope)
            advanceUntilIdle()
            assertEquals(sampleAllowlist, repo.allowlist.value)

            authRepo.setAuthState(AuthState.Unauthenticated)
            advanceUntilIdle()

            // Stale "yes" must NOT survive into the next session.
            assertNull(repo.allowlist.value)

            externalScope.cancel()
        }

    @Test
    fun fetchSkippedWhenNotAuthenticated() =
        runTest {
            val ds = FakeNetworkFeatureAllowlistDataSource().apply {
                setFetchResult(NetworkResult.Success(sampleAllowlist))
            }
            val authRepo = FakeAuthRepository().apply {
                setAuthState(AuthState.Unauthenticated)
            }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = CachedFeatureAllowlistRepository(ds, authRepo, externalScope)
            advanceUntilIdle()

            // The init collector saw Unauthenticated, so the cache is null and
            // no network call happened.
            assertEquals(0, ds.fetchCount)
            assertNull(repo.allowlist.value)

            // An external force-refresh while signed-out must also skip.
            val result = repo.fetchAllowlist()
            assertNull(result)
            assertEquals(0, ds.fetchCount)

            externalScope.cancel()
        }

    @Test
    fun networkFailurePreservesCachedValue() =
        runTest {
            val ds = FakeNetworkFeatureAllowlistDataSource().apply {
                setFetchResult(NetworkResult.Success(sampleAllowlist))
            }
            val authRepo = FakeAuthRepository().apply {
                setAuthState(authenticatedState())
            }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = CachedFeatureAllowlistRepository(ds, authRepo, externalScope)
            advanceUntilIdle()
            assertEquals(sampleAllowlist, repo.allowlist.value)

            // Server starts erroring.
            ds.setFetchResult(NetworkResult.ConnectionFailed)
            val result = repo.fetchAllowlist()

            // Refresh reports failure (null) but cache stays put.
            assertNull(result)
            assertEquals(sampleAllowlist, repo.allowlist.value)

            externalScope.cancel()
        }

    @Test
    fun userSwitchRefreshesCache() =
        runTest {
            val ds = FakeNetworkFeatureAllowlistDataSource().apply {
                setFetchResult(NetworkResult.Success(FeatureAllowlist(functionList = true)))
            }
            val authRepo = FakeAuthRepository().apply {
                setAuthState(authenticatedState(idToken = "alice-token"))
            }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = CachedFeatureAllowlistRepository(ds, authRepo, externalScope)
            advanceUntilIdle()
            assertEquals(FeatureAllowlist(functionList = true), repo.allowlist.value)
            assertEquals(listOf("alice-token"), ds.fetchIdTokens)

            // Sign out clears, sign in as a different user fetches fresh.
            authRepo.setAuthState(AuthState.Unauthenticated)
            advanceUntilIdle()
            assertNull(repo.allowlist.value)

            ds.setFetchResult(NetworkResult.Success(FeatureAllowlist(functionList = false)))
            authRepo.setAuthState(authenticatedState(idToken = "bob-token"))
            advanceUntilIdle()

            assertEquals(FeatureAllowlist(functionList = false), repo.allowlist.value)
            assertTrue(
                ds.fetchIdTokens.containsAll(listOf("alice-token", "bob-token")),
                "fetch must have been called with both tokens, was: ${ds.fetchIdTokens}",
            )

            externalScope.cancel()
        }
}
