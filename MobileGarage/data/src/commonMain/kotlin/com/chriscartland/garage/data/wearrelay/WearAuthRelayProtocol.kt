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

package com.chriscartland.garage.data.wearrelay

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Wire protocol for the phone → watch auth relay over the Wearable Data
 * Layer (`MessageClient.sendRequest` RPC).
 *
 * The watch cannot always complete Credential Manager sign-in (e.g. Play
 * services on some watches lack the Identity Sign-In module), so the phone
 * app answers requests for the signed-in user's identity and a fresh
 * Firebase ID token — Google's documented secondary auth method for Wear.
 *
 * Both sides (`:androidApp` `WearAuthRelayService`, `:wearApp`
 * `DataLayerWearAuthRelayClient`) use THIS codec so the payload shape
 * cannot drift unilaterally. Decoding is lenient (`ignoreUnknownKeys`) for
 * forward compatibility and returns null on malformed payloads.
 */
@Serializable
data class WearAuthRelayRequest(
    val forceRefresh: Boolean = false,
)

@Serializable
data class WearAuthRelayResponse(
    /** False when no user is signed in on the phone. */
    val signedIn: Boolean,
    val displayName: String? = null,
    val email: String? = null,
    /** Null when signed out, or when the phone's token fetch failed. */
    val idToken: String? = null,
    /** Token expiry (epoch seconds), when [idToken] is present. */
    val idTokenExp: Long? = null,
)

object WearAuthRelayProtocol {
    /** MessageClient RPC path the phone's WearableListenerService answers. */
    const val ID_TOKEN_PATH = "/garage/auth/id_token"

    /** Capability the phone app declares so the watch can find its node. */
    const val PHONE_AUTH_CAPABILITY = "garage_phone_auth_relay"

    private val json = Json { ignoreUnknownKeys = true }

    fun encodeRequest(request: WearAuthRelayRequest): ByteArray =
        json.encodeToString(WearAuthRelayRequest.serializer(), request).encodeToByteArray()

    fun decodeRequest(bytes: ByteArray): WearAuthRelayRequest? =
        try {
            json.decodeFromString(WearAuthRelayRequest.serializer(), bytes.decodeToString())
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

    fun encodeResponse(response: WearAuthRelayResponse): ByteArray =
        json.encodeToString(WearAuthRelayResponse.serializer(), response).encodeToByteArray()

    fun decodeResponse(bytes: ByteArray): WearAuthRelayResponse? =
        try {
            json.decodeFromString(WearAuthRelayResponse.serializer(), bytes.decodeToString())
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
}
