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

**Phase B/D foundation (current).** The app builds and launches on the simulator
with `NoOpAuthBridge` / `NoOpMessagingBridge` (inert auth + push) and
`defaultDevAppConfig` (placeholder backend) — no Firebase or Apple Developer
account required. This proves the framework + DI graph integrate.

**Pending (gated on user setup):**
- Real screens bound to the `Default*ViewModel`s (Phase E).
- iOS CI on `macos-latest` (Phase F).
- `FirebaseAuthBridge` / `FirebaseMessagingBridge`, `GoogleService-Info.plist`,
  and `AppConfig` read from `Info.plist` (Phase C) — needs the Firebase iOS app +
  APNs key.
- TestFlight + App Store (Phases F/G) — needs the Apple Developer account.
