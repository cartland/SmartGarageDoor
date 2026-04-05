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
import com.chriscartland.garage.data.NetworkConfigDataSource
import com.chriscartland.garage.domain.model.ServerConfig

class RetrofitNetworkConfigDataSource(
    private val network: GarageNetworkService,
) : NetworkConfigDataSource {
    override suspend fun fetchServerConfig(serverConfigKey: String): ServerConfig? {
        try {
            val response = network.getServerConfig(serverConfigKey)
            if (response.code() != 200) {
                Log.e(TAG, "Response code is ${response.code()}")
                return null
            }
            val body = response.body()?.body
            if (body == null) {
                Log.e(TAG, "Response body is null")
                return null
            }
            if (body.buildTimestamp.isNullOrEmpty()) {
                Log.e(TAG, "buildTimestamp is empty")
                return null
            }
            val remoteButtonBuildTimestamp = body.remoteButtonBuildTimestamp
            if (remoteButtonBuildTimestamp.isNullOrEmpty()) {
                Log.e(TAG, "remoteButtonBuildTimestamp is empty")
                return null
            }
            if (body.remoteButtonPushKey.isNullOrEmpty()) {
                Log.e(TAG, "remoteButtonPushKey is empty")
                return null
            }
            return ServerConfig(
                buildTimestamp = body.buildTimestamp,
                remoteButtonBuildTimestamp = remoteButtonBuildTimestamp,
                remoteButtonPushKey = body.remoteButtonPushKey,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching server config: $e")
            return null
        }
    }
}

private const val TAG = "RetrofitNetworkConfig"
