---
category: plan
status: active
last_verified: 2026-07-05
---

# App Store listing — draft copy + submission checklist (Phase G)

Draft assets for the iOS App Store submission. Everything here is
**staged, not submitted** — the actual App Store Connect data entry and
"Submit for review" are manual maintainer steps (mirrors the Play Store
two-location model: staged in repo → uploaded by hand).

## App identity (already locked)

- Name on the store: **Garage by Chris Cartland** (matches the App Store
  Connect record created for TestFlight)
- Bundle ID: `com.chriscartland.garage` · Universal (iPhone + iPad) ·
  iOS 16+
- `ITSAppUsesNonExemptEncryption = NO` (already set in the project)
- Category: **Lifestyle** (primary). Utilities also fits; Lifestyle is
  where smart-home companions live.

## Subtitle (30 chars max)

> Smart garage door companion

(28 chars.)

## Promotional text (170 chars max, editable without review)

> See your garage door's live status, get an alert when it's left open,
> and check the history of every open and close.

## Description (4000 chars max)

> Garage is the companion app for the Smart Garage Door open-source
> project — a DIY, ESP32-based garage door monitor and remote button.
>
> LIVE DOOR STATUS
> See at a glance whether the door is open, closed, or moving. Status
> updates arrive by push, and the app warns you when the door has been
> left open too long — then follows up with a single "resolved"
> notification once it closes.
>
> HISTORY
> Browse every open and close, grouped by day, going back through your
> full history.
>
> REMOTE BUTTON
> Authorized users can press the garage door button from anywhere. The
> two-tap flow (arm, then confirm) prevents accidental presses, and the
> server authorizes every press against a per-user allowlist.
>
> BUILT FOR RELIABILITY
> All business logic lives on the server; the app and the door hardware
> stay simple. The same shared logic powers the Android app, so both
> platforms behave identically.
>
> NOTE
> This app requires the companion Smart Garage Door hardware (an ESP32
> device wired to your garage door opener) and a configured server. It
> is a hobbyist / open-source project, not a commercial garage door
> product. Sign-in is with Google; door control is restricted to
> allowlisted accounts.

(~1200 chars — room to grow.)

## Keywords (100 chars max, comma-separated)

> garage,door,smart home,opener,monitor,ESP32,IoT,remote,notification

(69 chars.)

## URLs

- Support URL: https://github.com/cartland/SmartGarageDoor
- Marketing URL (optional): same
- Privacy policy URL: **required before submission** — reuse the policy
  linked from the Play Store listing (`AppLinks.PRIVACY_POLICY_URL` in
  `:domain` is the in-app source of truth; the store field needs the
  same URL).

## App Privacy (App Store Connect questionnaire)

Matches `PrivacyInfo.xcprivacy` (committed in the app target):

- Data collected: **User ID**, **Email address** — both *linked to the
  user*, *not used for tracking*, purpose *App functionality* (Google
  Sign-In identity checked against server-side allowlists).
- No tracking. No ads. No data sold/shared.

## Screenshots (staging: `MobileGarage/distribution/appstore/`)

App Store Connect requires at least one screenshot per size class.
**Staged now: the full 12-image set**, matching the Play Store
structure (Home / History / Settings × light/dark) — real app, live
production door data, signed-out state:

| Files | Device captured on | Pixel size |
|---|---|---|
| `iphone-6.9-0{1..6}-*.png` | iPhone 16 Pro Max simulator | 1320×2868 |
| `ipad-13-0{1..6}-*.png` | iPad Pro 13" (M4) simulator | 2064×2752 |

Capture procedure (repeat before submission so the shots match the
submitted build). Fully headless — tab taps via `idb`:

```bash
# one-time:
#   brew tap wix/brew && brew install applesimutils     (permission pre-grant)
#   brew tap facebook/fb && brew install idb-companion  (headless taps)
#   python3 -m venv ~/idbenv && ~/idbenv/bin/pip install fb-idb
xcrun simctl boot "iPhone 16 Pro Max"
xcodegen generate --spec MobileGarage/iosApp/project.yml --project MobileGarage/iosApp
xcodebuild -project MobileGarage/iosApp/GarageControl.xcodeproj -scheme GarageControl \
  -sdk iphonesimulator -destination "id=<booted-sim-udid>" build CODE_SIGNING_ALLOWED=NO
# ALWAYS uninstall + reinstall first (fresh Alt-Svc cache — see HTTP/3 gotcha):
xcrun simctl uninstall <udid> com.chriscartland.garage
xcrun simctl install <udid> <path-to>/GarageControl.app
applesimutils --byId <udid> --bundle com.chriscartland.garage --setPermissions notifications=YES
xcrun simctl launch <udid> com.chriscartland.garage   # wait ~15 s (fetch + door settle)
xcrun simctl io <udid> screenshot 01-home-light.png
idb ui tap --udid <udid> 220 913                      # History (iPhone 16 Pro Max points)
xcrun simctl io <udid> screenshot 02-history-light.png
idb ui tap --udid <udid> 366 913                      # Settings
xcrun simctl io <udid> screenshot 03-settings-light.png
xcrun simctl ui <udid> appearance dark                # then repeat the three
```

Tab-bar tap points: iPhone 16 Pro Max (bottom bar) Home 73,913 ·
History 220,913 · Settings 366,913. iPad Pro 13" (top pills) Home
417,47 · History 507,47 · Settings 598,47 — do NOT tap y≈25 on iPad
(that's the multitasking "…" menu).

Gotchas learned capturing this set:
- The notification-permission alert covers the first launch — pre-grant
  with `applesimutils` (plain `simctl privacy` has no `notifications`
  service).
- Wait for the door settle animation before capturing (the cold-open
  spring runs on first render). The pre-#1055 build also had a real
  fresh-install stuck-offset bug — fixed; if a capture shows a raised
  door labeled Closed, you are on a stale build.
- **HTTP/3 + VPN gotcha (why fresh-install-first is mandatory):** after
  the first successful fetch, cloudfunctions' `Alt-Svc` header upgrades
  the app's future requests to HTTP/3 (QUIC/UDP). With Tailscale (or a
  similar VPN) up on the host, the sim's QUIC silently dies —
  "network connection was lost" client-side while the server logs 200 —
  and the app shows the stale banner forever (the R1 gap). The Alt-Svc
  cache lives in the app container and survives relaunches AND sim
  reboots; uninstall+reinstall resets it so the first fetch rides
  HTTP/2 and succeeds. Capture promptly after launch.
- The check-in pill legitimately reads up to ~10 min (the device's real
  check-in cadence); only a red pill / "Not receiving updates" banner
  means the fetch failed.

## Review notes (App Review box)

> This app requires custom hardware (an open-source ESP32 garage door
> device) and an allowlisted Google account, so most features cannot be
> exercised by review. The app launches to a functional signed-out state
> showing live door status from our production server; sign-in is
> Google-only. No demo hardware can be provided; the project is
> open source: https://github.com/cartland/SmartGarageDoor

Apple sometimes asks for a demo video for hardware-dependent apps — a
short screen recording of the app + the physical door is the standard
answer if requested.

## Submission checklist (manual, in order)

1. Merge the `PrivacyInfo.xcprivacy` PR; cut the next `ios/N` build via
   `scripts/release-ios.sh` (the submitted binary must contain the
   manifest).
2. Upload screenshots (from `MobileGarage/distribution/appstore/`).
3. Paste subtitle / promo / description / keywords from this file.
4. Fill the App Privacy questionnaire (§ App Privacy above).
5. Set the privacy policy URL.
6. Pick the TestFlight build → Submit for review.
