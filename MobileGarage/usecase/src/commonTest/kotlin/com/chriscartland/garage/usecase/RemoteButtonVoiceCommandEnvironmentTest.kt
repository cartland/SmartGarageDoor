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
import com.chriscartland.garage.domain.model.VoiceIntent
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeRemoteButtonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteButtonVoiceCommandEnvironmentTest {
    private val authedUser = User(
        name = DisplayName("User"),
        email = Email("user@example.com"),
    )

    private fun environment(
        auth: FakeAuthRepository,
        remote: FakeRemoteButtonRepository,
        createButtonAckToken: () -> String = { "android-test-voice-1" },
    ) = RemoteButtonVoiceCommandEnvironment(
        doorState = MutableStateFlow(VoiceDoorState.CLOSED),
        pushRemoteButton = PushRemoteButtonUseCase(auth, remote),
        createButtonAckToken = createButtonAckToken,
    )

    @Test
    fun pressPushesTheRealButtonWhenAuthenticated() =
        runTest {
            val auth = FakeAuthRepository()
            auth.setAuthState(AuthState.Authenticated(authedUser))
            val remote = FakeRemoteButtonRepository()

            assertTrue(environment(auth, remote).pressButton(VoiceIntent.OPEN))
            assertEquals(
                listOf(FakeRemoteButtonRepository.PushCall("android-test-voice-1")),
                remote.pushCalls,
            )
        }

    @Test
    fun pressReturnsFalseAndNeverReachesTheRepoWhenNotAuthenticated() =
        runTest {
            val auth = FakeAuthRepository()
            auth.setAuthState(AuthState.Unauthenticated)
            val remote = FakeRemoteButtonRepository()

            assertFalse(environment(auth, remote).pressButton(VoiceIntent.OPEN))
            assertEquals(0, remote.pushCount, "Auth gate must run before any network call")
        }

    // Contract: pressButton reports failure by returning false, never
    // by throwing — the typed AppResult from the UseCase is that
    // no-throw boundary.
    @Test
    fun pressReturnsFalseOnNetworkFailure() =
        runTest {
            val auth = FakeAuthRepository()
            auth.setAuthState(AuthState.Authenticated(authedUser))
            val remote = FakeRemoteButtonRepository()
            remote.setPushSucceeds(false)

            assertFalse(environment(auth, remote).pressButton(VoiceIntent.CLOSE))
            assertEquals(1, remote.pushCount)
        }

    @Test
    fun eachPressMintsAFreshAckToken() =
        runTest {
            val auth = FakeAuthRepository()
            auth.setAuthState(AuthState.Authenticated(authedUser))
            val remote = FakeRemoteButtonRepository()
            var counter = 0
            val env = environment(auth, remote) { "token-voice-${++counter}" }

            env.pressButton(VoiceIntent.OPEN)
            env.pressButton(VoiceIntent.CLOSE)

            assertEquals(
                listOf("token-voice-1", "token-voice-2"),
                remote.pushCalls.map { it.buttonAckToken },
                "Server-side idempotency ack needs a fresh token per press",
            )
        }
}
