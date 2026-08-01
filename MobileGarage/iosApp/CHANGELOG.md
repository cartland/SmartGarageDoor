---
category: reference
status: active
last_verified: 2026-06-29
---
# iOS Changelog

Permanent, per-release history of the iOS app (`MobileGarage/iosApp/`). Mirrors
the Android `MobileGarage/CHANGELOG.md` convention: newest first, one `## X.Y.Z`
heading per release matching `MARKETING_VERSION` in `iosApp/project.yml`, with a
non-empty body of user-facing changes.

The `scripts/release-ios.sh` gate requires a `## X.Y.Z` heading (matching
`MARKETING_VERSION`) with a non-empty body before it will cut the `ios/N` tag —
same model as the Android `release-android.sh` changelog gate. Keep this current
as iOS changes merge.

Versioning mirrors Android (see `MobileGarage/CHANGELOG.md` § versioning):
major = rewrite or core-experience shift; minor = a user-facing feature added or
removed; patch = fixes, polish, refactors. iOS uses independent `ios/N` tags.

## 0.1.4

- **Settings now uses iOS's own word for the app's identifier.** The About
  section called it "Package", which is Android's term. iOS calls the same
  thing a Bundle ID, and that is what Xcode, App Store Connect and the Settings
  app all call it. The value was always right; only the label was borrowed. The
  other rows (Version, Build, Built) already read correctly and are unchanged.

## 0.1.3

- **The app can now be translated.** Every screen's text was built as plain
  Swift strings, which the compiler cannot collect — so there was nothing for a
  translator to translate, and no warning that this was the case. All five tabs
  now carry text in a form that reaches the app's string catalog: 175 entries,
  covering everything you can read. English is unchanged; adding a language is
  now a matter of supplying translations rather than changing code.
- **Two things that should never be translated no longer can be.** Your own name
  and email address on the account row, and the notification topic identifier in
  the developer panel, were being routed through the same machinery as ordinary
  copy. Version numbers and diagnostic counters are now formatted as numbers
  rather than looked up as text.
- Several decisions about what to display moved into code shared with the
  Android app — door-state headlines, the snooze row, history durations and row
  layout — so the two apps cannot quietly disagree. Nothing on screen changes.

## 0.1.2

- **The app stops forgetting what it knew.** On every launch while signed in, it
  briefly believed you were signed out — long enough for the cleanup that runs
  on sign-out to delete the cached remote-button status, snooze state, and
  feature access it had just restored. The visible cost was a few seconds of
  missing pills and late-appearing rows on each cold start. It now knows about
  your session from the first moment.
- **It no longer asks signed-in people to sign in.** Home and Settings treated
  "we have not heard from Firebase yet" as "signed out", so the sign-in row
  flashed at the start of every launch. Both now say "Checking sign-in…" until
  the answer is actually known.
- **A first launch no longer looks like a fault.** With nothing cached yet, the
  door read "Unknown" with a warning badge — blaming the door for the app having
  no data. It now says "Connecting…" with no badge.
- **Pull to refresh waits for the refresh.** The spinner used to disappear the
  instant you let go, while the request was still in flight, so the gesture
  looked like it did nothing. On Home, History, and Settings it now stays until
  the data actually arrives.
- **The "not receiving updates" warning stays put.** In History it used to
  scroll away with the list, taking its Retry button with it.
- Leaving History, Functions, or Diagnostics now releases their work instead of
  leaving it running for the rest of the session. History's "took longer than
  usual" tags are amber rather than alarm red. Times follow your device's
  12- or 24-hour setting. VoiceOver announces the status pills as buttons and
  reads the check-in age. Exported diagnostics files are timestamped instead of
  overwriting each other. On iPad, content no longer stretches the full width of
  the screen. The account sheet scrolls, so "Sign out" is reachable at large
  text sizes.

## 0.1.1

- Fix a launch crash on iOS 16 (crashed every launch on iPhone X / iOS 16.3.1,
  build 7): `HomeViewModelWrapper.init` registered observation Tasks with
  `[weak self]` + `self!`, and a wrapper instance discarded by
  `StateObject(wrappedValue:)` re-evaluation deallocated before its Tasks ran,
  trapping on the force-unwrap. All 8 sites now use the safe
  `guard let stream = self?...` pattern (same as `SettingsViewModelWrapper`).
  Root-caused from the TestFlight crash-feedback log (Incident
  3AE511E1-9499-4E51-A1CB-0A3D99F26919).

## 0.1.0

First TestFlight (Internal) release — a native SwiftUI iOS app that shares all
business logic with Android via the Kotlin `shared.framework` (`:iosFramework`),
wired through SKIE and `SharedViewModel<VM>`.

- Five tabs: Home / History / Profile / Functions / Diagnostics.
- Real Firebase Auth + Google Sign-In, live door STATUS from the production
  server, and the FCM-receive path (data message → shared `FcmPayloadParser`).
- Door visualization (geometry, palette, animation, live trajectory) shared from
  `:domain`, plus the History pipeline, snooze, info sheets, and access
  tri-states — feature parity with Android per ADR-029/ADR-031/ADR-032.
