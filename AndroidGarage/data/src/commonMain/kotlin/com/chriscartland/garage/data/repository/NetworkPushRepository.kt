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
import com.chriscartland.garage.domain.model.PushStatus
import com.chriscartland.garage.domain.model.SnoozeRequestStatus
import com.chriscartland.garage.domain.repository.PushRepository
import com.chriscartland.garage.domain.repository.ServerConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.time.delay
import java.time.Duration

class NetworkPushRepository(
    private val networkButtonDataSource: NetworkButtonDataSource,
    private val serverConfigRepository: ServerConfigRepository,
    private val remoteButtonPushEnabled: Boolean,
    private val snoozeNotificationsOption: Boolean,
) : PushRepository {
    private val _pushButtonStatus = MutableStateFlow(PushStatus.IDLE)
    override val pushButtonStatus: StateFlow<PushStatus> = _pushButtonStatus

    private val _snoozeRequestStatus = MutableStateFlow(SnoozeRequestStatus.IDLE)
    override val snoozeRequestStatus: StateFlow<SnoozeRequestStatus> = _snoozeRequestStatus

    private val _snoozeEndTimeSeconds = MutableStateFlow(0L)
    override val snoozeEndTimeSeconds: StateFlow<Long> = _snoozeEndTimeSeconds

    override suspend fun push(
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
            delay(Duration.ofMillis(500))
        }
        if (remoteButtonPushEnabled) {
            networkButtonDataSource.pushButton(
                remoteButtonBuildTimestamp = serverConfig.remoteButtonBuildTimestamp,
                buttonAckToken = buttonAckToken,
                remoteButtonPushKey = serverConfig.remoteButtonPushKey,
                idToken = idToken,
            )
        }
        _pushButtonStatus.value = PushStatus.IDLE
    }

    override suspend fun fetchSnoozeEndTimeSeconds() {
        val serverConfig = serverConfigRepository.getServerConfigCached()
        if (serverConfig == null) {
            Logger.e { "Server config is null" }
            return
        }
        if (!snoozeNotificationsOption) {
            Logger.w { "Snooze notifications disabled" }
            delay(Duration.ofMillis(500))
        }
        if (snoozeNotificationsOption) {
            val endTime = networkButtonDataSource.fetchSnoozeEndTimeSeconds(
                buildTimestamp = serverConfig.buildTimestamp,
            )
            _snoozeEndTimeSeconds.value = endTime
        }
    }

    override suspend fun snoozeOpenDoorsNotifications(
        snoozeDurationHours: String,
        idToken: String,
        snoozeEventTimestampSeconds: Long,
    ) {
        _snoozeRequestStatus.value = SnoozeRequestStatus.SENDING
        val serverConfig = serverConfigRepository.getServerConfigCached()
        if (serverConfig == null) {
            Logger.e { "Server config is null" }
            _snoozeRequestStatus.value = SnoozeRequestStatus.IDLE
            return
        }
        if (!snoozeNotificationsOption) {
            Logger.w { "Snooze notifications disabled" }
            delay(Duration.ofMillis(500))
        }
        if (snoozeNotificationsOption) {
            val success = networkButtonDataSource.snoozeNotifications(
                buildTimestamp = serverConfig.buildTimestamp,
                remoteButtonPushKey = serverConfig.remoteButtonPushKey,
                idToken = idToken,
                snoozeDurationHours = snoozeDurationHours,
                snoozeEventTimestampSeconds = snoozeEventTimestampSeconds,
            )
            if (!success) {
                _snoozeRequestStatus.value = SnoozeRequestStatus.ERROR
                return
            }
        }
        _snoozeRequestStatus.value = SnoozeRequestStatus.IDLE
    }
}
