---
category: reference
status: active
last_verified: 2026-04-24
---
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

## server/23
- **Dependency-security release.** Bumps `firebase-admin` 13.5.0 → 13.8.0 (PR #583) and adds a top-level `uuid: "^14.0.0"` override (PR #585) so every transitive uuid in firebase-admin's `@google-cloud/storage` / `google-gax` / `gaxios` / `teeny-request` subtree resolves to 14.0.0. Together these close all 5 open Dependabot alerts (3× node-forge high, 1× uuid medium, 1× js-yaml medium — js-yaml fixed via devDep override, PR #584).
- Zero functional change. firebase-admin 13.6/13.7/13.8 release notes ship additive features only — no breaking API surface for the call patterns this server uses (`auth().verifyIdToken`, Firestore reads/writes, FCM sends). uuid v14's `v4()` API is API-compatible with the v8/v9/v11 transitive callers; the CVE was specifically about the optional `buf` parameter, not used by any google-cloud-storage internal call site.
- Test count: 250 → 250 (no new tests; the dep bumps are runtime/transitive only and existing handler/contract tests cover the surfaces).
- Operational: this is the production proof for the dependency-upgrade plan's Bucket C entries. Watch the deploy log for `✔ Deploy complete!` (silent-failure signature from `docs/FIREBASE_DEPLOY_SETUP.md`) and confirm at least one FCM door-event reaches a device within 24h.

## server/22
- New authenticated endpoint `httpFunctionListAccess` (`GET /functionListAccess`, `X-AuthTokenGoogle` → `{enabled: boolean}`). Verifies the Firebase ID token, extracts the email, checks membership against `body.featureFunctionListAllowedEmails: string[]` on `configCurrent/current`. UI hint only — `pushButton` and `snoozeNotifications` retain their independent allowlist checks (still the security boundary). PR #573.
- Per-feature email allowlist field `featureFunctionListAllowedEmails` added to the `configCurrent/current` Firestore doc, edited directly in the Firebase console (no redeploy). Missing-field default is deny-all. New accessor `getFunctionListAuthorizedEmails(config)` in `controller/config/ConfigAccessors.ts`.
- Renamed `Auth.ts:isAuthorizedToPushRemoteButton` → `isEmailInAllowlist(email, list: string[] | null)` and made it null-tolerant. Reused from all three auth-gated handlers (`pushButton`, `snoozeNotifications`, `functionListAccess`). Closes a latent crash-on-null-allowlist that the existing handlers had inherited from before the accessor pattern; behavior change is "deny instead of throw" on a missing field. New unit tests pin the null + empty cases.
- Wire-contract fixtures introduced at `wire-contracts/functionListAccess/` (`response_enabled_true.json`, `response_enabled_false.json`). Both server tests (Mocha deep-equal) and Android Ktor `MockEngine` tests load the same files — a unilateral rename on either side fails the test on at least one side. See `wire-contracts/README.md`.
- Test count: 240 → 250 (new `HttpFunctionListAccessTest.ts` covers all 9 paths + the wire-contract deep-equal; `AuthTest.ts` extended for null/empty allowlist).
- Operational: nothing in production changes until an email is added to the new allowlist field. Default state is "endpoint live, returns `{enabled:false}` for everyone." Deploy and Firestore edit are independent.

## server/21
- Documentation cleanup release. The only runtime change is in `controller/config/ConfigAccessors.ts`: the `requireBuildTimestamp` throw message now points at `docs/archive/FIREBASE_HARDENING_PLAN.md` (the doc moved during the 2026-04-24 archive reorganization, PR #533). The throw never fires in current production — config has both buildTimestamp fields populated since `server/16` — so the path-update only matters if config is ever broken in the future.
- Five other source files (`Echo.ts`, `OpenDoor.ts`, `RemoteButton.ts` HTTP + pubsub, `Snooze.ts`, `HandlerResult.ts`) had identical archive-path updates inside JSDoc/inline comments only — no runtime effect on those.
- Zero runtime behavior change versus `server/20` for any path that currently executes. The 239-test suite passes; `ConfigAccessorsTest.ts` pins the new error message via the unchanged `/FIREBASE_HARDENING_PLAN\.md.*A3/` regex (matches the new `docs/archive/...` path because the regex isn't anchored).
- Context: same 6-PR documentation maintenance plan that produced `docs/AGENTS.md`, the YAML front-matter validator, and the markdown link checker. See PRs #531-#536 for the doc-side scope.

## server/20
- Re-deploy of `server/18` after the `server/19` rollback-investigation window. Same commit tree as `server/18` — see that entry below for the full handler-testing-plan scope. Re-deploys H1-H6 extractions (14 HTTP/pubsub handlers now pure-core-tested) plus the AuthService bridge introduced in PR #514.
- Why the rollback happened: an Android user reported the Home tab stuck on "Loading" after `server/18` deployed. Root-caused as an Android-side regression unrelated to the server changes — see Android 2.4.4 CHANGELOG. A 4-agent parallel review confirmed no server regression; two agents independently identified `DoorViewModel.fetchCurrentDoorEvent`'s missing `Complete(result.data)` setter on the Success branch (PR #518). FCM pushes bypassed the bug because a state change produces a distinct StateFlow value.
- Zero runtime behavior change from `server/19`. Behavior matches what `server/18` delivered on deploy 2026-04-24T14:27 UTC.

## server/19
- **Rollback of server/18** to re-run investigation when an Android user reported a Home tab regression. Zero code change — same commit tree as `server/17`.
- Root cause later identified as Android-side (see `server/20` entry). The rollback was a cautious-first-then-investigate move, not a response to a confirmed server-side bug.
- No new runtime behavior. Firestore data untouched. Full deploy success (`✔ Deploy complete!`) at 2026-04-24T15:27 UTC.
- Retroactive entry — the release itself used `--confirm-no-changelog` because rollback speed was prioritized during the investigation window.

## server/18
- Executed `FIREBASE_HANDLER_TESTING_PLAN.md` from H1 through H6 — all 14 HTTP/pubsub handlers now have a pure `handle<Action>(input)` core + thin wrapper, unit-tested against the existing fakes. Closes the deferred handler-test work from the database refactor plan (Phase 1/2/3). Net test count +72 (167 → 239); zero new test dependencies; existing fakes sufficient.
- Zero runtime behavior change on deploy. Extraction is code-shape only — every status code, error body string, Firestore call pattern, and session/UUID generation path is byte-identical. Three quirks (behavior reviewer flagged) are deliberately preserved and pinned by tests: (a) `httpAddRemoteButtonCommand`'s missing-buildTimestamp branch still writes `save(undefined, data)` because the pre-extraction `response.status(400).send(...)` had no `return`, (b) `httpAddRemoteButtonCommand`'s `verifyIdToken` throw propagates to 500 while `httpSnoozeNotificationsRequest` catches and returns 401 — asymmetry retained, (c) `httpRemoteButton`'s asymmetric "return fresh-read on clear, return pre-save on else" split retained.
- New shared infrastructure: `src/functions/HandlerResult.ts` (`ok(...)` / `err(status, body)` for multi-status-code handlers) and `src/controller/AuthService.ts` (`EventFCMService`-shaped bridge wrapping `firebase.auth().verifyIdToken`). The AuthService shipped first in its own prep PR (#514) with a contract test and `FakeAuthService` before any handler started using it. `test/helpers/AuthTestHelper.ts#setupAuthHappyPath` seeds config + fake-auth in one call for the three auth-heavy handlers.
- Extracted handlers, by PR: H1 Echo (#504), H2 door-sensor trio http/pubsub OpenDoor + pubsub DoorErrors (#505), H3 pubsub RemoteButton errors (#506), H4 snooze-latest read + `HandlerResult<T>` introduction (#507), H5 DataRetentionPolicy (#508), H5 DeleteData (#509), H5 ServerConfig read+update (#510), H5 Events trio currentEventData/eventHistory/nextEvent (#511), H6 plan-status doc refresh (#512), H3 HTTP remoteButton poll + ack-token state-machine tests (#515), AuthService bridge prep (#514), and H3/H4 auth-heavy handlers addRemoteButtonCommand + snoozeNotificationsRequest (#516).
- Also shipped: "Inject externals via wrapper" pattern documented in `FIREBASE_HANDLER_TESTING_PLAN.md` — when a handler reads `functions.config()`, `Date.now()`, or similar framework globals, the wrapper reads them and passes plain values to the pure core (see `readServerConfigSecret` in H5, `nowMillis?` arg in `handleDataRetentionPolicy`). Service calls (Firestore, FCM, Auth) continue to use the `setImpl`-swappable bridge pattern.

## server/17
- A3 of the hardening plan: `_FALLBACK` constants removed from the 4 buildTimestamp call sites; production config is now authoritative. `resolveBuildTimestamp` (silent fallback + warn) replaced by `requireBuildTimestamp` (throws on null + error log). Cleared by `server/16`'s 24h observation window where the fallback logs stayed empty across every pubsub cycle, confirming the fallback was masking no active bugs.
- Zero runtime behavior change on deploy — production config has `body.buildTimestamp` and `body.remoteButtonBuildTimestamp` populated (verified). `requireBuildTimestamp` never throws in the current state.
- Failure mode after A3: if the config field is deleted or emptied, the affected pubsub/HTTP function errors loudly (ERROR-level Cloud Log) instead of silently continuing with a stale hardcode. Trade-off is explicit and documented.
- Docs: three-layer history of the removal — inline comment blocks at each modified file pointing at the plan doc; new "A3 — Fallback removal (history + revert)" section in `docs/FIREBASE_HARDENING_PLAN.md` with before/after code, verification checklist, and exact revert commands; the thrown error message itself includes the doc-section pointer so operators land on the right page.
- Also shipped: `FIREBASE_HANDLER_TESTING_PLAN.md` (new doc — six-phase plan for handler-body extraction to close out the deferred handler tests from the database refactor's Phase 1/2/3) and a status refresh of `FIREBASE_HARDENING_PLAN.md` mapping each phase to its shipped PR.

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
