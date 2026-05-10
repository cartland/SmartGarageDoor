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

package com.chriscartland.garage.domain.model

/**
 * Connectivity state of the remote-button device.
 *
 * Server-side persists only [ONLINE] and [OFFLINE]. [UNKNOWN] is a
 * wire/Android concept — the HTTP cold-start endpoint returns it when
 * no `buttonHealthCurrent` doc exists yet for the device's
 * `buildTimestamp`. Future server-added values (e.g., `MAINTENANCE`)
 * will deserialize to [UNKNOWN] on this client (forward compat).
 */
enum class ButtonHealthState { UNKNOWN, ONLINE, OFFLINE }

/**
 * Snapshot of button-health state.
 *
 * [stateChangedAtSeconds] is null only when [state] is [ButtonHealthState.UNKNOWN];
 * for ONLINE and OFFLINE the server always provides a server-set transition
 * timestamp.
 *
 * [lastPollAtSeconds] is the server's freshest known poll time for the device.
 * Independent of state transitions: an ONLINE doc may report
 * `stateChangedAtSeconds = 12 hours ago` while `lastPollAtSeconds` is a few
 * seconds old. Null in three cases: (a) state is UNKNOWN (no doc yet),
 * (b) bootstrap edge where the server flipped OFFLINE without ever seeing a
 * poll, (c) FCM payloads from older servers that pre-date the field
 * (forward-compat: old wire shape decodes to null here). Used to render the
 * OFFLINE pill's "last seen X" label; for ONLINE we don't surface a freshness
 * value (deliberately — pubsub flips state to OFFLINE within ~1 min, so the
 * user can trust that "Available" means "verified within the last minute").
 */
data class ButtonHealth(
    val state: ButtonHealthState,
    val stateChangedAtSeconds: Long?,
    val lastPollAtSeconds: Long? = null,
)

/** Caller-relevant errors for the button-health repository. */
sealed interface ButtonHealthError : AppError {
    /** Network / HTTP-non-success or request failure. */
    data class Network(
        override val message: String = "Network failure",
        override val cause: Throwable? = null,
    ) : ButtonHealthError

    /** Server returned 401/403; user is not (or no longer) allowlisted for the button feature. */
    data class Forbidden(
        override val message: String = "Forbidden",
    ) : ButtonHealthError

    /** Local auth state is not [com.chriscartland.garage.domain.model.AuthState.Authenticated] — no fetch attempted. */
    data class NotAuthenticated(
        override val message: String = "Not authenticated",
    ) : ButtonHealthError
}
