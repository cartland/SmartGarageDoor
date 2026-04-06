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

package com.chriscartland.garage.remotebutton

import android.util.Log
import com.chriscartland.garage.config.APP_CONFIG
import com.chriscartland.garage.config.ServerConfigRepository
import com.chriscartland.garage.data.NetworkButtonDataSource
import com.chriscartland.garage.domain.model.PushStatus
import com.chriscartland.garage.domain.model.SnoozeRequestStatus
import com.chriscartland.garage.domain.repository.PushRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.time.delay
import java.time.Duration
import java.util.Date

class PushRepositoryImpl(
    private val networkButtonDataSource: NetworkButtonDataSource,
    private val serverConfigRepository: ServerConfigRepository,
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
            Log.e(TAG, "Server config is null")
            _pushButtonStatus.value = PushStatus.IDLE
            return
        }
        if (!APP_CONFIG.remoteButtonPushEnabled) {
            Log.w(TAG, "Remote button push is disabled")
            delay(Duration.ofMillis(500))
        }
        if (APP_CONFIG.remoteButtonPushEnabled) {
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
            Log.e(TAG, "Server config is null")
            return
        }
        if (!APP_CONFIG.snoozeNotificationsOption) {
            Log.w(TAG, "Snooze notifications disabled")
            delay(Duration.ofMillis(500))
        }
        if (APP_CONFIG.snoozeNotificationsOption) {
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
            Log.e(TAG, "Server config is null")
            _snoozeRequestStatus.value = SnoozeRequestStatus.IDLE
            return
        }
        if (!APP_CONFIG.snoozeNotificationsOption) {
            Log.w(TAG, "Snooze notifications disabled")
            delay(Duration.ofMillis(500))
        }
        if (APP_CONFIG.snoozeNotificationsOption) {
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

/**
 * Create a button ack token.
 *
 * This token is created by the client so the server can acknowledge the remote button push.
 * The client can send the same token to the server multiple times and the server is
 * responsible for only processing the token once.
 * When the server receives a button press, it will respond with the token to the client.
 */
fun createButtonAckToken(now: Date): String {
    val humanReadable = java.text.SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", java.util.Locale.US).format(now)
    val timestampMillis = now.time
    val appVersion = "AppVersionTODO"
    val buttonAckTokenData = "android-$appVersion-$humanReadable-$timestampMillis"
    val re = Regex("[^a-zA-Z0-9-_.]")
    val filtered = re.replace(buttonAckTokenData, ".")
    return filtered
}

private const val TAG = "PushRepository"
