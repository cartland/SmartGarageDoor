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
import com.chriscartland.garage.domain.model.DoorUpdateStrategyId
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
            // The watch polls, and has since before this enum existed:
            // `WearHomeViewModel.onVisible()` runs a foreground refresh loop
            // (10s idle, 2s while a press is waiting on the door) and
            // `onHidden()` stops it. The watch has no FCM registration at
            // all, so PUSH was never available to it.
            //
            // This states the policy; it does not enforce it. The watch runs
            // no AppStartup and therefore no DoorUpdateManager, so nothing
            // reads this value — the loop lives in the ViewModel, screen-
            // scoped, because the cadence depends on ButtonStateMachine
            // state that only the VM holds (an app-scoped manager could not
            // read it: `:usecase` cannot import `:viewmodel`). Unifying the
            // two hosts is a live proposal, not an oversight.
            defaultDoorUpdateStrategy = DoorUpdateStrategyId.POLL,
        )
}
