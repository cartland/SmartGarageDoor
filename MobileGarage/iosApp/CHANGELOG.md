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
