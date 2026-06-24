---
category: reference
status: active
last_verified: 2026-06-10
---

# iosApp — SwiftUI client

SwiftUI front-end for the Smart Garage Door system. Consumes the shared
Kotlin business logic via the `:iosFramework` Gradle module (`shared.framework`),
mirroring the Android app's clean architecture. See the iOS construction plan in
[`../docs/PENDING_FOLLOWUPS.md`](../docs/PENDING_FOLLOWUPS.md) § "iOS app construction".

## Layout

```
iosApp/
├── project.yml              # XcodeGen spec — source of truth for the .xcodeproj
├── iosApp/                  # App entry + Info.plist + asset catalog
│   └── iOSApp.swift         # @main; builds the NativeComponent DI graph
├── Core/                    # Cross-screen infrastructure
│   ├── SharedViewModel.swift   # Ties a Kotlin viewModelScope to a SwiftUI view
│   └── Theme/               # Colors + spacing tokens mirroring Android
└── Features/                # One folder per screen
    └── Main/                # Tab shell (Home/History/Profile/Functions/Diagnostics)
```

The `.xcodeproj` is **generated, never committed** (see `.gitignore`). `project.yml`
is the reviewable source of truth.

## Build (simulator)

```bash
brew install xcodegen                 # one-time
cd AndroidGarage/iosApp
xcodegen generate
xcodebuild -project iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

A pre-build Run Script invokes `:iosFramework:embedAndSignAppleFrameworkForXcode`,
which compiles `shared.framework` for the active SDK/arch and embeds + signs it
into the app. `FRAMEWORK_SEARCH_PATHS` points at that task's output.

## Status

**Phase C — Firebase auth wired.** `AppDelegate` runs `FirebaseApp.configure()`,
builds the `NativeComponent` with the real `FirebaseAuthBridge` /
`FirebaseMessagingBridge` (Core/Firebase/), and runs `AppStartup`. Google Sign-In
(Profile tab) presents via `GoogleSignInCoordinator` and signs into Firebase;
`AppConfig` is read from `Info.plist`. Verified locally: builds + links against the
Firebase SPM packages and launches on the simulator (real signed-out auth state +
notification-permission prompt render).

The Swift↔Kotlin bridge conformance is non-obvious — see the KDoc in
`Core/Firebase/FirebaseAuthBridge.swift` and `iosFramework/.../IosAuthUserStateHolder.kt`:
SKIE exposes the Kotlin suspend bridge methods as `__`-prefixed `async throws`
Swift requirements, and `observeAuthUser()` must return
`SkieSwiftOptionalFlow<DataAuthUserInfo>` (Swift can't construct a Kotlin Flow, so
the holder supplies one).

**Pending (gated on user setup):**
- Garage backend `GARAGE_BASE_URL` + `GARAGE_SERVER_CONFIG_KEY` in `Info.plist`
  (iOS equivalent of Android's `SERVER_CONFIG_KEY` + base URL). Until set, the app
  uses `defaultDevAppConfig`, so Firebase Auth works but **door data does not load**.
- APNs `.p8` key uploaded to Firebase Cloud Messaging. Until then **push is not
  delivered**; the messaging bridge + registration compile and run inertly.
- FCM notification-receive → `DoorEvent` parsing (mirrors Android `FcmMessageHandler`),
  wired in `AppDelegate.userNotificationCenter(_:didReceive:)`. Deferred until the
  APNs key lands (untestable without it); door state still refreshes on cold start.
- TestFlight + App Store (Phase G) — needs the Apple Developer account.
