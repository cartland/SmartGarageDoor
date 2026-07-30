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

package com.chriscartland.garage.presentation

import com.chriscartland.garage.domain.model.SnoozeState

/**
 * What the snooze row is currently saying — as four mutually exclusive states.
 *
 * The row answers one question ("will I be told when the door is open?"), but
 * the answer comes from two independent sources: the OS notification permission
 * and the server-side snooze. Representing that as two booleans, which is what
 * iOS did, forces every reader to re-derive the precedence between them, and a
 * reader that gets it wrong is not obviously wrong — it just renders a state
 * that looks plausible.
 *
 * That already cost us one bug: the iOS row used the same bell-with-slash glyph
 * for "you snoozed this" and "the OS is blocking notifications", so a snooze the
 * user set looked identical to notifications they could not receive at all. One
 * exhaustive type makes that collapse visible instead of silent.
 */
sealed interface SnoozeRowStatus {
    /** Snooze state not yet known. Nothing actionable to say. */
    data object Loading : SnoozeRowStatus

    /**
     * The OS is withholding notifications, so snoozing is moot. The row
     * becomes a prompt to grant permission rather than a snooze control.
     */
    data object PermissionDenied : SnoozeRowStatus

    /** Notifications will arrive. */
    data object Off : SnoozeRowStatus

    /**
     * Snoozing until [untilEpochSeconds].
     *
     * Deliberately an instant, not a rendered time. Formatting a clock time is
     * exactly the kind of thing that has to be locale-aware — 12- versus 24-hour
     * is a locale property, not a product decision — so each platform formats it
     * with its own localized formatter.
     */
    data class SnoozingUntil(
        val untilEpochSeconds: Long,
    ) : SnoozeRowStatus
}

/**
 * Resolves the snooze row's state from the two inputs that feed it.
 *
 * The one real decision here is the precedence: **a missing notification
 * permission outranks everything, including [SnoozeState.Loading]**. Permission
 * is known locally and synchronously; the snooze state is a network fact. If
 * both are unresolved-looking, the permission is the one the user can act on,
 * and showing "Loading…" over a denied permission would hide the actual problem
 * behind a spinner that never resolves into anything useful.
 *
 * Whether a save is in flight is deliberately *not* modeled here. Both platforms
 * already treat it as an orthogonal overlay on the row (a spinner replacing the
 * chevron) rather than a fifth state, and folding it in would multiply every arm
 * by two for no gain.
 */
object SnoozeRowStatusMapper {
    fun forState(
        snoozeState: SnoozeState,
        notificationsGranted: Boolean,
    ): SnoozeRowStatus {
        if (!notificationsGranted) return SnoozeRowStatus.PermissionDenied
        return when (snoozeState) {
            SnoozeState.Loading -> SnoozeRowStatus.Loading
            SnoozeState.NotSnoozing -> SnoozeRowStatus.Off
            is SnoozeState.Snoozing -> SnoozeRowStatus.SnoozingUntil(snoozeState.untilEpochSeconds)
        }
    }
}
