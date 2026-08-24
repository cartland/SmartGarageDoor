---
category: reference
status: active
last_verified: 2026-08-23
---

# iOS App Setup & Release Runbook

How the iOS app (`MobileGarage/iosApp/`) was created and configured across Apple
Developer, App Store Connect, and Firebase; what configuration lives where; what is
a secret (and how it is handled without entering the repo); and how to ship a build
to TestFlight. This is the iOS analog of [`FIREBASE_DEPLOY_SETUP.md`](FIREBASE_DEPLOY_SETUP.md).

Build/run mechanics (XcodeGen, the `shared.framework` pre-build script) and the
Swift↔Kotlin bridge live in [`../MobileGarage/iosApp/README.md`](../MobileGarage/iosApp/README.md);
the construction status/plan is in
[`../MobileGarage/docs/PENDING_FOLLOWUPS.md`](../MobileGarage/docs/PENDING_FOLLOWUPS.md) § 1.

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
| `GoogleService-Info.plist` | No* | `iosApp/GarageControl/GoogleService-Info.plist` | committed |
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
- Download **`GoogleService-Info.plist`** → committed at `iosApp/GarageControl/GoogleService-Info.plist`.
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
- `CODE_SIGN_ENTITLEMENTS = GarageControl/GarageControl.entitlements`, which declares
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
- Rendered from `MobileGarage/distribution/playstore/src/icon.svg` (the
  `GarageDoorCanvas` port — closed green door on `#D7E8CE`) at 1024 via `qlmanage`,
  then **flattened to opaque RGB** (no alpha — the App Store requires it).
- Added as a single **universal 1024** entry in
  `iosApp/GarageControl/Assets.xcassets/AppIcon.appiconset/`; Xcode's `actool`
  auto-generates the per-device sizes (120 iPhone, 152 iPad, …) and emits
  `CFBundleIconName` from it. Verify standalone with:
  `actool --app-icon AppIcon --output-partial-info-plist /tmp/p.plist --compile /tmp/out --platform iphoneos --minimum-deployment-target 16.0 --target-device iphone --target-device ipad MobileGarage/iosApp/GarageControl/Assets.xcassets`.

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
3. `brew install xcodegen`, then `xcodegen generate --spec MobileGarage/iosApp/project.yml --project MobileGarage/iosApp`.
4. Automatic signing recreates the certs/profiles on first archive — no manual cert export needed.
   The APNs `.p8` is already on Firebase (no per-machine step); only re-upload it if the key is rotated.

## Signing: stored cert, not cloud signing

**CI signs with a certificate and profile held as GitHub secrets.** It used to
use cloud signing (`-allowProvisioningUpdates`), which has `xcodebuild` mint an
Apple Distribution certificate on demand.

**Why that was changed (#1191, 2026-08-01).** (It merged 39 minutes *after* the `ios/12` tag was
pushed, so `ios/12` still ran the old cloud-signing path and failed on it. The first release this
code actually signed was `ios/14` on 2026-08-18 — see "Scoping the profile specifier" below for
what `ios/13` hit in between.) Certificates are a small,
account-wide, *capped* resource — Apple allows only a small number of Apple
Distribution certs per account (commonly two; the exact figure has varied by
account type and era, and the portal is the authority). The number does not
change the procedure: when you hit it, something has to be revoked. A CI job that creates one per release is spending a budget it cannot
refill, and when the cap is reached every release fails at the archive step:

```
error: Choose a certificate to revoke. Your account has reached the
maximum number of certificates. To create a new one, you must choose
a certificate to revoke.
error: No profiles for 'com.chriscartland.garage' were found
```

The second line is a knock-on: with no Distribution cert available, Xcode falls
back to looking for a *Development* profile and finds none. **Nothing reaches
App Store Connect when this happens** — export and upload are skipped, so the
build number stays free. Empirically: after both `ios/12` and `ios/13` failed,
the CI pre-flight still reported `Highest existing build on App Store Connect:
11`.

Re-running the same tag only helps when the fix lives **outside the repo** — a
secret, a portal action. A tag-triggered workflow runs the workflow file **from
the tagged commit**, so any fix to `release-ios.yml` itself requires a new tag;
that is why the `ios/13` fix shipped as `ios/14` rather than a re-run.

### The three secrets

| Secret | What it is |
|---|---|
| `IOS_DIST_CERT_P12_BASE64` | base64 of a `.p12` holding the Apple Distribution cert **and its private key** |
| `IOS_DIST_CERT_PASSWORD` | the password that `.p12` was exported with |
| `IOS_PROVISIONING_PROFILE_BASE64` | base64 of the App Store `.mobileprovision` for `com.chriscartland.garage` |

The workflow imports these into a throwaway keychain (random per-run password,
deleted by *Clean secrets*), reads the profile's UUID and Name out of it, and
archives with `CODE_SIGN_STYLE=Manual`. **It fails loudly if any secret is
missing** rather than falling back to cloud signing — a silent fallback would
quietly return to the path that exhausted the quota.

### Producing them (one-time, and after any cert expiry)

Requires a Mac and the Apple Developer portal. A certificate is only useful with
its **private key**, which exists solely on the machine that generated the
signing request — so a cert previously minted by CI cloud signing **cannot be
exported**, because its key died with the runner.

**Try creating the new certificate before revoking anything.** Cloud signing's
managed certs occupy their own bucket, so a cap reached by CI does not
necessarily mean the manual slot is full. On 2026-08-18 the portal listed
exactly one `Distribution Managed` cert — the one CI had been minting against —
and still accepted a brand-new manual *Apple Distribution* cert with no
revocation at all. Revoke only if step 3 is actually refused.

1. **Only if step 3 is refused: free a slot.** [Certificates][certs] → revoke an
   unused Apple Distribution cert. (Revoking invalidates builds signed with it
   that have not yet shipped; TestFlight builds already uploaded are
   unaffected.) See the note above — this was NOT needed in 2026-08.
2. **Generate a signing request.** Keychain Access → *Certificate Assistant* →
   *Request a Certificate From a Certificate Authority* → save to disk. This is
   what creates the private key locally. Set *Request is:* **Saved to disk**,
   leave *CA Email Address* blank, and leave the key-pair defaults alone.

   On macOS 26 **Keychain Access is no longer in Utilities and Spotlight does
   not index it** — it lives at
   `/System/Library/CoreServices/Applications/Keychain Access.app`.
3. **Create the certificate.** [Certificates][certs] → **+** → *Apple
   Distribution* → upload the request → download the `.cer` → double-click to
   install into the login keychain.
4. **Export the `.p12`.** Keychain Access → *My Certificates* → find the new
   *Apple Distribution* row → expand it and confirm it has a private key
   underneath (if not, step 2 was skipped) → right-click → *Export* → `.p12` →
   set a password.
5. **Create the App Store profile.** [Profiles][profiles] → **+** →
   *App Store Connect* → App ID `com.chriscartland.garage` → select the
   certificate from step 3 → download the `.mobileprovision`.
6. **Store all three.** Never paste a secret into a terminal argument or a chat
   — pipe from the file so it stays out of shell history:

   ```bash
   base64 -i /path/to/dist.p12            | gh secret set IOS_DIST_CERT_P12_BASE64
   base64 -i /path/to/profile.mobileprovision | gh secret set IOS_PROVISIONING_PROFILE_BASE64
   gh secret set IOS_DIST_CERT_PASSWORD   # prompts, does not echo
   ```

7. **Delete the local `.p12`** once the secret is set. GitHub Actions is the
   only place deploy-capable credentials are meant to live.

[certs]: https://developer.apple.com/account/resources/certificates/list
[profiles]: https://developer.apple.com/account/resources/profiles/list

### Verifying what you produced

Worth doing before the secrets go anywhere: a wrong `.p12` costs a whole release
cycle to discover.

```bash
# Certificate: type, team, validity. Expect "Apple Distribution: <name> (TEAM)".
openssl x509 -inform DER -in distribution.cer -noout -subject -issuer -dates

# The private key is present iff the identity is listed here.
security find-identity -v -p codesigning

# Profile: app id, distribution-ness, push environment.
security cms -D -i profile.mobileprovision > /tmp/p.plist
/usr/libexec/PlistBuddy -c 'Print :Entitlements:application-identifier' /tmp/p.plist
/usr/libexec/PlistBuddy -c 'Print :Entitlements:get-task-allow' /tmp/p.plist   # false
/usr/libexec/PlistBuddy -c 'Print :Entitlements:aps-environment' /tmp/p.plist  # production
/usr/libexec/PlistBuddy -c 'Print :ProvisionedDevices' /tmp/p.plist            # must NOT exist

# What is actually inside the .p12 (prompts for its password).
/usr/bin/openssl pkcs12 -in dist.p12 -nodes | grep -E "friendlyName|BEGIN.*PRIVATE KEY"
```

Two traps on that last command:

- **Use `/usr/bin/openssl`.** Homebrew's OpenSSL 3.x rejects a Keychain-exported
  `.p12` (legacy PBES1/RC2 encryption); macOS's own LibreSSL reads it natively.
  With the Homebrew build you would need `-legacy`.
- **Do not redirect stderr.** openssl writes its password prompt there, so
  `2>/dev/null` turns a prompt into a silent hang or a blind failure.

A correct single-identity export shows **two** `friendlyName` lines — one for the
certificate bag, one for the private-key bag, the latter named after the CSR's
Common Name — and exactly one `BEGIN PRIVATE KEY`. Two identities would show
four friendlyNames and two keys. `Apple Development` appearing at all means the
wrong row was selected in Keychain Access, and your personal development key is
about to be uploaded to CI.

### Scoping the profile specifier

`PROVISIONING_PROFILE_SPECIFIER` is written to a gitignored
`MobileGarage/iosApp/Signing.local.xcconfig` and pulled in by an optional
`#include?` from the committed `Secrets.xcconfig`. It is deliberately **not** on
the `xcodebuild` command line, and that is load-bearing.

Command-line build settings apply to **every target in the build graph**. With
the specifier there, all ~20 Swift Package dependencies inherit a profile they
cannot carry, and `ios/13` failed roughly twenty times over with:

```
error: Firebase_FirebaseAuth does not support provisioning profiles, but
       provisioning profile "Garage iOS Cert Expires 2027-08-18" has been
       manually specified
```

It is the *only* signing setting the packages reject. `CODE_SIGN_STYLE`,
`DEVELOPMENT_TEAM` and `CODE_SIGN_IDENTITY` stay on the command line, which is
also the only level that outranks the `CODE_SIGN_STYLE: Automatic` declared for
the target in `project.yml`.

**Moving all four into the xcconfig does not work.** Xcode precedence puts a
target's `.pbxproj` settings *above* its xcconfig, so `Automatic` wins while the
specifier still lands, and the archive fails with `conflicting provisioning
settings`. Verified locally before #1226 was pushed.

Because the file is gitignored and the include is optional, it exists on CI
alone — a local Xcode build keeps Automatic signing and needs no distribution
profile.

After archiving, the workflow asserts the archive embeds the profile UUID it
installed. A silent signing fallback is the failure mode this whole file guards
against, so it should not be inferred from `xcodebuild` declining to complain.

**A local archive cannot stand in for CI here.** CI imports into a throwaway
keychain and grants codesign access with `security set-key-partition-list`; a
local run uses your login keychain, a different mechanism. Driving `codesign`
headlessly against the login keychain fails with `errSecInternalComponent` — a
GUI authorization prompt nothing can answer — which tells you nothing about CI
either way. Local archives are still worth running to catch *configuration*
errors (they caught both #1226 bugs, including one wrong first attempt), but let
CI prove the keychain path, and do not loosen your login keychain's ACLs chasing
it.

### Expiry

Apple Distribution certificates last one year and provisioning profiles expire
with them. There is no warning in CI before the fact — the first symptom is an
archive failure. When it happens, repeat steps 2–7 above; step 1 is only needed
if the account is again at the cap.

## Automated releases (`release-ios.sh` + `release-ios.yml`)

Mirrors the Android model. `scripts/release-ios.sh` computes the next `ios/N` tag
(N = build number), gates on a clean tree + a `validate-ios.sh` marker + an
`iosApp/CHANGELOG.md` entry for `MARKETING_VERSION`, and pushes the tag.
`.github/workflows/release-ios.yml` (macOS) reacts to `ios/[0-9]*`: it runs the
**launch smoke gate** (below), archives the Release app (overriding
`CURRENT_PROJECT_VERSION` to N so the tag owns the build number), then
`xcodebuild -exportArchive` with `destination=upload` ships it to
**TestFlight Internal**. Same flags + `--check` copy-paste workflow as
`release-android.sh`. Deliberately **not Xcode Cloud** — keeps the release pipeline
in GitHub Actions, consistent with Android/Firebase.

### Launch smoke gate (`scripts/ios-launch-smoke.sh`)

**Compiling is not launching.** Until 2026-07-24, no CI or release step ever
*launched* the app — `ios-ci.yml` and `release-ios.yml` only compiled/archived it,
so a build that compiles cleanly but crashes at launch (DI-graph init failure,
dyld missing-symbol, a throwing `AppDelegate` path, a Kotlin/Native
release-mode-only crash) shipped to TestFlight undetected. A TestFlight build in
the `ios/7` era did exactly that. The gate closes the class:

- **What it does:** fresh-installs the built app on a simulator, cold-launches
  it, asserts the process is still alive after `SMOKE_WAIT_SECONDS` (default
  10 s), screenshots, then terminates and warm-relaunches with the same
  assertion (fresh-install and relaunch can differ — the #1055 render bug was
  fresh-install-only). On failure it dumps any
  `~/Library/Logs/DiagnosticReports/GarageControl-*.ips` crash report produced
  during the run, so the CI log shows *why*, and exits non-zero. Trust the
  printed `IOS LAUNCH SMOKE: PASS` / `[FAIL]` markers.
- **Where it runs:** `release-ios.yml` builds a **Release**-configuration
  simulator app (same Kotlin/Native release-mode compile + Swift `-O` as the
  shipped artifact, with the injected server-config secret) and runs the smoke
  **before the archive step** — a launch-crashing build aborts the release
  before anything reaches App Store Connect. `ios-ci.yml` and
  `validate-ios.sh` run it against the Debug build on every iOS change
  (screenshots upload as the `launch-smoke-screenshots` artifact in CI).
- **Oldest-supported-OS leg (release lane only).** The `ios/7` launch crash
  (`HomeViewModelWrapper` `self!` trap, fixed in 0.1.1) reproduced **only on
  iOS 16.x** — newer SwiftUI defers the Task timing that deallocates a
  discarded `StateObject` wrapper, so the newest-runtime smoke stayed green
  while the app crashed on every launch on an iPhone X (a device capped at
  iOS 16 forever). `release-ios.yml` therefore downloads the deployment-target
  runtime (`xcodebuild -downloadPlatform iOS -buildVersion 16.4`, ~6 GB) and
  re-runs the Release smoke on an iOS 16.4 simulator. Blocking by design; not
  in per-PR CI (too slow). If the deployment target in `project.yml` ever
  rises, bump the `-buildVersion` here to match.
- **`self!` is banned in iosApp Swift** (`scripts/check-ios-self-force-unwrap.sh`,
  run by `validate-ios.sh` step 1 + an early `ios-ci.yml` step): the
  `[weak self]` + `self!` Task pattern is exactly the class the OS-version leg
  exists for — the grep kills it at PR time instead of release time. Safe
  replacement: `guard let stream = self?.<flow> else { return }` + `self?.`
  per iteration.
- **Residual gaps (known, accepted):** (a) **signed-in-only crashes** — CI has
  no Google credentials, so the smoke always exercises the signed-out launch
  path; (b) **device-only crashes** — the smoke runs a simulator slice, not the
  TestFlight-signed device archive; (c) **upgrade-state crashes** — the smoke
  fresh-installs, it does not migrate data written by an older build (the warm
  relaunch covers same-version persisted state only). If a TestFlight crash
  report ever lands in one of these classes, extend the gate rather than
  re-litigating it (e.g., a keychain-seeded fake-auth launch variant for (a),
  a data-fixture install for (c)). For retrieving TestFlight crash logs from
  the command line, dispatch `.github/workflows/ios-crash-feedback.yml`
  (`gh workflow run ios-crash-feedback.yml`) — it prints tester-shared crash
  submissions + logs from App Store Connect (that is how the ios/7 crash was
  root-caused).

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

Agent-facing shortcut: the `release-ios` skill (`.claude/skills/release-ios/SKILL.md`) mirrors this section as a copy-paste runbook, same as `release-android` and `release-firebase`.

1. Bump `MARKETING_VERSION` in `project.yml` (if the user-facing version changed) and
   add a matching `## X.Y.Z` heading to `MobileGarage/iosApp/CHANGELOG.md`.
2. `./scripts/validate-ios.sh` (writes the marker).
3. `./scripts/release-ios.sh --check` → copy-paste the printed `--confirm-tag ios/N`
   command (a git-based suggestion; CI verifies the actual build number).
4. The tag push triggers `release-ios.yml` → **pre-flight build-number check** →
   archive → upload → the build appears in TestFlight after processing. Monitor the
   Actions run; a failure opens a `release-failure/ios` issue (auto-closed on the next success).

**Status: verified end-to-end (`ios/1`, build 1 / 0.1.0, 2026-06-30).** The
`app-store-connect` method + cloud-signing-via-API-key + `destination: upload` path
lands the build in TestFlight Internal. The first-release gotcha was the API-key role
(App Manager → Admin, above). If a future Xcode ever rejects cloud signing, the fallback
is importing a Distribution `.p12` + profile from secrets (manual signing).

### Runner Xcode / iOS 26 SDK requirement

As of mid-2026 Apple **rejects any App Store Connect upload not built with the iOS 26
SDK (Xcode 26+)**. The archive builds fine, but `xcodebuild -exportArchive` fails at
validation with *"This app was built with the iOS 18.5 SDK. All iOS and iPadOS apps must
be built with the iOS 26 SDK or later."* GitHub's `macos-latest` still resolved to
**Xcode 16.4 / iOS 18.5 SDK** on 2026-07-01, so the `ios/3` upload was rejected — note
the **pre-flight + archive + cloud-sign all succeeded; only the upload failed**, so this
is a pure toolchain gate, not a signing or code problem.

**Fix (in the repo):** both `release-ios.yml` and `ios-ci.yml` pin **`runs-on: macos-26`**
(GA since 2026-02-26; default **Xcode 26.5**, which matches the maintainer's local Xcode
that archives cleanly). The archive step runs `xcodebuild -version` so the SDK is visible
in the run log. `ios/1` (2026-06-30) predated this enforcement, which is why it passed on
the old runner. If Apple bumps the required SDK again, bump the runner image or add an
explicit `maxim-lobanov/setup-xcode` step selecting the needed Xcode.

### Verifying a release-pipeline change

`validate-ios.sh` and iOS CI (`ios-ci.yml`) both do a **Debug build for the simulator
with signing disabled**. They do **not** exercise the release path — Release config,
device archive, code signing, export, and upload are all untested by them. So a change
that touches release-sensitive surface (scheme / target / product names, `release-ios.yml`,
`ExportOptions`, entitlements, archive/export flags, the runner's Xcode) can pass every
local check and PR gate and still fail at release time. Verify by tier, cheapest first:

1. **Local unsigned Release archive** — no secrets; catches almost all rename/config
   breakage (Release compiles for device, the scheme's *archive* action runs, the product
   is named correctly):
   ```bash
   xcodegen generate --spec MobileGarage/iosApp/project.yml --project MobileGarage/iosApp
   xcodebuild -project MobileGarage/iosApp/GarageControl.xcodeproj -scheme GarageControl \
     -configuration Release -destination 'generic/platform=iOS' \
     -archivePath /tmp/GarageControl.xcarchive archive CODE_SIGNING_ALLOWED=NO
   ```
   Then inspect `/tmp/GarageControl.xcarchive/Products/Applications/` (expect `GarageControl.app`)
   and its `Info.plist` `CFBundleIdentifier` (must still be `com.chriscartland.garage` — the
   product name can change, the bundle id must not). This does **not** test signing / export /
   upload — those need the App Store Connect key, which lives only in CI.

2. **A real tag (`ios/N`)** — the *only* thing that exercises cloud-signing + export +
   **upload**, and the only thing that hits **Apple's upload-time validation** (SDK version,
   entitlements, marketing icon, export compliance). Empirically these fail *only here*: the
   iOS-26-SDK gate above passed pre-flight + archive + cloud-sign and failed at upload. There
   is no local or PR-CI substitute — cutting the tag **is** the test. Read the run's outcome
   explicitly with `gh run view <id> --json status,conclusion` (the `gh run watch` exit code
   is unreliable), and on failure inspect the failed step's log tail for the real error.
