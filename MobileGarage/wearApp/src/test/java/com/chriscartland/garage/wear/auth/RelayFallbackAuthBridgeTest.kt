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

package com.chriscartland.garage.wear.auth

import com.chriscartland.garage.data.AuthUserInfo
import com.chriscartland.garage.data.wearrelay.WearAuthRelayResponse
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.testcommon.FakeAuthBridge
import com.chriscartland.garage.usecase.AppSettleWindow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the local-first / relay-fallback auth composition.
 *
 * Motivating platform fact (captured from a Pixel Watch 4, 2026-07-22):
 * GMS rejects Credential Manager Sign in with Google on Wear OS
 * ("Google Identity Services do not support this Android Credential
 * Manager API on Wear OS"), so the phone relay is the working path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RelayFallbackAuthBridgeTest {
    private class FakeRelayClient : WearAuthRelayClient {
        var response: WearAuthRelayResponse? = null
        var requestCount = 0
            private set
        var lastForceRefresh: Boolean? = null
            private set

        /**
         * When set, every request blocks until it completes — a phone that is
         * out of range, or a Data Layer `await()` that simply never returns.
         * The real client puts no timeout on that call, so "never answers" is
         * not a hypothetical: it is the state a watch sitting on a bedside
         * table is in.
         */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun requestAuth(forceRefresh: Boolean): WearAuthRelayResponse? {
            requestCount++
            lastForceRefresh = forceRefresh
            gate?.await()
            return response
        }
    }

    private val localBridge = FakeAuthBridge()
    private val relayClient = FakeRelayClient()
    private val bridge = RelayFallbackAuthBridge(
        local = localBridge,
        relay = relayClient,
        relayPollMillis = 1_000L,
    )

    @Test
    fun localUserWinsAndRelayIsNotConsulted() =
        runTest {
            localBridge.setAuthUser(AuthUserInfo(displayName = "Local", email = "local@example.com"))
            val latest = MutableStateFlow<AuthUserInfo?>(null)
            backgroundScope.launch { bridge.observeAuthUser().collect { latest.value = it } }
            runCurrent()
            assertEquals("local@example.com", latest.value?.email)
            advanceTimeBy(5_000L)
            assertEquals(0, relayClient.requestCount)
            coroutineContext.cancelChildren()
        }

    @Test
    fun relayIdentityFlowsThroughWhileSignedOutLocally() =
        runTest {
            relayClient.response = WearAuthRelayResponse(
                signedIn = true,
                displayName = "Phone User",
                email = "phone@example.com",
            )
            val latest = MutableStateFlow<AuthUserInfo?>(null)
            backgroundScope.launch { bridge.observeAuthUser().collect { latest.value = it } }
            runCurrent()
            assertEquals("phone@example.com", latest.value?.email)
            assertEquals(1, relayClient.requestCount)
            coroutineContext.cancelChildren()
        }

    @Test
    fun relaySignedOutPhoneYieldsNullAndKeepsPolling() =
        runTest {
            relayClient.response = WearAuthRelayResponse(signedIn = false)
            val latest = MutableStateFlow<AuthUserInfo?>(AuthUserInfo("seed", "seed@example.com"))
            backgroundScope.launch { bridge.observeAuthUser().collect { latest.value = it } }
            runCurrent()
            assertNull(latest.value)
            advanceTimeBy(3_001L)
            assertEquals(4, relayClient.requestCount)
            coroutineContext.cancelChildren()
        }

    /**
     * The bug: the watch offered a Sign in button on every single launch.
     *
     * The old flow opened with a bare `emit(null)` before it had asked the
     * phone anything at all. `FirebaseAuthRepository` maps null to
     * `AuthState.Unauthenticated`, and the hero screen answers that with a
     * button — over the top of an account the very next emission was usually
     * about to find.
     *
     * Asserting on the emission COUNT, not on the value: "emitted null" and
     * "said nothing yet" are the same `latest.value` and opposite behaviours.
     * Zero emissions is what leaves the repository at `AuthState.Unknown`,
     * which the screen already words as "Checking sign-in".
     */
    @Test
    fun signInIsNotOfferedBeforeThePhoneHasBeenAsked() =
        runTest {
            relayClient.gate = CompletableDeferred()
            relayClient.response = WearAuthRelayResponse(
                signedIn = true,
                displayName = "Phone User",
                email = "phone@example.com",
            )
            var emissions = 0
            backgroundScope.launch { bridge.observeAuthUser().collect { emissions++ } }
            runCurrent()

            assertEquals("the phone should have been asked", 1, relayClient.requestCount)
            assertEquals("nothing may be claimed before the phone answers", 0, emissions)
            coroutineContext.cancelChildren()
        }

    /**
     * Silence cannot be forever. A watch with no phone in range must still be
     * able to sign in locally, so an unanswered relay concedes after the
     * grace period and the button appears.
     */
    @Test
    fun anUnansweredRelayConcedesAfterTheGracePeriod() =
        runTest {
            relayClient.gate = CompletableDeferred()
            var emissions = 0
            val latest = MutableStateFlow<AuthUserInfo?>(AuthUserInfo("seed", "seed@example.com"))
            backgroundScope.launch {
                bridge.observeAuthUser().collect {
                    emissions++
                    latest.value = it
                }
            }

            advanceTimeBy(RelayFallbackAuthBridge.DEFAULT_UNRESOLVED_GRACE_MILLIS - 1)
            runCurrent()
            assertEquals("must still be silent one millisecond before the deadline", 0, emissions)

            advanceTimeBy(2)
            runCurrent()
            assertEquals(1, emissions)
            assertNull(latest.value)
            coroutineContext.cancelChildren()
        }

    /**
     * The grace bound can only offer a button early — never lose an account.
     * It is a separate emission racing the request, not a timeout that
     * cancels it, so a phone that answers late still signs the watch in.
     */
    @Test
    fun aLateRelayAnswerStillSignsTheWatchIn() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            relayClient.gate = gate
            relayClient.response = WearAuthRelayResponse(
                signedIn = true,
                displayName = "Phone User",
                email = "phone@example.com",
            )
            val latest = MutableStateFlow<AuthUserInfo?>(null)
            backgroundScope.launch { bridge.observeAuthUser().collect { latest.value = it } }

            advanceTimeBy(RelayFallbackAuthBridge.DEFAULT_UNRESOLVED_GRACE_MILLIS + 1)
            runCurrent()
            assertNull("the grace emission should have conceded by now", latest.value)

            gate.complete(Unit)
            runCurrent()
            assertEquals("phone@example.com", latest.value?.email)
            coroutineContext.cancelChildren()
        }

    /**
     * One rule, applied to two different unknowns. The watch waits the same
     * five seconds before admitting it cannot identify you as the phone and
     * iOS wait before admitting they cannot vouch for the door.
     *
     * The constant is written out in [RelayFallbackAuthBridge] rather than
     * imported, because the auth layer does not otherwise depend on
     * `:usecase`. This assertion is what stops the two drifting.
     */
    @Test
    fun graceMatchesTheAppWideSettleWindow() {
        assertEquals(
            AppSettleWindow.SETTLE_WINDOW_MILLIS,
            RelayFallbackAuthBridge.DEFAULT_UNRESOLVED_GRACE_MILLIS,
        )
    }

    @Test
    fun idTokenPrefersLocalThenRelay() =
        runTest {
            localBridge.setIdTokenResult(FirebaseIdToken(idToken = "local-token", exp = 1L))
            relayClient.response = WearAuthRelayResponse(
                signedIn = true,
                idToken = "relay-token",
                idTokenExp = 2L,
            )
            assertEquals("local-token", bridge.getIdToken(forceRefresh = true)?.asString())
            assertEquals(0, relayClient.requestCount)

            localBridge.setIdTokenResult(null)
            val relayToken = bridge.getIdToken(forceRefresh = true)
            assertEquals("relay-token", relayToken?.asString())
            assertEquals(2L, relayToken?.exp)
            assertEquals(true, relayClient.lastForceRefresh)
        }

    @Test
    fun idTokenNullWhenNeitherSourceHasOne() =
        runTest {
            localBridge.setIdTokenResult(null)
            relayClient.response = WearAuthRelayResponse(signedIn = true, idToken = null)
            assertNull(bridge.getIdToken(forceRefresh = false))
            relayClient.response = null
            assertNull(bridge.getIdToken(forceRefresh = false))
        }
}
