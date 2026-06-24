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

**How this app should relate to the Android app:** [ADR-029](../docs/DECISIONS.md#adr-029-ios--android--feature-parity-platform-native-design-one-shared-identity) — eventual 1:1 *capability* parity, platform-native/idiomatic UI (don't copy Android's layout pixel-for-pixel), and one shared "Garage" identity (door visualization, state semantics + colors, naming, tab order) carried by the shared KMP typed states.

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

**Door data + FCM receive (wired):** the garage backend config (`GARAGE_BASE_URL`
committed; secret `GARAGE_SERVER_CONFIG_KEY` via gitignored `Secrets.local.xcconfig`)
flows into the shared Ktor client — verified on the simulator showing real door
STATUS. FCM data messages are parsed with the **shared** `FcmPayloadParser`
(via `IosNativeHelper.parseFcmDoorEvent`, so the payload-key contract matches
Android) in `AppDelegate.application(_:didReceiveRemoteNotification:…)` and applied
through `ReceiveFcmDoorEventUseCase` → the door-event cache the UI observes.

**Pending:**
- **APNs key** is uploaded to Firebase, but real push *delivery* needs a signed
  device build (`aps-environment` entitlement). The FCM-receive path is wired but
  **not runtime-verified**: `simctl` silent (`content-available`) push does not
  reliably reach `didReceiveRemoteNotification` (a known limitation, compounded by
  Firebase app-delegate swizzling), so this is a device / Phase-G check.
- **Google Sign-In end-to-end** — wired; the OAuth flow needs an interactive tap-through.
- **TestFlight + App Store** (Phase G) — needs device signing + entitlements.
