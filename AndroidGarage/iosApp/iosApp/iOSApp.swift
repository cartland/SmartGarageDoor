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

import SwiftUI
@preconcurrency import shared

/// SwiftUI entry point.
///
/// Builds the Kotlin DI graph (`NativeComponent`) exactly once at launch and
/// hands it to the view tree, mirroring battery-butler's `iOSApp.swift` and
/// the Android `AppComponent` lifetime.
///
/// **Phase B baseline:** the graph is built with `NoOpAuthBridge` /
/// `NoOpMessagingBridge` (inert auth + push) and `defaultDevAppConfig`
/// (placeholder backend). No Firebase, no Apple Developer account required —
/// this proves the framework + DI graph integrate and the app launches on the
/// simulator. The real Firebase bridges (`FirebaseAuthBridge`,
/// `FirebaseMessagingBridge`) and `AppConfig` read from `Info.plist` land in
/// later PRs once the iOS Firebase config (`GoogleService-Info.plist`) exists.
@main
struct GarageApp: App {
    let component: NativeComponent

    init() {
        component = IosNativeHelper().createComponent(
            authBridge: NoOpAuthBridge.shared,
            messagingBridge: NoOpMessagingBridge.shared,
            appConfig: IosNativeHelper.companion.defaultDevAppConfig
        )
    }

    var body: some Scene {
        WindowGroup {
            MainScreen(component: component)
        }
    }
}
