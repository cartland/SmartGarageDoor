---
category: reference
status: active
last_verified: 2026-06-30
---

# iOS App Setup & Release Runbook

How the iOS app (`AndroidGarage/iosApp/`) was created and configured across Apple
Developer, App Store Connect, and Firebase; what configuration lives where; what is
a secret (and how it is handled without entering the repo); and how to ship a build
to TestFlight. This is the iOS analog of [`FIREBASE_DEPLOY_SETUP.md`](FIREBASE_DEPLOY_SETUP.md).

Build/run mechanics (XcodeGen, the `shared.framework` pre-build script) and the
Swift↔Kotlin bridge live in [`../AndroidGarage/iosApp/README.md`](../AndroidGarage/iosApp/README.md);
the construction status/plan is in
[`../AndroidGarage/docs/PENDING_FOLLOWUPS.md`](../AndroidGarage/docs/PENDING_FOLLOWUPS.md) § 1.

## Identifiers at a glance

| Thing | Value | Secret? |
|---|---|---|
| Apple Developer Team ID | `4EFTFGDT4G` | No — appears in every app's provisioning; committed in `project.yml` |
| Bundle ID / App ID | `com.chriscartland.garage` | No (same string as the Android `applicationId`; Apple's namespace is separate from Google's, so reuse is fine) |
| App Store Connect app name | `Garage by Chris Cartland` | No (public store name; chosen because "Garage" / "Smart Garage Door" were taken) |
| App Store Connect SKU | `smart-garage-door` | No (internal id, never shown publicly) |
| Firebase project | `escape-echo` | No |
| Account holder | Christopher Cartland | — |

## What's a secret vs. committed (the map)

| Item | Secret? | Where it lives | How it's provided |
|---|---|---|---|
| Apple Team ID | No | `project.yml` `DEVELOPMENT_TEAM` | committed |
| `GoogleService-Info.plist` | No* | `iosApp/iosApp/GoogleService-Info.plist` | committed |
| Signing certificates + provisioning profiles | — | macOS Keychain + Apple servers | created on demand by Xcode **automatic signing**; never in repo |
| `GARAGE_SERVER_CONFIG_KEY` (door backend) | **Yes** | `iosApp/Secrets.local.xcconfig` (**gitignored**) | build setting → `$(GARAGE_SERVER_CONFIG_KEY)` substituted into `Info.plist` |
| APNs auth key (`.p8`) | **Yes** | uploaded to Firebase Console; `.p8` file kept offline by the maintainer | created in the Apple Developer portal, uploaded to Firebase Cloud Messaging — never in repo |
| Apple ID + password | **Yes** | maintainer's Apple account | interactive sign-in in Xcode / the portals — never in repo |
| App Store Connect API key (CI release) | **Yes** | GitHub Actions secrets (`APP_STORE_CONNECT_KEY_ID` / `_ISSUER_ID` / `_KEY_P8`) — **Admin** role | never on a dev machine; see "Automated releases" |

\* `GoogleService-Info.plist` is committed on purpose: a Firebase **iOS client config**
is designed to ship inside the app bundle (it's extractable from any distributed
app), so it is not a credential to hide. Security comes from Firebase Auth + the
server-side email allowlist + Firestore/Functions rules, not from concealing this
file. Mirrors the committed `google-services.json` decision on Android.

## Setup procedures (by system)

### 1. Apple Developer Program
Enrolled the Apple Developer Program (account holder: Christopher Cartland). The
**Team ID** is `4EFTFGDT4G` — read it at developer.apple.com → Account → Membership
details, or Xcode → Settings → Accounts. Not a secret.

### 2. App ID (Certificates, Identifiers & Profiles)
developer.apple.com → **Identifiers** → ＋ → **App IDs** → **App**:
- **Explicit** Bundle ID `com.chriscartland.garage`.
- Capabilities: **Push Notifications** checked (leave **Broadcast** *unchecked* — FCM doesn't use broadcast push; Google Sign-In needs no special capability).

### 3. APNs authentication key (`.p8`) → Firebase
For real FCM push (FCM → APNs → device):
- Apple Developer portal → **Keys** → ＋ → enable **Apple Push Notifications service (APNs)** → download the **`.p8`** (downloadable **once**; it's a secret — store it offline, never commit).
- Firebase Console → project `escape-echo` → Project Settings → **Cloud Messaging** → **APNs Authentication Key** → upload the `.p8` (with its Key ID + the Team ID).

### 4. Firebase iOS app
- Firebase Console → project `escape-echo` → Add app → **iOS**, bundle `com.chriscartland.garage`.
- Download **`GoogleService-Info.plist`** → committed at `iosApp/iosApp/GoogleService-Info.plist`.
- Firebase Auth → enable the **Google** sign-in provider.
- The plist's `REVERSED_CLIENT_ID` is wired into `Info.plist` → `CFBundleURLTypes` so the Google Sign-In OAuth callback returns to the app.

### 5. App Store Connect app record
appstoreconnect.apple.com → **Apps** → ＋ → **New App**:
- Platform **iOS**; Name `Garage by Chris Cartland`; Primary Language English (U.S.);
  Bundle ID `com.chriscartland.garage` (appears in the dropdown only after step 2);
  SKU `smart-garage-door`; User Access Full Access.
- This reserves the upload target; screenshots/description/pricing are not needed for TestFlight.

### 6. Code signing + entitlements (in `project.yml` / `Info.plist` — committed)
All committed; the `.xcodeproj` is regenerated from `project.yml` via XcodeGen.
- `DEVELOPMENT_TEAM = 4EFTFGDT4G`, `CODE_SIGN_STYLE = Automatic` (Xcode manages the
  Apple Development + Apple Distribution certs and provisioning profiles on demand;
  those artifacts live in the Keychain / Apple's servers, never in the repo).
- `CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements`, which declares
  `aps-environment = $(APS_ENVIRONMENT)`.
- `project.yml` per-config build settings drive that: **Debug → `development`**
  (dev-device APNs sandbox), **Release → `production`** (TestFlight / App Store APNs).
- `Info.plist`: `UIBackgroundModes` → `remote-notification` (lets FCM data messages
  wake the app) and `ITSAppUsesNonExemptEncryption = false` (skips the
  export-compliance prompt at upload — only standard HTTPS is used).
- CI builds pass `CODE_SIGNING_ALLOWED=NO`, so signing is inert there; signing is
  only exercised by a real archive on the maintainer's Mac.

### 7. App icon
The App Store rejects any upload (TestFlight included) without a 1024×1024 marketing
icon, and an empty `AppIcon` set also drops `CFBundleIconName`. The icon:
- Rendered from `AndroidGarage/distribution/playstore/src/icon.svg` (the
  `GarageDoorCanvas` port — closed green door on `#D7E8CE`) at 1024 via `qlmanage`,
  then **flattened to opaque RGB** (no alpha — the App Store requires it).
- Added as a single **universal 1024** entry in
  `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/`; Xcode's `actool`
  auto-generates the per-device sizes (120 iPhone, 152 iPad, …) and emits
  `CFBundleIconName` from it. Verify standalone with:
  `actool --app-icon AppIcon --output-partial-info-plist /tmp/p.plist --compile /tmp/out --platform iphoneos --minimum-deployment-target 16.0 --target-device iphone --target-device ipad AndroidGarage/iosApp/iosApp/Assets.xcassets`.

### 8. First TestFlight build (manual, via Xcode)
1. Toolbar destination = **Any iOS Device (arm64)** (Archive is greyed out on a simulator).
2. Confirm **Scheme → Archive → Build Configuration = Release** (→ production APNs entitlement).
3. **Product → Archive** (builds the device `shared.framework` then the Release app).
4. Organizer → **Validate App** (dry-run; catches issues without consuming an upload).
5. **Distribute App → TestFlight Internal Only → Automatically manage signing → Upload.**
6. Build processes in App Store Connect → TestFlight (minutes to ~an hour), then is
   available to internal testers. No export-compliance prompt (handled by the plist key).

The simulator cannot install a TestFlight build (no TestFlight app on the simulator;
the artifact is device-arch/device-signed). For the simulator, run directly from
Xcode (⌘R). Real push *delivery* requires a physical device.

## Reproduce on a fresh machine / for a new maintainer
1. Add the Apple ID to **Xcode → Settings → Accounts** (must have access to team `4EFTFGDT4G`).
2. Create `iosApp/Secrets.local.xcconfig` with `GARAGE_SERVER_CONFIG_KEY = <key>`
   (get the value from the maintainer / the Firebase server config — see
   [`FIREBASE_CONFIG_AUTHORITY.md`](FIREBASE_CONFIG_AUTHORITY.md)). Without it the app
   builds and runs but door data won't load (Auth still works).
3. `brew install xcodegen`, then `xcodegen generate --spec AndroidGarage/iosApp/project.yml --project AndroidGarage/iosApp`.
4. Automatic signing recreates the certs/profiles on first archive — no manual cert export needed.
   The APNs `.p8` is already on Firebase (no per-machine step); only re-upload it if the key is rotated.

## Automated releases (`release-ios.sh` + `release-ios.yml`)

Mirrors the Android model. `scripts/release-ios.sh` computes the next `ios/N` tag
(N = build number), gates on a clean tree + a `validate-ios.sh` marker + an
`iosApp/CHANGELOG.md` entry for `MARKETING_VERSION`, and pushes the tag.
`.github/workflows/release-ios.yml` (macOS) reacts to `ios/[0-9]*`: it archives the
Release app (overriding `CURRENT_PROJECT_VERSION` to N so the tag owns the build
number), then `xcodebuild -exportArchive` with `destination=upload` ships it to
**TestFlight Internal**. Same flags + `--check` copy-paste workflow as
`release-android.sh`. Deliberately **not Xcode Cloud** — keeps the release pipeline
in GitHub Actions, consistent with Android/Firebase.

### One-time: create the App Store Connect API key + GitHub secrets
The workflow signs and uploads via an **App Store Connect API key** (no cert is
imported into a keychain — `xcodebuild -allowProvisioningUpdates` creates the
Distribution cert + profile on demand):
1. appstoreconnect.apple.com → **Users and Access → Integrations → App Store Connect API**
   → generate a key with the **Admin** role → note the **Issuer ID** + **Key ID**,
   download the **`.p8`** (downloadable once — store it offline; it's a secret).
   **The role MUST be Admin, not App Manager.** Cloud signing (`-allowProvisioningUpdates`)
   creates the *Distribution* certificate on demand, and only Admin can do that — an
   App Manager key archives fine but fails export with `Cloud signing permission error` /
   `No signing certificate "iOS Distribution" found` (empirically, `ios/1` attempt 1).
   You can't change a key's role; regenerate as Admin and revoke the old key.
2. Add four **GitHub Actions repo secrets** (Settings → Secrets and variables → Actions):
   - `APP_STORE_CONNECT_KEY_ID` — the Key ID
   - `APP_STORE_CONNECT_ISSUER_ID` — the Issuer ID
   - `APP_STORE_CONNECT_KEY_P8` — the full contents of the `.p8`
   - `GARAGE_SERVER_CONFIG_KEY` — the door-backend key (so the TestFlight build loads door data)

### Build numbering (tag `ios/N` == `CFBundleVersion`)
The tag `ios/N` sets `CURRENT_PROJECT_VERSION = N`, which becomes the build's
`CFBundleVersion`. App Store Connect requires each build number to be **unique and
strictly increasing**, and it *silently auto-resolves* a collision to a different
number rather than failing — that's how `ios/1` landed as build 2 (a manual upload
had already taken build 1).

**Only GitHub Actions can deploy, so only GitHub Actions checks the number.** The
deploy-capable App Store Connect API key lives *only* in GitHub Actions secrets —
never on a dev machine — so `release-ios.yml` is the single credentialed path to a
build, and it holds the authoritative build-number check:
- **CI pre-flight (authoritative):** the *first* thing the workflow does (before the
  slow toolchain setup + archive) is query App Store Connect (`scripts/asc-latest-build.rb`)
  and **abort loudly** if `N` isn't strictly greater than the latest existing build —
  with logs telling you the exact number to use next. Nothing is archived or uploaded
  on a collision.
- **`release-ios.sh --check` (advisory):** deliberately uses **git tags only** (no ASC
  credentials, so nothing local can bypass Actions). It *suggests* `ios/(highest + 1)`;
  CI has the final say. `--confirm-tag` lets you skip ahead to any higher `ios/N` (e.g.
  the number CI told you), as long as it strictly increases.

If CI aborts saying build `N` is taken, just re-release with the number it reports:
`./scripts/release-ios.sh --confirm-tag ios/<that number>`. (The failed tag is inert —
nothing deployed — and can be deleted: `git push origin :refs/tags/ios/N && git tag -d ios/N`.)

### Cutting a release
1. Bump `MARKETING_VERSION` in `project.yml` (if the user-facing version changed) and
   add a matching `## X.Y.Z` heading to `AndroidGarage/iosApp/CHANGELOG.md`.
2. `./scripts/validate-ios.sh` (writes the marker).
3. `./scripts/release-ios.sh --check` → copy-paste the printed `--confirm-tag ios/N`
   command (a git-based suggestion; CI verifies the actual build number).
4. The tag push triggers `release-ios.yml` → **pre-flight build-number check** →
   archive → upload → the build appears in TestFlight after processing. Monitor the
   Actions run; a failure opens a `release-failure/ios` issue (auto-closed on the next success).

**Status: verified end-to-end (`ios/1`, build 1 / 0.1.0, 2026-06-30).** The
`app-store-connect` method + cloud-signing-via-API-key + `destination: upload` path
works on `macos-latest` and lands the build in TestFlight Internal. The one gotcha
was the API-key role (App Manager → Admin, above). If a future Xcode ever rejects
cloud signing, the fallback is importing a Distribution `.p12` + profile from secrets
(manual signing).
