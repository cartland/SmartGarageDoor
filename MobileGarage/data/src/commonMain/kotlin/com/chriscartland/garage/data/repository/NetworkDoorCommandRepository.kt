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
import com.chriscartland.garage.data.NetworkDoorCommandDataSource
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.DoorCommandVerdict
import com.chriscartland.garage.domain.model.VoiceIntent
import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.domain.repository.DoorCommandRepository
import com.chriscartland.garage.domain.repository.ServerConfigRepository

/**
 * Mirrors [NetworkRemoteButtonRepository]'s plumbing — same server config, same
 * ADR-027 token fetch at the repository layer — because the endpoint mirrors the
 * push endpoint's auth exactly. That was a deliberate server-side choice so the
 * client could reuse this path rather than grow a second way to authenticate.
 */
class NetworkDoorCommandRepository(
    private val networkDoorCommandDataSource: NetworkDoorCommandDataSource,
    private val serverConfigRepository: ServerConfigRepository,
    private val authRepository: AuthRepository,
) : DoorCommandRepository {
    override suspend fun checkCommand(intent: VoiceIntent): AppResult<DoorCommandVerdict, ActionError> {
        val command = when (intent) {
            VoiceIntent.OPEN -> "open"
            VoiceIntent.CLOSE -> "close"
            // The controller only ever commits a HIGH-confidence OPEN or CLOSE,
            // so this is unreachable from the voice loop. Refusing rather than
            // guessing keeps it that way if a future caller is careless.
            VoiceIntent.UNKNOWN -> return AppResult.Error(ActionError.NetworkFailed)
        }
        val serverConfig = serverConfigRepository.serverConfig.value
            ?: serverConfigRepository.fetchServerConfig()
        if (serverConfig == null) {
            Logger.e { "doorCommand: server config is null" }
            return AppResult.Error(ActionError.NetworkFailed)
        }
        val idToken = authRepository.getIdToken(forceRefresh = true)
        if (idToken == null) {
            Logger.e { "doorCommand: getIdToken returned null" }
            return AppResult.Error(ActionError.NotAuthenticated)
        }
        return when (
            val result = networkDoorCommandDataSource.checkDoorCommand(
                command = command,
                remoteButtonPushKey = serverConfig.remoteButtonPushKey,
                idToken = idToken.asString(),
            )
        ) {
            is NetworkResult.Success -> AppResult.Success(result.data)
            is NetworkResult.HttpError -> {
                Logger.e { "doorCommand HTTP ${result.code}" }
                AppResult.Error(ActionError.NetworkFailed)
            }
            NetworkResult.ConnectionFailed -> {
                Logger.e { "doorCommand connection failed" }
                AppResult.Error(ActionError.NetworkFailed)
            }
        }
    }
}
