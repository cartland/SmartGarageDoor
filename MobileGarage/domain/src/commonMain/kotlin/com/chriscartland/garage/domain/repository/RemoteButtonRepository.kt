package com.chriscartland.garage.domain.repository

import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppResult

/**
 * Manages the garage door remote button press action.
 *
 * This is a one-shot action: call [pushButton], await the result.
 *
 * - [AppResult.Success] — the server acknowledged the request.
 * - [AppResult.Error] of [ActionError.Forbidden] — the server answered and
 *   refused this account (HTTP 403). A verdict, not a fault: retrying cannot
 *   change it, and every surface words it as such.
 * - [AppResult.Error] of [ActionError.NetworkFailed] — anything else that
 *   kept the press from being acknowledged (connection failure, any other
 *   HTTP status, no server config, no token).
 *
 * Per ADR-027 the implementation fetches the current Firebase ID token
 * itself via [com.chriscartland.garage.domain.repository.AuthRepository.getIdToken];
 * callers do not pass a token.
 */
interface RemoteButtonRepository {
    suspend fun pushButton(buttonAckToken: String): AppResult<Unit, ActionError>
}
