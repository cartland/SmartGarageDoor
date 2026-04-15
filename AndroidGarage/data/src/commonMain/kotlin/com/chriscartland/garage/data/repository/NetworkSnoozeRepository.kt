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
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.repository.ServerConfigRepository
import com.chriscartland.garage.domain.repository.SnoozeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class NetworkSnoozeRepository(
    private val networkButtonDataSource: NetworkButtonDataSource,
    private val serverConfigRepository: ServerConfigRepository,
    private val snoozeNotificationsOption: Boolean,
    private val currentTimeSeconds: () -> Long,
) : SnoozeRepository {
    private val snoozeStateFlow = MutableStateFlow<SnoozeState>(SnoozeState.Loading)

    override fun observeSnoozeState(): Flow<SnoozeState> = snoozeStateFlow

    override suspend fun fetchSnoozeStatus() {
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

    override suspend fun snoozeNotifications(
        snoozeDurationHours: String,
        idToken: String,
        snoozeEventTimestampSeconds: Long,
    ): Boolean {
        val serverConfig = serverConfigRepository.getServerConfigCached()
        if (serverConfig == null) {
            Logger.e { "Server config is null" }
            return false
        }
        if (!snoozeNotificationsOption) {
            Logger.w { "Snooze notifications disabled" }
            delay(500)
            return true // Treat as success in feature-disabled mode
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
            is NetworkResult.Success -> true
            is NetworkResult.HttpError -> {
                Logger.e { "Snooze HTTP ${result.code}" }
                false
            }
            NetworkResult.ConnectionFailed -> {
                Logger.e { "Snooze connection failed" }
                false
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
