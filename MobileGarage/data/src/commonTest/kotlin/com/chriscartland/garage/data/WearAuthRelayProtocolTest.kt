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

package com.chriscartland.garage.data

import com.chriscartland.garage.data.wearrelay.WearAuthRelayProtocol
import com.chriscartland.garage.data.wearrelay.WearAuthRelayRequest
import com.chriscartland.garage.data.wearrelay.WearAuthRelayResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WearAuthRelayProtocolTest {
    @Test
    fun requestRoundTrips() {
        val request = WearAuthRelayRequest(forceRefresh = true)
        val decoded = WearAuthRelayProtocol.decodeRequest(WearAuthRelayProtocol.encodeRequest(request))
        assertEquals(request, decoded)
    }

    @Test
    fun signedInResponseRoundTrips() {
        val response = WearAuthRelayResponse(
            signedIn = true,
            displayName = "Test User",
            email = "test@example.com",
            idToken = "token-abc",
            idTokenExp = 1_234_567L,
        )
        val decoded = WearAuthRelayProtocol.decodeResponse(WearAuthRelayProtocol.encodeResponse(response))
        assertEquals(response, decoded)
    }

    @Test
    fun signedOutResponseRoundTrips() {
        val response = WearAuthRelayResponse(signedIn = false)
        val decoded = WearAuthRelayProtocol.decodeResponse(WearAuthRelayProtocol.encodeResponse(response))
        assertEquals(response, decoded)
    }

    @Test
    fun decodeToleratesUnknownKeys() {
        val decoded = WearAuthRelayProtocol.decodeResponse(
            """{"signedIn":true,"email":"a@b.c","futureField":42}""".encodeToByteArray(),
        )
        assertEquals(
            WearAuthRelayResponse(signedIn = true, email = "a@b.c"),
            decoded,
        )
    }

    @Test
    fun decodeReturnsNullOnMalformedPayload() {
        assertNull(WearAuthRelayProtocol.decodeResponse("not json".encodeToByteArray()))
        assertNull(WearAuthRelayProtocol.decodeRequest(byteArrayOf(0x00, 0x01)))
        assertNull(WearAuthRelayProtocol.decodeResponse("""{"noSignedInKey":1}""".encodeToByteArray()))
    }
}
