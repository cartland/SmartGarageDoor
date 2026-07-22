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

package com.chriscartland.garage.wear.config

import com.chriscartland.garage.domain.model.AppConfig
import com.chriscartland.garage.wear.BuildConfig

/**
 * Builds [AppConfig] from the Wear module's [BuildConfig] values.
 *
 * Mirrors the phone's `AppConfigFactory`; all configuration comes from
 * build.gradle.kts buildConfigField declarations or local.properties secrets.
 */
object WearAppConfigFactory {
    fun create(): AppConfig =
        AppConfig(
            baseUrl = BuildConfig.BASE_URL,
            // The watch never renders door history; this only sizes the
            // (unused) recent-events fetch, so keep it small.
            recentEventCount = 25,
            serverConfigKey = BuildConfig.SERVER_CONFIG_KEY,
            // Snooze management stays a phone concern.
            snoozeNotificationsOption = false,
            remoteButtonPushEnabled = true,
        )
}
