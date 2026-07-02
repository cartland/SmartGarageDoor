---
category: reference
status: active
last_verified: 2026-06-10
---

# iosApp — GarageControl (SwiftUI client)

SwiftUI front-end for the Smart Garage Door system. Consumes the shared
Kotlin business logic via the `:iosFramework` Gradle module (`shared.framework`),
mirroring the Android app's clean architecture. See the iOS construction plan in
[`../docs/PENDING_FOLLOWUPS.md`](../docs/PENDING_FOLLOWUPS.md) § "iOS app construction".

**How this app should relate to the Android app:** [ADR-029](../docs/DECISIONS.md#adr-029-ios--android--feature-parity-platform-native-design-one-shared-identity) — eventual 1:1 *capability* parity, platform-native/idiomatic UI (don't copy Android's layout pixel-for-pixel), and one shared "Garage" identity (door visualization, state semantics + colors, naming, tab order) carried by the shared KMP typed states.

**Apple Developer / App Store Connect / Firebase setup, signing, the secrets map, and the TestFlight procedure:** [`../../docs/IOS_RELEASE_SETUP.md`](../../docs/IOS_RELEASE_SETUP.md).

## Layout

```
iosApp/
├── project.yml              # XcodeGen spec — source of truth for the .xcodeproj
├── GarageControl/           # App entry + Info.plist + asset catalog
│   └── GarageControlApp.swift  # @main; builds the NativeComponent DI graph
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
cd MobileGarage/iosApp
xcodegen generate
xcodebuild -project GarageControl.xcodeproj -scheme GarageControl \
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

**Signing + first TestFlight build — DONE (2026-06-30).** Automatic code signing,
the `aps-environment` push entitlement (Debug=development / Release=production), and
the 1024 app icon are wired (see the setup runbook); the first build is uploaded to
TestFlight Internal. Real distribution signing + production-APNs entitlement passed
validation + upload.

**Pending:**
- **On-device verification** — real push *delivery* (FCM→APNs→device) and the Google
  Sign-In OAuth tap-through still need the installed TestFlight build / a physical
  device to confirm end-to-end. The FCM-receive path is wired but not runtime-verified
  (`simctl` silent push is unreliable; Firebase app-delegate swizzling compounds it).
- **Release automation + App Store** (Phase G) — `scripts/release-ios.sh` +
  `.github/workflows/release-ios.yml`, then the App Store listing/submit. See
  [`../../docs/IOS_RELEASE_SETUP.md`](../../docs/IOS_RELEASE_SETUP.md) § "Future: automated releases".
