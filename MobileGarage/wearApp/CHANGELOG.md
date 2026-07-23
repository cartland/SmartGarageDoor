---
category: reference
status: active
last_verified: 2026-07-22
---
# Wear OS App Changelog

Internal release history for the Wear OS app. Releases are cut with
`./scripts/release-wear.sh` as `wear/N` tags (versionCode = 1000000 + N).
The phone app's history lives in [`../CHANGELOG.md`](../CHANGELOG.md).

## Versioning

Same rule as the phone app: major = rewrite or core-experience shift;
minor = added or removed user-facing feature; patch = fixes, polish, refactors.

## 0.1.5

- The hold-to-confirm ring is now centered on the physical screen and hugs
  the bezel, instead of circling the door image off-center. The screen now
  stays on for up to 15 seconds while a press is in flight or the door is
  moving, so you can watch the action complete without the display timing
  out; it never stays on just because the button is armed. Pressing now
  allows a little more time before showing "Door did not move," since the
  watch's network path is less reliable than the phone's.
- Before the first door status arrives, the screen now shows "Connecting…"
  with no warning badge, instead of a gray door labeled "Unknown" with a
  warning triangle — calmer during the first few seconds after opening the
  app.

## 0.1.4

- Armed button stays armed while you keep touching the screen: every touch
  (down and up, anywhere on the screen) restarts the disarm timer, so
  partial taps and aborted holds no longer let the button quietly disarm.
  It now disarms only after ~8 seconds with no touches. Also fixes a
  mid-hold disarm edge where a hold started late in the armed window could
  visually complete but never fire. Operating the door still requires the
  full continuous 2-second hold, and a quick tap still never triggers it.

## 0.1.3

- Sign in with your phone: while the watch is signed out, the app now uses
  the paired phone's signed-in account over Bluetooth or Wi-Fi (requires
  phone app 2.21.0). Play services rejects watch-local Google sign-in on
  Wear OS ("Google Identity Services do not support this Android
  Credential Manager API on Wear OS", captured on a Pixel Watch 4), so the
  phone relay is the working path; the Sign in button remains for watches
  where it works.

## 0.1.2

- Sign-in failures now show a transient "Sign-in failed" message under the
  button instead of silently doing nothing (the 0.1.1 button appeared
  unresponsive when Credential Manager failed, e.g. watches whose Play
  services lack the Identity Sign-In module).
- Slightly smaller door in the signed-out layout so the sign-in button and
  failure message fit the round screen.

## 0.1.1

- No app changes. First release through the fully automated CI to Play
  pipeline (wear/N tag to Wear internal track), now including Play release
  notes from `distribution/wear-whatsnew/`.

## 0.1.0

- Initial Wear OS release: animated garage door status with tap-to-arm and a
  2-second hold-to-confirm remote button.
- Standalone watch app: Sign in with Google via Credential Manager,
  foreground-only status refresh, shared door animation spec with phone/iOS.
