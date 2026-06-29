---
category: reference
status: active
last_verified: 2026-06-29
---
# iOS Changelog

Permanent, per-release history of the iOS app (`AndroidGarage/iosApp/`). Mirrors
the Android `AndroidGarage/CHANGELOG.md` convention: newest first, one `## X.Y.Z`
heading per release matching `MARKETING_VERSION` in `iosApp/project.yml`, with a
non-empty body of user-facing changes.

When iOS release tooling lands (Phase F — `scripts/release-ios.sh` + the
archive/upload workflow, paired with Apple code-signing), a release gate will
require the `## X.Y.Z` heading for the version being shipped — same model as the
Android `release-android.sh` changelog gate. Until then this file is the running
log; keep it current as iOS changes merge so the first release has its history.

Versioning mirrors Android (see `AndroidGarage/CHANGELOG.md` § versioning):
major = rewrite or core-experience shift; minor = a user-facing feature added or
removed; patch = fixes, polish, refactors. iOS uses independent `ios/N` tags.

## Unreleased (0.1.0)

The app is functional on the simulator with real production backend data but has
not yet shipped to TestFlight or the App Store (Phase F/G — user-gated on Apple
code-signing). Built so far:

- Five-tab SwiftUI app (Home / History / Profile / Functions / Diagnostics)
  sharing all business logic with Android via the Kotlin `shared.framework`
  (`:iosFramework`), wired through SKIE and `SharedViewModel<VM>`.
- Real Firebase Auth + Google Sign-In, live door STATUS from the production
  server, and the FCM-receive path (data message → shared `FcmPayloadParser`).
- Door visualization (geometry, palette, animation spec, live trajectory) shared
  from `:domain`, plus the History pipeline, snooze, info sheets, and access
  tri-states — feature parity with Android per ADR-029/ADR-031/ADR-032.
- Browsable snapshot gallery of every SwiftUI `#Preview` (ADR-030).
- `scripts/validate-ios.sh` — local CI-exact build verification.
