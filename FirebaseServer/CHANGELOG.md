# Firebase Server Changelog

Internal release history for Cloud Functions deployments. The Android app and ESP32 firmware do not read a version from the server; this log is for the maintainer and for rollback decisions.

## Versioning

Server releases use a monotonic integer (`server/N`). No semver — each release is a deployment snapshot, not a library version. The tag itself is the identity.

## Release-line convention

Every release that reaches `main` gets an entry here. `scripts/release-firebase.sh` blocks the tag push if `## server/N` has no body. Emergency override: `--confirm-no-changelog <target-sha>`.

**Supersede pattern.** Releases that supersede an untested predecessor (bug-chase chain — server/11 shipped, broken, server/12 attempted fix, still broken, server/13 finally worked) may **replace** the predecessor's entry with a single entry on the final release. Git log of this file preserves the original content, so archaeology is possible without cluttering the human-readable history.

Example:

```markdown
## server/13
- Fixed snooze TTL bug. (First attempts in server/11 and server/12 had off-by-one errors that only surfaced in production.)

## server/10
- Bump Firebase Functions runtime to Node 22.
```

---

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
