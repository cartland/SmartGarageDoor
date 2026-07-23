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

import com.chriscartland.garage.data.AuthBridge
import com.chriscartland.garage.data.AuthUserInfo
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.GoogleIdToken
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * [AuthBridge] that prefers watch-local Firebase auth and falls back to the
 * phone auth relay.
 *
 * - When the watch itself is signed in (Credential Manager succeeded),
 *   [local] is authoritative and the relay is never consulted.
 * - While the watch is signed out, the relay is polled: if the paired
 *   phone's app is signed in and reachable, its identity flows through and
 *   `getIdToken` fetches fresh tokens from the phone per call.
 *
 * This is Google's documented secondary auth for Wear — required in
 * practice because Credential Manager sign-in fails on some watches
 * (Pixel Watch 4 / Wear OS 7, observed 2026-07-22). The shared
 * `FirebaseAuthRepository` consumes this bridge unchanged.
 *
 * Polling only runs while the flow is collected AND the local user is
 * absent; each poll is one lightweight Data Layer RPC. Gating the poll on
 * screen visibility is a noted follow-up (docs/WEAR_OS.md).
 */
class RelayFallbackAuthBridge(
    private val local: AuthBridge,
    private val relay: WearAuthRelayClient,
    private val relayPollMillis: Long = DEFAULT_RELAY_POLL_MILLIS,
) : AuthBridge {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAuthUser(): Flow<AuthUserInfo?> =
        local.observeAuthUser().flatMapLatest { localUser ->
            if (localUser != null) {
                flowOf(localUser)
            } else {
                relayUserFlow()
            }
        }

    private fun relayUserFlow(): Flow<AuthUserInfo?> =
        flow {
            emit(null)
            while (true) {
                val response = relay.requestAuth(forceRefresh = false)
                if (response?.signedIn == true) {
                    emit(
                        AuthUserInfo(
                            displayName = response.displayName ?: "",
                            email = response.email ?: "",
                        ),
                    )
                } else {
                    emit(null)
                }
                delay(relayPollMillis)
            }
        }

    override suspend fun signInWithGoogleToken(idToken: GoogleIdToken): Boolean = local.signInWithGoogleToken(idToken)

    override fun getCurrentUser(): AuthUserInfo? = local.getCurrentUser()

    override suspend fun getIdToken(forceRefresh: Boolean): FirebaseIdToken? {
        val localToken = local.getIdToken(forceRefresh)
        if (localToken != null) {
            return localToken
        }
        val response = relay.requestAuth(forceRefresh = forceRefresh) ?: return null
        val relayToken = response.idToken ?: return null
        return FirebaseIdToken(idToken = relayToken, exp = response.idTokenExp ?: 0L)
    }

    override suspend fun signOut() {
        local.signOut()
    }

    companion object {
        /** Relay poll cadence while signed out and the bridge flow is collected. */
        const val DEFAULT_RELAY_POLL_MILLIS: Long = 15_000L
    }
}
