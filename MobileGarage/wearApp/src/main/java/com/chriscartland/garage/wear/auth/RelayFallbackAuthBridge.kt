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
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

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
 *
 * Note what this bridge does NOT emit: a "signed out" answer that predates
 * asking the phone. See [relayUserFlow] — that emission is the whole reason
 * the watch used to flash a Sign in button on every launch.
 */
class RelayFallbackAuthBridge(
    private val local: AuthBridge,
    private val relay: WearAuthRelayClient,
    private val relayPollMillis: Long = DEFAULT_RELAY_POLL_MILLIS,
    private val unresolvedGraceMillis: Long = DEFAULT_UNRESOLVED_GRACE_MILLIS,
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

    /**
     * The relay's view of who is signed in, polled while the watch itself
     * is signed out.
     *
     * Says NOTHING until the phone has actually been asked. `null` here is
     * not "we don't know yet": the shared `FirebaseAuthRepository` maps it
     * to `AuthState.Unauthenticated`, and the hero screen answers that with
     * a Sign in button. Emitting one before the first RPC had returned
     * therefore offered sign-in on every single launch, over the top of an
     * account the very next emission was about to find. Staying silent
     * leaves the repository at `AuthState.Unknown`, which is the honest
     * answer and which the screen already words as "Checking sign-in".
     *
     * Bounded, because silence cannot be forever: a watch with no phone in
     * range must still be able to sign in locally, and
     * [DataLayerWearAuthRelayClient] puts no timeout on its Data Layer
     * `await()`. [unresolvedGraceMillis] after collection starts, an
     * unanswered relay concedes `null` and the button appears. The grace
     * emission never cancels the in-flight request, so a late answer still
     * arrives and signs the watch in: the bound can only offer a button
     * early, never lose an account.
     */
    private fun relayUserFlow(): Flow<AuthUserInfo?> =
        channelFlow {
            val grace = launch {
                delay(unresolvedGraceMillis)
                send(null)
            }
            while (true) {
                val response = relay.requestAuth(forceRefresh = false)
                // Idempotent: only the first answer still has a grace job to
                // stop, and cancelling a finished job is a no-op.
                grace.cancel()
                if (response?.signedIn == true) {
                    send(
                        AuthUserInfo(
                            displayName = response.displayName ?: "",
                            email = response.email ?: "",
                        ),
                    )
                } else {
                    send(null)
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

        /**
         * How long the watch will keep saying "Checking sign-in" while it
         * waits for the phone's first answer, before conceding that it is
         * signed out and offering the button.
         *
         * The same five seconds the rest of the app waits before it starts
         * describing a problem (`AppSettleWindow.SETTLE_WINDOW_MILLIS`) —
         * this is that one rule applied to identity rather than to door
         * data. The value is written out rather than imported because the
         * auth layer does not otherwise depend on `:usecase`; the two are
         * pinned together by
         * `RelayFallbackAuthBridgeTest.graceMatchesTheAppWideSettleWindow`.
         */
        const val DEFAULT_UNRESOLVED_GRACE_MILLIS: Long = 5_000L
    }
}
