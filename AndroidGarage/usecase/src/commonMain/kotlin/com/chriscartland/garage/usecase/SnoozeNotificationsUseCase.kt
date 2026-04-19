/*
 * Copyright 2024 Chris Cartland. All rights reserved.
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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.domain.repository.SnoozeRepository

/**
 * Snoozes open-door notifications for a specified duration.
 *
 * On success returns the server-authoritative [SnoozeState]. Callers can
 * surface this directly in the UI (action overlay, card title) without
 * waiting for the flow observer to forward it.
 *
 * Error cases:
 * - [ActionError.NotAuthenticated] — user not signed in.
 * - [ActionError.MissingData] — no current door event timestamp available.
 * - [ActionError.NetworkFailed] — repository reported a network failure.
 */
class SnoozeNotificationsUseCase(
    private val ensureFreshIdToken: EnsureFreshIdTokenUseCase,
    private val authRepository: AuthRepository,
    private val snoozeRepository: SnoozeRepository,
) {
    suspend operator fun invoke(
        snoozeDurationHours: String,
        lastChangeTimeSeconds: Long?,
    ): AppResult<SnoozeState, ActionError> {
        val authState = authRepository.getAuthState()
        if (authState !is AuthState.Authenticated) {
            return AppResult.Error(ActionError.NotAuthenticated)
        }
        if (lastChangeTimeSeconds == null) {
            return AppResult.Error(ActionError.MissingData)
        }
        val idToken = ensureFreshIdToken(authState)
        return snoozeRepository.snoozeNotifications(
            snoozeDurationHours = snoozeDurationHours,
            idToken = idToken.asString(),
            snoozeEventTimestampSeconds = lastChangeTimeSeconds,
        )
    }
}
