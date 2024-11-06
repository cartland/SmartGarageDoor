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

package com.chriscartland.garage.config

import com.chriscartland.garage.BuildConfig

data class AppConfig(
    val server: Server,
    val baseUrl: String,
    val initialData: InitialData,
    val fetchOnViewModelInit: FetchOnViewModelInit,
    val recentEventCount: Int,
    val serverConfigKey: String,
    val snoozeNotificationsOption: Boolean,
    val remoteButtonPushEnabled: Boolean,
    val logSummary: Boolean,
)

enum class Server {
    Development,
    Production,
}

enum class InitialData {
    Demo,
    Empty,
}

enum class FetchOnViewModelInit {
    Yes,
    No,
}

//private val SERVER = Server.Development
private val SERVER = Server.Production

//private val INITIAL_DATA = InitialData.Demo
private val INITIAL_DATA = InitialData.Empty

//private val FETCH_ON_VIEW_MODEL_INIT = FetchOnViewModelInit.No
private val FETCH_ON_VIEW_MODEL_INIT = FetchOnViewModelInit.Yes

object AppLoggerKeys {
    const val INIT_CURRENT_DOOR = "init_current_door"
    const val INIT_RECENT_DOOR = "init_recent_door"
    const val USER_FETCH_CURRENT_DOOR = "user_fetch_current_door"
    const val USER_FETCH_RECENT_DOOR = "user_fetch_recent_door"
    const val FCM_DOOR_RECEIVED = "fcm_door_received"
    const val FCM_SUBSCRIBE_TOPIC = "fcm_subscribe_topic"
}

val APP_CONFIG = AppConfig(
    server = SERVER,
    baseUrl = when (SERVER) {
        Server.Development -> "http://10.0.2.2:5001/escape-echo/us-central1/"
        Server.Production -> "https://us-central1-escape-echo.cloudfunctions.net/"
    },
    initialData = INITIAL_DATA,
    fetchOnViewModelInit = FETCH_ON_VIEW_MODEL_INIT,
    recentEventCount = 30,
    serverConfigKey = BuildConfig.SERVER_CONFIG_KEY,
    snoozeNotificationsOption = false,
    remoteButtonPushEnabled = true,
    logSummary = true,
)
