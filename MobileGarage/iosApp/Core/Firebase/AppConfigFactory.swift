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

import Foundation
@preconcurrency import shared

/// Builds the shared `AppConfig` from `Info.plist`, mirroring how Android reads
/// `BuildConfig`. The garage backend `baseUrl` + `serverConfigKey` are the iOS
/// equivalents of Android's `SERVER_CONFIG_KEY` + base URL; supply them via the
/// `GARAGE_BASE_URL` / `GARAGE_SERVER_CONFIG_KEY` Info.plist keys (or an
/// xcconfig). Until they are set, the values fall back to
/// `IosNativeHelper.defaultDevAppConfig` (placeholder backend), so Firebase
/// Auth works but garage door data does not load.
enum AppConfigFactory {
    static func fromInfoPlist() -> AppConfig {
        let dev = IosNativeHelper.companion.defaultDevAppConfig

        func nonEmptyString(_ key: String) -> String? {
            guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String,
                  !value.isEmpty else { return nil }
            return value
        }

        return AppConfig(
            baseUrl: nonEmptyString("GARAGE_BASE_URL") ?? dev.baseUrl,
            recentEventCount: dev.recentEventCount,
            serverConfigKey: nonEmptyString("GARAGE_SERVER_CONFIG_KEY") ?? dev.serverConfigKey,
            snoozeNotificationsOption: dev.snoozeNotificationsOption,
            remoteButtonPushEnabled: dev.remoteButtonPushEnabled
        )
    }
}
