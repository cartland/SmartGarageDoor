# Firebase Server Changelog

Internal release history for Cloud Functions deployments. The Android app and ESP32 firmware do not read a version from the server; this log is for the maintainer and for rollback decisions.

## Versioning

Server releases use a monotonic integer (`server/N`). No semver — each release is a deployment snapshot, not a library version. The tag itself is the identity.

## Release-line convention

Every release that reaches `main` gets an entry here. `scripts/release-firebase.sh` blocks the tag push if `## server/N` has no body. Emergency override: `--confirm-no-changelog <target-sha>`.

**Supersede pattern.** Releases that supersede an untested predecessor (bug-chase chain — a release ships broken, an attempted fix is still broken, the next attempt finally works) may **replace** the predecessor's entry with a single entry on the final release. Git log of this file preserves the original content, so archaeology is possible without cluttering the human-readable history.

Example (placeholder tag numbers — the gate looks for exact `server/<real number>` headings and the release script additionally ignores matches inside fenced code blocks):

```markdown
## server/<final>
- Fixed snooze TTL bug. (First attempts in server/<final-2> and server/<final-1> had off-by-one errors that only surfaced in production.)

## server/<prior>
- Bump Firebase Functions runtime to Node 22.
```

---

## server/16
- First release to actually read `buildTimestamp` values from server config — the pubsub door-sensor and remote-button jobs have been using hardcoded literals since June 2021 even though the config contained the values. After this release, changing a device ID is a config update via `httpServerConfigUpdate` rather than a code deploy.
- Zero runtime behavior change on deploy: production config has `body.buildTimestamp = "Sat Mar 13 14:45:00 2021"` (plain) and `body.remoteButtonBuildTimestamp = "Sat%20Apr%2010%2023:57:32%202021"` (URL-encoded since April 2021). The new `getRemoteButtonBuildTimestamp` accessor applies `decodeURIComponent()` so callers see the pre-refactor plain form. Both resolved strings are byte-identical to the hardcoded literals.
- Named file-local `_FALLBACK` constants in each call site preserve the old behavior if the config field ever goes missing or empty — and a warn-level Cloud Log entry is now emitted when the fallback fires, tagged with the call-site context (`httpCheckForOpenDoors`, `pubsubCheckForOpenDoorsJob`, `pubsubCheckForDoorErrors`, `pubsubCheckForRemoteButtonErrors`). `getRemoteButtonBuildTimestamp` also logs if URL-decoding throws on malformed input (returns the raw value; never crashes the job).
- `ConfigAccessorsTest.ts` (13 tests + 3 log-behavior tests) pins the production config shape byte-for-byte.
- Replaces the reverted attempt in server/15 (PR #492 read the wrong config key for the door sensor and returned the URL-encoded value verbatim for the remote button; caught before release and reverted in PR #494).

## server/15
- Release with no behavior changes. Dependency hygiene — closes Dependabot alerts #66 (`fast-xml-parser` XMLBuilder injection) and #67 (`uuid` < 14.0.0 buffer bounds).
- `uuid` direct dep bumped 8.3.2 → 14.0.0 (PR #491). Our only use is `uuidv4()` with no `buf` argument — not in the CVE vector at any version — but the bump clears the alert and the new `UuidTest.ts` pins the v4 output shape for future bumps.
- `fast-xml-parser` transitive override pinned to ≥ 5.7.0 via `package.json` overrides (PR #493). No direct usage in our code; pulled by `firebase-admin → @google-cloud/storage`. Emulator smoke test green with the override.
- `CveGuardTest.ts` adds a unit-test pattern for dependency CVE guards — one `it()` per advisory, fails if a flagged version is re-introduced. Mirrors the existing `jws` guard in `VerifyIdTokenTest.ts`.
- Also included: the revert of PR #492 (the initial A1 `buildTimestamp`-from-config attempt read the wrong config key for the door sensor and returned the URL-encoded value verbatim for the remote button; net zero runtime behavior change since the bug was caught before release). A corrected A1 will ship separately.

## server/14
- Release with no behavior changes. Refactor + test follow-up closing out the database refactor plan.
- `ServerConfigDatabase` and `SnoozeNotificationsDatabase` converted to the canonical interface + FirestoreImpl + swappable singleton pattern (PRs #485, #484). All 9 DB modules now follow the same shape; all 9 have collection-string contract tests and in-memory fakes. `ConfigAccessors.ts` extracted the pure config-payload getters (`getRemoteButtonPushKey`, `isSnoozeNotificationsEnabled`, etc.) out of the DB module.
- `OldDataFCMFakeTest.ts` adds fake-based orchestration tests for `sendFCMForOldData` (11 tests covering short-circuits, snooze integration, duplicate suppression, and save/FCM failure modes).
- **Test discovery fix:** the mocha glob in `FirebaseServer/package.json` was unquoted, so `sh` expanded it one level deep and silently skipped every test under `test/controller/fcm/*.ts`. Quoting the glob surfaced 84 existing tests that hadn't been running in CI (PR #486). All surfaced tests pass.
- Cleanup: 3 duplicate top-level test files removed (older copies of canonical versions deeper in the tree); stale `// TODO: Add Snooze option` comment removed from `pubsub/OpenDoor.ts`; bare `// TODO.` in `httpRemoteButton`'s no-ack-token branch replaced with comments documenting the actual existing behavior.

## server/13
- Release with no behavior changes. `EventFCM` refactored to the same interface + service + `setImpl` pattern used by the database modules. The `DefaultEventFCMService` produces byte-identical `firebase.messaging().send(...)` calls as the previous bare function.
- Also shipped: CI job naming standardization (gate jobs renamed with `Android` / `Firebase` prefixes; branch protection swapped via `gh api`). Internal tooling only.
- Test architecture: fakes gained single-shot `failNextX(error)` helpers; old sinon-based `EventUpdatesTest.ts` retired in favor of stricter `EventUpdatesFakeTest.ts` with 5 new failure-mode tests.

## server/12
- Release with no behavior changes. Internal refactor only — no Firestore collection or document-shape change.
- Database refactor Phase 3: centralized the `updateCurrent` / `updateAll` collection onto `UpdateDatabase` (interface + in-memory fake + contract-pinned collection names). Two inline `new TimeSeriesDatabase('updateCurrent', ...)` call sites removed (`DatabaseCleaner`, `Echo`). Dead `Database.ts` / `DatabaseTest.ts` removed.
- Added parallel `EventUpdatesFakeTest.ts` running alongside the existing sinon-based `EventUpdatesTest.ts` (test-only change, both run).

## server/11
- Release with no behavior changes. Internal refactor only — no Firestore collection or document-shape change.
- Database refactor Phase 2: centralized the `eventsCurrent` / `eventsAll` callers onto `SensorEventDatabase` (interface + in-memory fake + contract-pinned collection names). Four inline `new TimeSeriesDatabase('eventsCurrent', ...)` call sites removed.
- First release to exercise the new CHANGELOG gate in `release-firebase.sh` and the exact-match `Firebase Checks / Unit Tests` verifier in `firebase-deploy.yml`.
- CI job naming standardized: `Android Checks` / `Firebase Checks` caller prefixes, plus `Run Unit Tests` renamed to `Unit Tests` on the Firebase side.

## server/10
- Bump Firebase Functions runtime to Node 22 (production). Local dev + CI also moved to Node 22.

## server/9
- Release with no behavior changes. GitHub Actions bumped (checkout v5, setup-node v5, google-github-actions/auth v3) to support Node 24 on CI runners.

## server/8
- First Firebase release cut with the new `scripts/release-firebase.sh` script (parity with Android's `release-android.sh`).
- Added `scripts/validate-firebase.sh` local pre-push check and validation marker.
- Internal: began database-centralization refactor (Phase 0 + Phase 1a — RemoteButton databases). No external API change.

## server/7
- Release with no behavior changes. Cleaned up 16 ESLint unused-var warnings.

## server/6
- Dropped unused `googleapis` direct dependency.
- Added `qs` and `path-to-regexp` npm overrides to patch Express transitive CVEs.
- Dependabot hygiene: `fast-xml-parser` and `protobufjs` updates.
- Pinned local Node version via `FirebaseServer/.nvmrc` (Node 20).

## server/5
- Release with no behavior changes. The long-running silent-deploy-failure was diagnosed and fixed around this release by re-provisioning the deployer service account with the correct `roles/iam.serviceAccountUser` on the runtime SA. Fix lives outside the repo; this tag is the first clean deploy after it. Runbook: `docs/FIREBASE_DEPLOY_SETUP.md`.

## server/4
- Overrode transitive `jws` to 4.0.1 + 3.2.3, closing Dependabot alerts #25 and #26.
- Added `verifyIdToken` library-chain regression guard test so future `jws` / `jsonwebtoken` / `google-auth-library` bumps can't silently break auth.
- Added Firebase emulator smoke test to CI.
- Added `npm audit` warn step to Firebase CI.

## server/3
- Pinned all Firebase server dependencies to exact versions (no `^`/`~` ranges) to eliminate surprise transitive drift.
- Fixed TypeScript build errors that had accumulated on `main`.

## server/2
- Release with no behavior changes. Path-based CI skipping + gate jobs (repo-level plumbing).

## server/1
- Initial tagged release. Migrated Firebase server from tslint to ESLint.
- Added `TimeSeriesDatabase` unit test coverage and general test scaffolding.
- Repository restructure: Firebase server moved into its own directory.
