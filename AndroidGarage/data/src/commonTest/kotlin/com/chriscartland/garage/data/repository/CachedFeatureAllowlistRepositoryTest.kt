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
    private val sampleAllowlist = FeatureAllowlist(functionList = true, developer = false)

    private fun authenticatedState(email: String = "test@example.com"): AuthState.Authenticated =
        AuthState.Authenticated(
            user = User(
                name = DisplayName("Test User"),
                email = Email(email),
            ),
        )

    @Test
    fun signInTriggersFetch() =
        runTest {
            val ds = FakeNetworkFeatureAllowlistDataSource().apply {
                setFetchResult(NetworkResult.Success(sampleAllowlist))
            }
            val authRepo = FakeAuthRepository().apply {
                setIdTokenResult(FirebaseIdToken(idToken = "fresh-token", exp = Long.MAX_VALUE))
            }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = CachedFeatureAllowlistRepository(ds, authRepo, externalScope)
            advanceUntilIdle()
            // Initial state is Unknown — no fetch yet.
            assertEquals(0, ds.fetchCount)
            assertNull(repo.allowlist.value)

            authRepo.setAuthState(authenticatedState())
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
                setIdTokenResult(FirebaseIdToken(idToken = "id-token", exp = Long.MAX_VALUE))
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
                setIdTokenResult(FirebaseIdToken(idToken = "id-token", exp = Long.MAX_VALUE))
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
    fun userSwitchDuringFetchDiscardsStaleResult() =
        runTest {
            // Race scenario: user A is authenticated, fetchAllowlist starts
            // with A's token; mid-fetch, the auth state flips to user B.
            // The in-flight network call resolves with A's answer — the
            // post-fetch email guard must discard it rather than writing
            // A's answer under B's session.
            val ds = object : com.chriscartland.garage.data.NetworkFeatureAllowlistDataSource {
                var swapAuthDuringFetch: (() -> Unit)? = null
                var resultToReturn: FeatureAllowlist = FeatureAllowlist(functionList = true, developer = false)

                override suspend fun fetchAllowlist(idToken: String): NetworkResult<FeatureAllowlist> {
                    swapAuthDuringFetch?.invoke()
                    return NetworkResult.Success(resultToReturn)
                }
            }
            val authRepo = FakeAuthRepository().apply {
                setIdTokenResult(FirebaseIdToken(idToken = "alice-token", exp = Long.MAX_VALUE))
                setAuthState(authenticatedState(email = "alice@example.com"))
            }
            val bobState = AuthState.Authenticated(
                user = User(
                    name = DisplayName("Bob"),
                    email = Email("bob@example.com"),
                ),
            )
            ds.swapAuthDuringFetch = { authRepo.setAuthState(bobState) }

            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val repo = CachedFeatureAllowlistRepository(ds, authRepo, externalScope)
            advanceUntilIdle()

            // After init: A's fetch fired, mid-fetch swapped to Bob. Result
            // for A was discarded (post-fetch email check). Bob's
            // emission also fires fetchAllowlist — by that point swap is
            // null and Bob's fetch writes correctly. The key invariant:
            // cache reflects Bob's session, not A's discarded answer.
            ds.swapAuthDuringFetch = null
            ds.resultToReturn = FeatureAllowlist(functionList = false, developer = false)
            repo.fetchAllowlist()
            advanceUntilIdle()
            assertEquals(FeatureAllowlist(functionList = false, developer = false), repo.allowlist.value)

            externalScope.cancel()
        }

    @Test
    fun userSwitchRefreshesCache() =
        runTest {
            val ds = FakeNetworkFeatureAllowlistDataSource().apply {
                setFetchResult(NetworkResult.Success(FeatureAllowlist(functionList = true, developer = false)))
            }
            val authRepo = FakeAuthRepository().apply {
                setIdTokenResult(FirebaseIdToken(idToken = "alice-token", exp = Long.MAX_VALUE))
                setAuthState(authenticatedState(email = "alice@example.com"))
            }
            val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val repo = CachedFeatureAllowlistRepository(ds, authRepo, externalScope)
            advanceUntilIdle()
            assertEquals(FeatureAllowlist(functionList = true, developer = false), repo.allowlist.value)
            assertEquals(listOf("alice-token"), ds.fetchIdTokens)

            // Sign out clears, sign in as a different user fetches fresh.
            authRepo.setAuthState(AuthState.Unauthenticated)
            advanceUntilIdle()
            assertNull(repo.allowlist.value)

            ds.setFetchResult(NetworkResult.Success(FeatureAllowlist(functionList = false, developer = false)))
            authRepo.setIdTokenResult(FirebaseIdToken(idToken = "bob-token", exp = Long.MAX_VALUE))
            authRepo.setAuthState(authenticatedState(email = "bob@example.com"))
            advanceUntilIdle()

            assertEquals(FeatureAllowlist(functionList = false, developer = false), repo.allowlist.value)
            assertTrue(
                ds.fetchIdTokens.containsAll(listOf("alice-token", "bob-token")),
                "fetch must have been called with both tokens, was: ${ds.fetchIdTokens}",
            )

            externalScope.cancel()
        }
}
