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

import FirebaseCore
import FirebaseMessaging
import GoogleSignIn
import UIKit
import UserNotifications
@preconcurrency import shared

/// Owns Firebase startup and the Kotlin DI graph for the app's lifetime.
///
/// Mirrors the Android `Application` + `MainActivity.onCreate` split: configure
/// Firebase, build the `NativeComponent` with the real Swift bridges, run
/// `AppStartup`, and register for push. The component is built exactly once and
/// read by the SwiftUI `App` (`iOSApp.swift`).
final class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate, UNUserNotificationCenterDelegate {
    private(set) var component: NativeComponent!

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()

        // Google Sign-In reads its client ID from the Firebase app options
        // (auto-populated from GoogleService-Info.plist).
        if let clientID = FirebaseApp.app()?.options.clientID {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        }

        component = IosNativeHelper().createComponent(
            authBridge: FirebaseAuthBridge(),
            messagingBridge: FirebaseMessagingBridge(),
            appConfig: AppConfigFactory.fromInfoPlist()
        )
        _ = component.appStartup.run()

        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        Task { @MainActor in
            _ = try? await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .badge, .sound])
            application.registerForRemoteNotifications()
        }
        return true
    }

    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        GIDSignIn.sharedInstance.handle(url)
    }

    // MARK: MessagingDelegate

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        // FCM topic registration is driven by the shared `FcmRegistrationManager`
        // through `MessagingBridge`; this hook is just the APNs->FCM token-refresh
        // signal. (FirebaseAppDelegateProxyEnabled stays on, so the APNs token is
        // forwarded to FCM automatically.)
    }

    // MARK: UNUserNotificationCenterDelegate

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .sound]
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        // TODO (Phase C, push-receive): parse
        // `response.notification.request.content.userInfo` into a `DoorEvent` and
        // call `component.receiveFcmDoorEventUseCase(event:)` — mirrors Android's
        // `FcmMessageHandler`. Deferred until the APNs key lands (untestable
        // without it); door state still refreshes on cold start via
        // `InitialDoorFetchManager`.
    }
}
