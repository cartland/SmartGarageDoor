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

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.domain.repository.UserScopedCache
import com.chriscartland.garage.testcommon.FakeAuthRepository
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class RecordingUserScopedCache : UserScopedCache {
    var clearCount = 0

    override suspend fun clearUserScopedEntries() {
        clearCount++
    }
}

class SignOutCacheClearManagerTest {
    private val user = User(name = DisplayName("Test User"), email = Email("test@example.com"))

    private fun createManager(
        scope: TestScope,
        authRepository: FakeAuthRepository,
        cache: UserScopedCache,
    ): SignOutCacheClearManager =
        SignOutCacheClearManager(
            authRepository = authRepository,
            userScopedCache = cache,
            scope = scope.backgroundScope,
            dispatcher = UnconfinedTestDispatcher(scope.testScheduler),
        )

    @Test
    fun clearsOnSignOut() =
        runTest {
            val auth = FakeAuthRepository()
            val cache = RecordingUserScopedCache()
            auth.setAuthState(AuthState.Authenticated(user))
            createManager(this, auth, cache).start()
            runCurrent()

            auth.setAuthState(AuthState.Unauthenticated)
            runCurrent()

            assertEquals(1, cache.clearCount)
        }

    @Test
    fun doesNotClearWhileSignedInOrUnknown() =
        runTest {
            val auth = FakeAuthRepository()
            val cache = RecordingUserScopedCache()
            createManager(this, auth, cache).start()
            runCurrent()

            // Initial Unknown state: no clear.
            assertEquals(0, cache.clearCount)

            auth.setAuthState(AuthState.Authenticated(user))
            runCurrent()

            assertEquals(0, cache.clearCount)
        }

    @Test
    fun tokenRefreshStyleAuthenticatedRewritesDoNotTrigger() =
        runTest {
            val auth = FakeAuthRepository()
            val cache = RecordingUserScopedCache()
            auth.setAuthState(AuthState.Authenticated(user))
            createManager(this, auth, cache).start()
            runCurrent()

            // A token refresh writes a NEW Authenticated instance; the
            // manager projects to a boolean so nothing fires.
            auth.setAuthState(AuthState.Authenticated(user.copy()))
            runCurrent()

            assertEquals(0, cache.clearCount)
        }

    @Test
    fun clearsOncePerSignOutAcrossCycles() =
        runTest {
            val auth = FakeAuthRepository()
            val cache = RecordingUserScopedCache()
            auth.setAuthState(AuthState.Authenticated(user))
            createManager(this, auth, cache).start()
            runCurrent()

            auth.setAuthState(AuthState.Unauthenticated)
            runCurrent()
            auth.setAuthState(AuthState.Authenticated(user))
            runCurrent()
            auth.setAuthState(AuthState.Unauthenticated)
            runCurrent()

            assertEquals(2, cache.clearCount)
        }

    @Test
    fun startIsIdempotent() =
        runTest {
            val auth = FakeAuthRepository()
            val cache = RecordingUserScopedCache()
            auth.setAuthState(AuthState.Authenticated(user))
            val manager = createManager(this, auth, cache)
            manager.start()
            manager.start()
            manager.start()
            runCurrent()

            auth.setAuthState(AuthState.Unauthenticated)
            runCurrent()

            // One collector, one clear — not three.
            assertEquals(1, cache.clearCount)
        }
}
