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
import com.chriscartland.garage.domain.model.AppConfig

/**
 * Builds [AppConfig] from [BuildConfig] values.
 *
 * All configuration comes from build.gradle.kts buildConfigField declarations
 * or local.properties secrets — nothing is hardcoded here.
 */
object AppConfigFactory {
    fun create(): AppConfig =
        AppConfig(
            baseUrl = BuildConfig.BASE_URL,
            recentEventCount = 30,
            serverConfigKey = BuildConfig.SERVER_CONFIG_KEY,
            snoozeNotificationsOption = true,
            remoteButtonPushEnabled = true,
        )
}
