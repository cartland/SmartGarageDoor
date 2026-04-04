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

import android.text.format.DateFormat
import android.util.Log
import com.chriscartland.garage.config.APP_CONFIG
import com.chriscartland.garage.config.ServerConfigRepository
import com.chriscartland.garage.domain.model.PushStatus
import com.chriscartland.garage.domain.model.SnoozeRequestStatus
import com.chriscartland.garage.domain.repository.PushRepository
import com.chriscartland.garage.internet.BuildTimestamp
import com.chriscartland.garage.internet.ButtonAckToken
import com.chriscartland.garage.internet.GarageNetworkService
import com.chriscartland.garage.internet.IdToken
import com.chriscartland.garage.internet.RemoteButtonBuildTimestamp
import com.chriscartland.garage.internet.RemoteButtonPushKey
import com.chriscartland.garage.internet.SnoozeDurationParameter
import com.chriscartland.garage.internet.SnoozeEventTimestampParameter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.time.delay
import java.time.Duration
import java.util.Date
import javax.inject.Inject

class PushRepositoryImpl
    @Inject
    constructor(
        private val network: GarageNetworkService,
        private val serverConfigRepository: ServerConfigRepository,
    ) : PushRepository {
        private val _pushButtonStatus = MutableStateFlow(PushStatus.IDLE)
        override val pushButtonStatus: StateFlow<PushStatus> = _pushButtonStatus

        private val _snoozeRequestStatus = MutableStateFlow(SnoozeRequestStatus.IDLE)
        override val snoozeRequestStatus: StateFlow<SnoozeRequestStatus> = _snoozeRequestStatus

        private val _snoozeEndTimeSeconds = MutableStateFlow(0L)
        override val snoozeEndTimeSeconds: StateFlow<Long> = _snoozeEndTimeSeconds

        /**
         * Send a command to the server to push the remote button.
         */
        override suspend fun push(
            idToken: String,
            buttonAckToken: String,
        ) {
            _pushButtonStatus.value = PushStatus.SENDING
            val tag = "pushRemoteButton"
            val serverConfig = serverConfigRepository.getServerConfigCached()
            if (serverConfig == null) {
                Log.e(tag, "Server config is null")
                _pushButtonStatus.value = PushStatus.IDLE
                return
            }
            Log.d(tag, "Pushing remote button")
            Log.d(tag, "Server config: $serverConfig")
            Log.d(tag, "Button ack token: $buttonAckToken")

            if (!APP_CONFIG.remoteButtonPushEnabled) {
                Log.w(tag, "Remote button push is disabled: !remoteButtonPushEnabled")
                delay(Duration.ofMillis(500))
            }
            if (APP_CONFIG.remoteButtonPushEnabled) {
                val response = network.postRemoteButtonPush(
                    remoteButtonBuildTimestamp = RemoteButtonBuildTimestamp(
                        serverConfig.remoteButtonBuildTimestamp,
                    ),
                    buttonAckToken = ButtonAckToken(buttonAckToken),
                    remoteButtonPushKey = RemoteButtonPushKey(
                        serverConfig.remoteButtonPushKey,
                    ),
                    idToken = IdToken(idToken),
                )
                Log.i(tag, "Response: ${response.code()}")
                Log.i(tag, "Response body: ${response.body()}")
            }
            _pushButtonStatus.value = PushStatus.IDLE
        }

        override suspend fun fetchSnoozeEndTimeSeconds() {
            val tag = "fetchSnoozeEndTimeSeconds"
            Log.d(tag, "Fetching snooze end time")

            val serverConfig = serverConfigRepository.getServerConfigCached()
            if (serverConfig == null) {
                Log.e(tag, "Server config is null")
                return
            }
            Log.d(tag, "Server config: $serverConfig")

            if (!APP_CONFIG.snoozeNotificationsOption) {
                Log.w(tag, "Snooze notification disabled: !snoozeNotificationsOption")
                delay(Duration.ofMillis(500))
            }
            if (APP_CONFIG.snoozeNotificationsOption) {
                val response = network.getSnooze(
                    buildTimestamp = BuildTimestamp(serverConfig.buildTimestamp),
                )
                Log.i(tag, "Response: ${response.code()}")
                Log.i(tag, "Response body: ${response.body()}")
                val body = response.body()
                if (body == null) {
                    Log.e(tag, "Error: No response")
                    return
                }
                if (body.error != null) {
                    Log.e(tag, "Error: ${response.body()?.error}")
                    return
                }
                val snooze = body.snooze
                if (snooze == null) {
                    Log.e(tag, "Error: No snooze")
                    return
                }
                val snoozeEndTimeSeconds = body.snooze.snoozeEndTimeSeconds
                if (snoozeEndTimeSeconds == null) {
                    Log.e(tag, "Error: No snooze end time")
                    return
                }
                Log.d(tag, "Snooze end time: $snoozeEndTimeSeconds")
                _snoozeEndTimeSeconds.value = snoozeEndTimeSeconds
            }
            Log.d(tag, "Request complete")
        }

        override suspend fun snoozeOpenDoorsNotifications(
            snoozeDurationHours: String,
            idToken: String,
            snoozeEventTimestampSeconds: Long,
        ) {
            _snoozeRequestStatus.value = SnoozeRequestStatus.SENDING
            val tag = "snoozeOpenDoorsNotifications"
            Log.d(tag, "Requesting to snooze door open notifications for $snoozeDurationHours hours")

            val serverConfig = serverConfigRepository.getServerConfigCached()
            if (serverConfig == null) {
                Log.e(tag, "Server config is null")
                _snoozeRequestStatus.value = SnoozeRequestStatus.IDLE
                return
            }
            Log.d(tag, "Server config: $serverConfig")

            if (!APP_CONFIG.snoozeNotificationsOption) {
                Log.w(tag, "Snooze notification disabled: !snoozeNotificationsOption")
                delay(Duration.ofMillis(500))
            }
            if (APP_CONFIG.snoozeNotificationsOption) {
                val response = network.postSnoozeOpenDoorsNotifications(
                    buildTimestamp = BuildTimestamp(serverConfig.buildTimestamp),
                    remoteButtonPushKey = RemoteButtonPushKey(
                        serverConfig.remoteButtonPushKey,
                    ),
                    idToken = IdToken(idToken),
                    snoozeDuration = SnoozeDurationParameter(snoozeDurationHours),
                    snoozeEventTimestamp = SnoozeEventTimestampParameter(snoozeEventTimestampSeconds),
                )
                Log.i(tag, "Response: ${response.code()}")
                Log.i(tag, "Response body: ${response.body()}")
                if (response.body() == null) {
                    Log.e(tag, "Error: No response")
                    _snoozeRequestStatus.value = SnoozeRequestStatus.ERROR
                    return
                }
                // TODO: Diagnose why body() is null when Retrofit receives {"error":"Disabled"}
                if (response.body()?.error != null) {
                    Log.e(tag, "Error: ${response.body()?.error}")
                    _snoozeRequestStatus.value = SnoozeRequestStatus.ERROR
                    return
                }
            }
            Log.d(tag, "Request complete")
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
    val humanReadable = DateFormat.format("yyyy-MM-dd hh:mm:ss a", now).toString()
    val timestampMillis = now.time
    val appVersion = "AppVersionTODO"
    val buttonAckTokenData = "android-$appVersion-$humanReadable-$timestampMillis"
    val re = Regex("[^a-zA-Z0-9-_.]")
    val filtered = re.replace(buttonAckTokenData, ".")
    return filtered
}

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class PushRepositoryModule {
    @Binds
    abstract fun bindPushRepository(pushRepository: PushRepositoryImpl): PushRepository
}

private const val TAG = "PushRepository"
