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

package com.chriscartland.garage.iosframework

import com.chriscartland.garage.data.AuthBridge
import com.chriscartland.garage.data.MessagingBridge
import com.chriscartland.garage.datalocal.DataStoreFactory
import com.chriscartland.garage.datalocal.DatabaseFactory
import com.chriscartland.garage.domain.model.AppConfig
import platform.Foundation.NSBundle

/**
 * Single Swift-callable entry point that builds the iOS DI graph.
 *
 * Pattern matches battery-butler's `IosNativeHelper` — Swift calls
 * `IosNativeHelper().createComponent(...)` once at app launch and
 * passes the result through the view tree.
 *
 * The Swift side supplies platform-specific bridges
 * ([authBridge] = `FirebaseAuthBridge.swift`,
 *  [messagingBridge] = `FirebaseMessagingBridge.swift`). When Swift
 * impls are not yet wired (PR 5 baseline), pass [NoOpAuthBridge] /
 * [NoOpMessagingBridge] — the framework still builds and the DI
 * graph instantiates; only auth/push are inert.
 *
 * [DatabaseFactory] and [DataStoreFactory] are pure-Kotlin iOS
 * actuals (NSDocumentDirectory path resolution); constructed here.
 *
 * [appConfig] is supplied by Swift, typically read from `Info.plist`
 * (`Bundle.main.object(forInfoDictionaryKey:)`). For PR 5 baseline
 * the Swift side can pass [defaultDevAppConfig] as a placeholder.
 */
class IosNativeHelper {
    fun createComponent(
        authBridge: AuthBridge,
        messagingBridge: MessagingBridge,
        appConfig: AppConfig,
    ): NativeComponent =
        NativeComponent::class.create(
            authBridge = authBridge,
            messagingBridge = messagingBridge,
            databaseFactory = DatabaseFactory(),
            dataStoreFactory = DataStoreFactory(),
            appConfig = appConfig,
            appVersion = currentAppVersion(),
        )

    private fun currentAppVersion(): String {
        val bundle = NSBundle.mainBundle
        val version =
            bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
                ?: "Unknown"
        val build = bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: "0"
        return "$version ($build)"
    }

    companion object {
        /**
         * Placeholder [AppConfig] for early-iteration iOS builds.
         * Real values must come from `Info.plist` keys in the iOS app
         * before any production-track release.
         */
        val defaultDevAppConfig: AppConfig = AppConfig(
            baseUrl = "https://example.invalid",
            recentEventCount = 50,
            serverConfigKey = "",
            snoozeNotificationsOption = true,
            remoteButtonPushEnabled = true,
        )
    }
}
