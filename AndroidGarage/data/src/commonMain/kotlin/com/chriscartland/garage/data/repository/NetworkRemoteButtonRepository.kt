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
import com.chriscartland.garage.domain.model.PushStatus
import com.chriscartland.garage.domain.repository.RemoteButtonRepository
import com.chriscartland.garage.domain.repository.ServerConfigRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NetworkRemoteButtonRepository(
    private val networkButtonDataSource: NetworkButtonDataSource,
    private val serverConfigRepository: ServerConfigRepository,
    private val remoteButtonPushEnabled: Boolean,
) : RemoteButtonRepository {
    private val _pushButtonStatus = MutableStateFlow(PushStatus.IDLE)
    override val pushButtonStatus: StateFlow<PushStatus> = _pushButtonStatus

    override suspend fun pushButton(
        idToken: String,
        buttonAckToken: String,
    ) {
        _pushButtonStatus.value = PushStatus.SENDING
        val serverConfig = serverConfigRepository.getServerConfigCached()
        if (serverConfig == null) {
            Logger.e { "Server config is null" }
            _pushButtonStatus.value = PushStatus.IDLE
            return
        }
        if (!remoteButtonPushEnabled) {
            Logger.w { "Remote button push is disabled" }
            delay(500)
        }
        if (remoteButtonPushEnabled) {
            when (
                val result = networkButtonDataSource.pushButton(
                    remoteButtonBuildTimestamp = serverConfig.remoteButtonBuildTimestamp,
                    buttonAckToken = buttonAckToken,
                    remoteButtonPushKey = serverConfig.remoteButtonPushKey,
                    idToken = idToken,
                )
            ) {
                is NetworkResult.Success -> Logger.d { "Push succeeded" }
                is NetworkResult.HttpError -> Logger.e { "Push HTTP ${result.code}" }
                NetworkResult.ConnectionFailed -> Logger.e { "Push connection failed" }
            }
        }
        _pushButtonStatus.value = PushStatus.IDLE
    }
}
