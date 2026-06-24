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
/// The Kotlin DI graph (`NativeComponent`) is built exactly once in
/// `AppDelegate.didFinishLaunching` (after `FirebaseApp.configure()`), mirroring
/// battery-butler's `iOSApp.swift` and the Android `AppComponent` lifetime. This
/// `App` reads the already-built graph from the delegate and hands it to the
/// view tree.
///
/// **Phase C:** built with the real `FirebaseAuthBridge` /
/// `FirebaseMessagingBridge` and `AppConfig` read from `Info.plist`. Firebase
/// Auth (Google Sign-In) works on the simulator; garage door data needs the
/// `GARAGE_BASE_URL` / `GARAGE_SERVER_CONFIG_KEY` Info.plist values, and push
/// delivery needs the APNs key uploaded to Firebase.
@main
struct GarageApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            MainScreen(component: appDelegate.component)
        }
    }
}
