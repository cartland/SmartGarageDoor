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

import android.content.Context
import co.touchlab.kermit.Logger
import com.chriscartland.garage.data.wearrelay.WearAuthRelayProtocol
import com.chriscartland.garage.data.wearrelay.WearAuthRelayRequest
import com.chriscartland.garage.data.wearrelay.WearAuthRelayResponse
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Watch-side client for the phone auth relay. Interface + default impl so
 * the composing bridge is unit-testable without Play services.
 */
interface WearAuthRelayClient {
    /**
     * Ask the paired phone for the signed-in user's identity and a Firebase
     * ID token. Null when no phone is reachable, the phone app is missing,
     * or the RPC fails.
     */
    suspend fun requestAuth(forceRefresh: Boolean): WearAuthRelayResponse?
}

/** [WearAuthRelayClient] over the Wearable Data Layer (MessageClient RPC). */
class DataLayerWearAuthRelayClient(
    private val context: Context,
) : WearAuthRelayClient {
    override suspend fun requestAuth(forceRefresh: Boolean): WearAuthRelayResponse? =
        try {
            val capability = Wearable
                .getCapabilityClient(context)
                .getCapability(
                    WearAuthRelayProtocol.PHONE_AUTH_CAPABILITY,
                    CapabilityClient.FILTER_REACHABLE,
                ).await()
            val node = capability.nodes.firstOrNull { it.isNearby } ?: capability.nodes.firstOrNull()
            if (node == null) {
                Logger.d { "WearAuthRelay: no reachable phone node" }
                null
            } else {
                val responseBytes = Wearable
                    .getMessageClient(context)
                    .sendRequest(
                        node.id,
                        WearAuthRelayProtocol.ID_TOKEN_PATH,
                        WearAuthRelayProtocol.encodeRequest(WearAuthRelayRequest(forceRefresh = forceRefresh)),
                    ).await()
                WearAuthRelayProtocol.decodeResponse(responseBytes).also { response ->
                    Logger.d { "WearAuthRelay: response signedIn=${response?.signedIn} hasToken=${response?.idToken != null}" }
                }
            }
        } catch (e: Exception) {
            Logger.w { "WearAuthRelay: request failed: $e" }
            null
        }
}
