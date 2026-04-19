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

package com.chriscartland.garage.data.repository

import co.touchlab.kermit.Logger
import com.chriscartland.garage.data.NetworkButtonDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.repository.ServerConfigRepository
import com.chriscartland.garage.domain.repository.SnoozeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * All network work (fetch + submit) runs on [externalScope] so that VM-scope
 * cancellation can never strand the singleton state.
 *
 * Why: callers (ViewModels) launch these suspend calls on viewModelScope.
 * If the VM is cancelled after a network call returns but before the state
 * write, Ktor's rethrown CancellationException skips the write — the
 * singleton's StateFlow gets stuck, and every future subscriber sees stale
 * data. See ADR-018 and [FirebaseAuthRepository] for the same pattern.
 *
 * The caller still suspends via [kotlinx.coroutines.Deferred.await]/
 * [kotlinx.coroutines.Job.join]. If the caller is cancelled, their
 * `await`/`join` throws, but the launched coroutine continues on
 * [externalScope] and completes the state update independently.
 */
class NetworkSnoozeRepository(
    private val networkButtonDataSource: NetworkButtonDataSource,
    private val serverConfigRepository: ServerConfigRepository,
    private val snoozeNotificationsOption: Boolean,
    private val currentTimeSeconds: () -> Long,
    private val externalScope: CoroutineScope,
) : SnoozeRepository {
    private val snoozeStateFlow = MutableStateFlow<SnoozeState>(SnoozeState.Loading)

    init {
        externalScope.launch { doFetchSnoozeStatus() }
    }

    override fun observeSnoozeState(): Flow<SnoozeState> = snoozeStateFlow

    override suspend fun fetchSnoozeStatus() {
        externalScope.launch { doFetchSnoozeStatus() }.join()
    }

    override suspend fun snoozeNotifications(
        snoozeDurationHours: String,
        idToken: String,
        snoozeEventTimestampSeconds: Long,
    ): AppResult<SnoozeState, ActionError> =
        externalScope
            .async {
                doSnoozeNotifications(snoozeDurationHours, idToken, snoozeEventTimestampSeconds)
            }.await()

    private suspend fun doFetchSnoozeStatus() {
        val serverConfig = serverConfigRepository.getServerConfigCached()
        if (serverConfig == null) {
            Logger.e { "Server config is null" }
            clearLoadingState()
            return
        }
        if (!snoozeNotificationsOption) {
            Logger.w { "Snooze notifications disabled" }
            delay(500)
            clearLoadingState()
            return
        }
        when (
            val result = networkButtonDataSource.fetchSnoozeEndTimeSeconds(
                buildTimestamp = serverConfig.buildTimestamp,
            )
        ) {
            is NetworkResult.Success -> {
                snoozeStateFlow.value = snoozeStateFromEndTime(result.data)
            }
            is NetworkResult.HttpError -> {
                Logger.e { "Snooze fetch HTTP ${result.code}" }
                clearLoadingState()
            }
            NetworkResult.ConnectionFailed -> {
                Logger.e { "Snooze fetch connection failed" }
                clearLoadingState()
            }
        }
    }

    private suspend fun doSnoozeNotifications(
        snoozeDurationHours: String,
        idToken: String,
        snoozeEventTimestampSeconds: Long,
    ): AppResult<SnoozeState, ActionError> {
        val serverConfig = serverConfigRepository.getServerConfigCached()
        if (serverConfig == null) {
            Logger.e { "Server config is null" }
            return AppResult.Error(ActionError.NetworkFailed)
        }
        if (!snoozeNotificationsOption) {
            Logger.w { "Snooze notifications disabled" }
            delay(500)
            // Feature disabled: pretend it succeeded and surface the current
            // flow value so callers don't see a phantom network failure.
            return AppResult.Success(snoozeStateFlow.value)
        }
        return when (
            val result = networkButtonDataSource.snoozeNotifications(
                buildTimestamp = serverConfig.buildTimestamp,
                remoteButtonPushKey = serverConfig.remoteButtonPushKey,
                idToken = idToken,
                snoozeDurationHours = snoozeDurationHours,
                snoozeEventTimestampSeconds = snoozeEventTimestampSeconds,
            )
        ) {
            is NetworkResult.Success -> {
                // Compute the new state from the server's authoritative end
                // time, write it to the observable flow, and return the SAME
                // value. Callers can use the return value directly (no
                // observer race) or subscribe via observeSnoozeState().
                val newState = snoozeStateFromEndTime(result.data)
                snoozeStateFlow.value = newState
                AppResult.Success(newState)
            }
            is NetworkResult.HttpError -> {
                Logger.e { "Snooze HTTP ${result.code}" }
                AppResult.Error(ActionError.NetworkFailed)
            }
            NetworkResult.ConnectionFailed -> {
                Logger.e { "Snooze connection failed" }
                AppResult.Error(ActionError.NetworkFailed)
            }
        }
    }

    /** If still Loading (first fetch), fall back to NotSnoozing so the UI doesn't show "Loading..." forever. */
    private fun clearLoadingState() {
        if (snoozeStateFlow.value is SnoozeState.Loading) {
            snoozeStateFlow.value = SnoozeState.NotSnoozing
        }
    }

    private fun snoozeStateFromEndTime(endTimeSeconds: Long): SnoozeState {
        if (endTimeSeconds <= 0) return SnoozeState.NotSnoozing
        return if (endTimeSeconds > currentTimeSeconds()) {
            SnoozeState.Snoozing(endTimeSeconds)
        } else {
            SnoozeState.NotSnoozing
        }
    }
}
