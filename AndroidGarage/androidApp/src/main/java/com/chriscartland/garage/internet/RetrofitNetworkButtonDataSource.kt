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

package com.chriscartland.garage.internet

import android.util.Log
import com.chriscartland.garage.data.NetworkButtonDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

class RetrofitNetworkButtonDataSource
    @Inject
    constructor(
        private val network: GarageNetworkService,
    ) : NetworkButtonDataSource {
        override suspend fun pushButton(
            remoteButtonBuildTimestamp: String,
            buttonAckToken: String,
            remoteButtonPushKey: String,
            idToken: String,
        ): Boolean =
            try {
                val response = network.postRemoteButtonPush(
                    remoteButtonBuildTimestamp = RemoteButtonBuildTimestamp(remoteButtonBuildTimestamp),
                    buttonAckToken = ButtonAckToken(buttonAckToken),
                    remoteButtonPushKey = RemoteButtonPushKey(remoteButtonPushKey),
                    idToken = IdToken(idToken),
                )
                Log.i(TAG, "Push response: ${response.code()}")
                response.isSuccessful
            } catch (e: Exception) {
                Log.e(TAG, "Push error: $e")
                false
            }

        override suspend fun snoozeNotifications(
            buildTimestamp: String,
            remoteButtonPushKey: String,
            idToken: String,
            snoozeDurationHours: String,
            snoozeEventTimestampSeconds: Long,
        ): Boolean {
            return try {
                val response = network.postSnoozeOpenDoorsNotifications(
                    buildTimestamp = BuildTimestamp(buildTimestamp),
                    remoteButtonPushKey = RemoteButtonPushKey(remoteButtonPushKey),
                    idToken = IdToken(idToken),
                    snoozeDuration = SnoozeDurationParameter(snoozeDurationHours),
                    snoozeEventTimestamp = SnoozeEventTimestampParameter(snoozeEventTimestampSeconds),
                )
                Log.i(TAG, "Snooze response: ${response.code()}")
                val body = response.body()
                if (body == null || body.error != null) {
                    Log.e(TAG, "Snooze error: ${body?.error}")
                    return false
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Snooze error: $e")
                false
            }
        }

        override suspend fun fetchSnoozeEndTimeSeconds(buildTimestamp: String): Long {
            return try {
                val response = network.getSnooze(
                    buildTimestamp = BuildTimestamp(buildTimestamp),
                )
                val body = response.body()
                if (body == null || body.error != null) {
                    Log.e(TAG, "Snooze fetch error: ${body?.error}")
                    return 0L
                }
                body.snooze?.snoozeEndTimeSeconds ?: 0L
            } catch (e: Exception) {
                Log.e(TAG, "Snooze fetch error: $e")
                0L
            }
        }
    }

@Module
@InstallIn(SingletonComponent::class)
object NetworkButtonDataSourceModule {
    @Provides
    @Singleton
    fun provideNetworkButtonDataSource(network: GarageNetworkService): NetworkButtonDataSource = RetrofitNetworkButtonDataSource(network)
}

private const val TAG = "RetrofitNetworkButton"
