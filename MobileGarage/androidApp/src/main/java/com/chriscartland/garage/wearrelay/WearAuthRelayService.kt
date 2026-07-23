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

package com.chriscartland.garage.wearrelay

import co.touchlab.kermit.Logger
import com.chriscartland.garage.data.wearrelay.WearAuthRelayProtocol
import com.chriscartland.garage.data.wearrelay.WearAuthRelayResponse
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.WearableListenerService
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

/**
 * Answers the watch's auth-relay RPC (Wearable Data Layer) with the phone's
 * signed-in identity and a Firebase ID token.
 *
 * This is Google's documented secondary auth method for Wear ("token
 * sharing from the companion app"): watches whose Play services cannot
 * complete Credential Manager sign-in (observed on Pixel Watch 4 /
 * Wear OS 7, 2026-07-22) still authenticate as long as the phone app is
 * signed in and reachable over Bluetooth or Wi-Fi.
 *
 * Play services starts this service on demand — it has no UI, holds no
 * state, and mirrors `FirebaseAuthBridge`'s token access. The payload
 * shape is pinned by the shared `WearAuthRelayProtocol` codec (`:data`),
 * used verbatim by the watch client. ID tokens expire after ~1 hour; the
 * watch re-requests per call, so nothing long-lived crosses the wire.
 */
class WearAuthRelayService : WearableListenerService() {
    override fun onRequest(
        nodeId: String,
        path: String,
        request: ByteArray,
    ): Task<ByteArray>? {
        if (path != WearAuthRelayProtocol.ID_TOKEN_PATH) {
            return null
        }
        val relayRequest = WearAuthRelayProtocol.decodeRequest(request)
        if (relayRequest == null) {
            Logger.w { "WearAuthRelay: malformed request from $nodeId" }
            return Tasks.forResult(
                WearAuthRelayProtocol.encodeResponse(WearAuthRelayResponse(signedIn = false)),
            )
        }
        val user = Firebase.auth.currentUser
        if (user == null) {
            Logger.d { "WearAuthRelay: request while signed out" }
            return Tasks.forResult(
                WearAuthRelayProtocol.encodeResponse(WearAuthRelayResponse(signedIn = false)),
            )
        }
        val source = TaskCompletionSource<ByteArray>()
        user
            .getIdToken(relayRequest.forceRefresh)
            .addOnSuccessListener { result ->
                Logger.d { "WearAuthRelay: served ID token (forceRefresh=${relayRequest.forceRefresh})" }
                source.setResult(
                    WearAuthRelayProtocol.encodeResponse(
                        WearAuthRelayResponse(
                            signedIn = true,
                            displayName = user.displayName,
                            email = user.email,
                            idToken = result.token,
                            idTokenExp = result.expirationTimestamp,
                        ),
                    ),
                )
            }.addOnFailureListener { exception ->
                Logger.w { "WearAuthRelay: token fetch failed: $exception" }
                // Identity without a token: the watch shows signed-in state
                // but the push will fail its auth gate until a token succeeds.
                source.setResult(
                    WearAuthRelayProtocol.encodeResponse(
                        WearAuthRelayResponse(
                            signedIn = true,
                            displayName = user.displayName,
                            email = user.email,
                        ),
                    ),
                )
            }
        return source.task
    }
}
