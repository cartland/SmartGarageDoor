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

        override suspend fun requestAuth(forceRefresh: Boolean): WearAuthRelayResponse? {
            requestCount++
            lastForceRefresh = forceRefresh
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
