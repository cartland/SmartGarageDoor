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

package com.chriscartland.garage.data.repository

import co.touchlab.kermit.Logger
import com.chriscartland.garage.data.NetworkButtonHealthDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.ButtonHealth
import com.chriscartland.garage.domain.model.ButtonHealthError
import com.chriscartland.garage.domain.model.ButtonHealthState
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.repository.ButtonHealthRepository
import com.chriscartland.garage.domain.repository.ServerConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Repository for the remote-button health state.
 *
 * Pattern matches [NetworkSnoozeRepository] (ADR-019): all state
 * mutations happen on [externalScope] so VM-scope cancellation can
 * never strand the singleton.
 *
 * FCM-vs-fetch ordering rule: both fetch success and FCM updates
 * check [shouldOverwrite] before writing — a stale fetch result
 * cannot clobber a fresher FCM update, and vice versa. UNKNOWN is
 * treated as oldest (any non-UNKNOWN value wins over a current
 * UNKNOWN regardless of timestamp).
 */
class NetworkButtonHealthRepository(
    private val networkButtonHealthDataSource: NetworkButtonHealthDataSource,
    private val serverConfigRepository: ServerConfigRepository,
    private val externalScope: CoroutineScope,
) : ButtonHealthRepository {
    private val _buttonHealth =
        MutableStateFlow<LoadingResult<ButtonHealth>>(LoadingResult.Loading(null))

    override val buttonHealth: StateFlow<LoadingResult<ButtonHealth>> = _buttonHealth

    override suspend fun fetchButtonHealth(idToken: String): AppResult<ButtonHealth, ButtonHealthError> =
        externalScope
            .async {
                doFetchButtonHealth(idToken)
            }.await()

    override fun applyFcmUpdate(update: ButtonHealth) {
        externalScope.launch {
            tryWrite(update, source = "FCM")
        }
    }

    private suspend fun doFetchButtonHealth(idToken: String): AppResult<ButtonHealth, ButtonHealthError> {
        _buttonHealth.value = LoadingResult.Loading(_buttonHealth.value.data)
        val serverConfig = serverConfigRepository.serverConfig.value
            ?: serverConfigRepository.fetchServerConfig()
        if (serverConfig == null) {
            Logger.e { "Server config is null" }
            _buttonHealth.value = LoadingResult.Error(IllegalStateException("Server config is null"))
            return AppResult.Error(ButtonHealthError.Network())
        }
        return when (
            val result = networkButtonHealthDataSource.fetchButtonHealth(
                buildTimestamp = serverConfig.remoteButtonBuildTimestamp,
                remoteButtonPushKey = serverConfig.remoteButtonPushKey,
                idToken = idToken,
            )
        ) {
            is NetworkResult.Success -> {
                tryWrite(result.data, source = "fetch")
                AppResult.Success(result.data)
            }
            is NetworkResult.HttpError -> {
                Logger.e { "Button health HTTP ${result.code}" }
                _buttonHealth.value =
                    LoadingResult.Error(IllegalStateException("HTTP ${result.code}"))
                if (result.code == 401 || result.code == 403) {
                    AppResult.Error(ButtonHealthError.Forbidden())
                } else {
                    AppResult.Error(ButtonHealthError.Network())
                }
            }
            NetworkResult.ConnectionFailed -> {
                Logger.e { "Button health connection failed" }
                _buttonHealth.value =
                    LoadingResult.Error(IllegalStateException("Connection failed"))
                AppResult.Error(ButtonHealthError.Network())
            }
        }
    }

    /** Apply [incoming] only if it should overwrite the current value. */
    private fun tryWrite(
        incoming: ButtonHealth,
        source: String,
    ) {
        val current = _buttonHealth.value
        if (shouldOverwrite(current, incoming)) {
            _buttonHealth.value = LoadingResult.Complete(incoming)
            Logger.i { "buttonHealth <- $incoming (source=$source)" }
        } else {
            Logger.d { "buttonHealth: dropping stale $source update $incoming, current=$current" }
        }
    }

    /**
     * UNKNOWN is treated as oldest. Otherwise compare stateChangedAtSeconds
     * strictly (`>`, not `>=`) — same-timestamp updates from no-op writes
     * should not trigger a re-emit anyway.
     *
     * Visibility: internal so [NetworkButtonHealthRepositoryTest] can pin
     * the rule directly.
     */
    internal fun shouldOverwrite(
        current: LoadingResult<ButtonHealth>,
        incoming: ButtonHealth,
    ): Boolean {
        // No prior data — accept anything.
        if (current !is LoadingResult.Complete) return true
        val currentValue = current.data ?: return true
        // Current is UNKNOWN — any non-UNKNOWN incoming wins.
        if (currentValue.state == ButtonHealthState.UNKNOWN) {
            return incoming.state != ButtonHealthState.UNKNOWN
        }
        // Incoming is UNKNOWN over a known state — never overwrite.
        if (incoming.state == ButtonHealthState.UNKNOWN) return false
        // Both have known state — compare timestamps strictly.
        val currentTs = currentValue.stateChangedAtSeconds ?: return true
        val incomingTs = incoming.stateChangedAtSeconds ?: return false
        return incomingTs > currentTs
    }
}
