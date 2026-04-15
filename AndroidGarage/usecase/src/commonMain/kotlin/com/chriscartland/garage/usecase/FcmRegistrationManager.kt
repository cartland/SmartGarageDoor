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

import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.FcmRegistrationStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * App-scoped manager for FCM registration with retry (ADR-015).
 *
 * Owns the retry lifecycle: calls [RegisterFcmUseCase] (single attempt),
 * retries with fixed delay on failure, stops on success.
 *
 * [start] is idempotent — calling twice doesn't create two retry loops.
 * Status is observable via [registrationStatus].
 */
class FcmRegistrationManager(
    private val registerFcmUseCase: RegisterFcmUseCase,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY,
) {
    private val statusFlow = MutableStateFlow(FcmRegistrationStatus.UNKNOWN)

    /** Observable registration status. ViewModel collects this. */
    val registrationStatus: Flow<FcmRegistrationStatus> = statusFlow

    private var retryJob: Job? = null

    /**
     * Start the registration attempt. Idempotent — if already running
     * or already registered, this is a no-op.
     */
    fun start() {
        if (statusFlow.value == FcmRegistrationStatus.REGISTERED) {
            Logger.d { "FcmRegistrationManager: already registered" }
            return
        }
        if (retryJob?.isActive == true) {
            Logger.d { "FcmRegistrationManager: retry already running" }
            return
        }
        retryJob = scope.launch(dispatcher) {
            attemptWithRetry()
        }
    }

    /**
     * Force re-registration. Cancels any running retry, resets status,
     * and starts fresh. Use when the FCM topic may have changed
     * (e.g., server config updated) or registration needs to be refreshed.
     */
    fun restart() {
        Logger.d { "FcmRegistrationManager: restarting" }
        retryJob?.cancel()
        retryJob = null
        statusFlow.value = FcmRegistrationStatus.UNKNOWN
        retryJob = scope.launch(dispatcher) {
            attemptWithRetry()
        }
    }

    private suspend fun attemptWithRetry() {
        while (true) {
            Logger.d { "FcmRegistrationManager: attempting registration" }
            when (val result = registerFcmUseCase()) {
                is AppResult.Success -> {
                    Logger.d { "FcmRegistrationManager: registered" }
                    statusFlow.value = FcmRegistrationStatus.REGISTERED
                    return
                }
                is AppResult.Error -> {
                    Logger.w { "FcmRegistrationManager: failed (${result.error}), retrying in ${retryDelayMillis}ms" }
                    statusFlow.value = FcmRegistrationStatus.NOT_REGISTERED
                    delay(retryDelayMillis)
                }
            }
        }
    }

    companion object {
        const val DEFAULT_RETRY_DELAY = 30_000L // 30 seconds
    }
}
