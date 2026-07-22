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

## 0.1.0

- Initial Wear OS release: animated garage door status with tap-to-arm and a
  2-second hold-to-confirm remote button.
- Standalone watch app: Sign in with Google via Credential Manager,
  foreground-only status refresh, shared door animation spec with phone/iOS.
