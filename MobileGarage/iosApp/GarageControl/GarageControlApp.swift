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
/// battery-butler's app entry point and the Android `AppComponent` lifetime. This
/// `App` reads the already-built graph from the delegate and hands it to the
/// view tree.
///
/// **Phase C:** built with the real `FirebaseAuthBridge` /
/// `FirebaseMessagingBridge` and `AppConfig` read from `Info.plist`. Firebase
/// Auth (Google Sign-In) works on the simulator; garage door data needs the
/// `GARAGE_BASE_URL` / `GARAGE_SERVER_CONFIG_KEY` Info.plist values, and push
/// delivery needs the APNs key uploaded to Firebase.
@main
struct GarageControlApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            MainScreen(component: appDelegate.component)
                // Report visibility into the shared `AppVisibilityState` so
                // the door-update strategy knows when to run. iOS defaults to
                // `DoorUpdateStrategyId.POLL`, which fetches nothing until it
                // is told the app is visible — this is the signal that turns
                // it on, and the reason a fresh value is on screen the moment
                // the user comes back.
                //
                // `onAppear` as well as `onChange`: the first `.active` is the
                // value `scenePhase` already holds at launch, so `onChange`
                // never fires for it and polling would wait for the user to
                // background the app once before ever starting.
                .onAppear { report(scenePhase) }
                // iOS 16 deployment target: the single-parameter `onChange`.
                // Read the closure's value, never `self.scenePhase` — the
                // handler that runs can be one a previous body evaluation
                // registered, so the captured property may be stale (the
                // `AnimatedDoorCanvas` bug, #1055).
                .onChange(of: scenePhase) { newPhase in report(newPhase) }
        }
    }

    /// Only `.active` counts as visible. `.inactive` covers the app switcher
    /// and a pulled-down Control Center — moments where a poll would spend a
    /// request on a screen nobody is reading.
    private func report(_ phase: ScenePhase) {
        appDelegate.component.appVisibilityState.setVisible(visible: phase == .active)
    }
}
