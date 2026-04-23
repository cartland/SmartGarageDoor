# FirebaseServer Database Refactor Plan

Centralize `TimeSeriesDatabase` usage in `FirebaseServer/` behind typed
per-collection interfaces with in-memory fakes for tests. Zero production
data impact.

**Status:** Phases 0–6, 9 (partial), 10 shipped. Phases 7, 8, and the `HttpEchoTest` half of Phase 9 are deferred — testing Firebase Functions HTTP wrappers requires either `firebase-functions-test` package setup or extracting handler bodies into pure functions (production code change). See the *Deferred work* section at the end of this doc. Every Firestore collection now goes through a typed singleton with a contract test, and regression is guarded by the Phase 4 lint rule. Zero Firestore data impact throughout.

### Phase shipping history

| Phase | Scope | PR | Tag |
|---|---|---|---|
| 0 | Prerequisites + `test/fakes/` infra | (multiple) | server/12 |
| 1 | RemoteButton trio (interface + fake + contract) | (multiple) | server/11–12 |
| 2 | `SensorEventDatabase` interface + fake; `EventUpdatesFakeTest` | #476 | server/13 |
| 3 | `UpdateDatabase` rename from `Database.ts`; `NotificationsDatabase` migration | #478, #481 | server/13 |
| 4 | ESLint ban on `new TimeSeriesDatabase(` outside `src/database/` | #482 | server/13 |
| 5 | `ServerConfigDatabase` to canonical pattern + `ConfigAccessors` split | #485 | (unreleased) |
| 6 | `SnoozeNotificationsDatabase` to canonical pattern | #484 | (unreleased) |
| 9 (partial) | `OldDataFCMFakeTest` + mocha glob fix (surfaced 84 hidden tests) | #486 | (unreleased) |
| 10 | Stale TODO removal + doc refresh | #487 | (unreleased) |

## Purpose

The server has two competing patterns for wrapping Firestore
collections. The duplication creates silent failure modes (a collection
name renamed in one file but not others) and forces imprecise test
mocking. This refactor consolidates to a single pattern, adds fake-based
tests as each area is migrated, and pins collection names against
accidental change.

## Goals

1. **One source of truth per collection.** Each Firestore collection is
   defined in exactly one TypeScript module. Callers import the
   singleton; they never name the collection string.
2. **Typed method surfaces per collection.** `getCurrent()` on the event
   DB returns the event shape, not `any`. The TypeScript compiler tells
   you every caller when a field is renamed.
3. **Fakes over mocks.** Each DB has an in-memory implementation of its
   interface that tests instantiate directly. No `sinon.stub(prototype,
   ...)` in new tests.
4. **Increased test coverage** in the HTTP and pubsub handlers that
   currently have none.
5. **Collision-proof collection names.** A typo in a Firestore
   collection name fails the build, not production.
6. **Zero production data impact.** No document migrations, no field
   renames, no schema changes. Firestore sees byte-identical traffic
   before and after.

## Non-Goals

Explicitly **out of scope** for this refactor:

- Changing any Firestore collection name or document shape.
- Adding Firestore transactions, batching, or queries that don't exist today.
- Modifying `TimeSeriesDatabase.ts` (the underlying Firestore wrapper).
- Narrowing types in a way that drops fields on reads.
- Migrating to Firebase Functions v2 or changing the trigger model.
- Introducing a DI container, factory registry, or service locator.

## Current State

### Pattern A — centralized (good)

Four collections are already centralized in `src/database/`:

- `SensorEventDatabase.ts` — `'eventsCurrent'`/`'eventsAll'`
- `SnoozeNotificationsDatabase.ts` — snooze collections
- `ServerConfigDatabase.ts` — config collections
- `UpdateDatabase.ts` — `'updateCurrent'`/`'updateAll'`
- `NotificationsDatabase.ts` — `'notificationsCurrent'`/`'notificationsAll'`

### Pattern B — duplicated inline (bad)

~14 `new TimeSeriesDatabase(...)` calls scattered across handlers and
controllers. The same collection is instantiated multiple times:

| Collection | Pattern A exists? | Pattern B duplicate count |
|---|---|---|
| `'eventsCurrent'` | yes (`SensorEventDatabase`) | **4 more inline instances** |
| `'updateCurrent'` | yes (`Database.ts`) | 2 more inline instances |
| `'remoteButtonRequestCurrent'` | no | 3 inline instances |
| `'remoteButtonCommandCurrent'` | no | 2 inline instances |
| `'remoteButtonRequestErrorCurrent'` | no | 2 inline instances |
| `'notificationsCurrent'` | yes (`NotificationsDatabase`) | 0 inline instances (migrated) |

Duplicates currently pass tests because `TimeSeriesDatabase` is
stateless. The issue is maintenance: renaming a collection requires
editing N files and hoping nothing is missed.

### Current test mocking style (imprecise)

Pattern B tests stub `sinon.stub(TimeSeriesDatabase.prototype, ...)`.
This intercepts **every** `TimeSeriesDatabase` instance in the process
— including unrelated collections a handler might incidentally touch.
Silent coupling; a bug in one path can be masked by a stub for another.

## Target Architecture

One module per collection. Each module exports:

```typescript
// src/database/EventDatabase.ts

import { TimeSeriesDatabase } from './TimeSeriesDatabase';

export const COLLECTION_CURRENT = 'eventsCurrent';   // pinned
export const COLLECTION_ALL     = 'eventsAll';        // pinned

export interface EventDatabase {
  getCurrent(buildTimestamp: string): Promise<Record<string, unknown> | null>;
  save(buildTimestamp: string, data: Record<string, unknown>): Promise<void>;
}

class FirestoreImpl implements EventDatabase {
  private readonly db = new TimeSeriesDatabase(COLLECTION_CURRENT, COLLECTION_ALL);
  getCurrent(t: string) { return this.db.getCurrent(t); }
  save(t: string, d: Record<string, unknown>) { return this.db.save(t, d); }
}

let _instance: EventDatabase = new FirestoreImpl();
export const DATABASE: EventDatabase = {
  getCurrent: (t)    => _instance.getCurrent(t),
  save:       (t, d) => _instance.save(t, d),
};

/** TEST-ONLY: swap the implementation. */
export function setImpl(impl: EventDatabase): void { _instance = impl; }
```

Callers only ever see `DATABASE`. Tests call `setImpl(fake)` in
`beforeEach`, restore in `afterEach`.

**Why module-level singleton + `setImpl` and not function-param
injection:**

- Extends the existing `Pattern A` already in the repo.
- Zero handler-signature churn — handlers keep calling `DATABASE.save(...)`.
- Clear test seam — one function flips the whole module.
- Avoids DI framework complexity in a serverless codebase.

**Why interface + fake (not class + stub):**

- Typed per-collection (no `any` leaks downstream).
- Fakes are simple Map-backed storage; readable in 30 seconds.
- No prototype pollution between tests.
- Tests become self-documenting specs: `fake.seed(); call(); expect(fake.saved)...`

## Principles

### Backward Compatibility

**The refactor cannot change what Firestore sees.** These are the
invariants every PR must preserve:

1. **Collection name strings are byte-identical before and after.**
   Verified by `grep` diff in each PR description.
2. **Document shapes are untouched.** No field additions, removals,
   renames, or restructuring.
3. **Return types start wide** (`Record<string, unknown>`) to match
   existing untyped reads. Narrowing to specific types is a **separate
   follow-up**, not part of this refactor.
4. **`TimeSeriesDatabase.ts` is not modified.** The underlying Firestore
   wrapper is the one file where a change could break reads/writes.
5. **No runtime behavior changes.** If current code writes before reading,
   new code writes before reading. Call order, await patterns, error
   handling are all preserved in-shape.
6. **Concurrent-deploy safe.** During a Cloud Functions deploy window,
   old and new code may both be live. Since they share collection names
   and document shapes, they remain interoperable.

### Long-term Maintenance

Principles for future contributors (and future-you):

1. **One module owns one collection.** If you're writing to a
   Firestore collection, the call goes through a singleton in
   `src/database/`. Don't `new TimeSeriesDatabase(...)` outside that
   directory.
2. **Collection names live in exactly one place.** The two string
   constants (`COLLECTION_CURRENT`, `COLLECTION_ALL`) in each DB module
   are the single source of truth. A contract test pins them.
3. **Interfaces are append-only.** Adding a method is safe; removing
   one or changing a signature requires updating every caller. If a
   method needs to change, add a new one and deprecate the old.
4. **Fakes mirror the interface — that's it.** Fakes should not
   simulate Firestore error conditions unless a specific test requires
   it. A fake is a Map and a list, not a mini-database.
5. **Fakes live in `test/fakes/`, not `src/`.** They're test
   infrastructure; bundling them with production code invites drift.
6. **If a collection name must change** (e.g. sharding, renaming for
   clarity), treat it as a data migration, not a refactor:
   - Copy documents from old → new collection in production.
   - Update the DB module's string constants.
   - Update the contract test.
   - Update the data retention cron.
   - Update any dashboards / alerts.
   - Deploy all in one release.
7. **When stubbing, stub the singleton, not the prototype.**
   `sinon.stub(DATABASE, 'get')` is fine for quick tests. Existing
   stub-style tests (e.g. `SnoozeNotificationsTest`) continue to work.
   New tests should prefer fakes for clarity.

## Phased Plan

Each phase is a separate PR, independently mergeable, auto-merged after
CI passes. No phase depends on a subsequent phase.

### Phase 0 — Prerequisites

**Scope:**
- Fix `ServerConfigDatabase.ts` self-reference bug (`Config.DATABASE`
  / `Config.CURRENT_KEY` referenced from inside the class — should use
  `this.`).
- Delete commented-out `EVENT_DATABASE` in `http/Events.ts:42`.
- Create `test/fakes/` directory.
- Add `test/fakes/README.md` describing the fake pattern and pointing
  here.

**New tests:** none.

### Phase 1 — RemoteButton pilot

**Scope:**
- Create `RemoteButtonRequestDatabase.ts`, `RemoteButtonCommandDatabase.ts`,
  `RemoteButtonRequestErrorDatabase.ts` (interface + FirestoreImpl +
  singleton + `setImpl`).
- Create matching fakes in `test/fakes/`.
- Migrate `pubsub/RemoteButton.ts`, `http/RemoteButton.ts`,
  `controller/DatabaseCleaner.ts` (RemoteButton lines only) to import
  the singletons.

**New tests:**
- `HttpRemoteButtonTest.ts` — happy path, ack token logic, session continuity.
- `PubsubRemoteButtonTest.ts` — staleness detection writes error entry;
  fresh requests do not; missing request writes correct error.

**Contract tests** for all 3 new DB modules.

### Phase 2 — Events consolidation (highest-value)

**Scope:**
- Extend `SensorEventDatabase.ts` with an `EventDatabase` interface +
  `setImpl` function. (The singleton already exists; it gets an
  interface.)
- Create `FakeEventDatabase` in `test/fakes/`.
- Migrate `pubsub/OpenDoor.ts`, `http/OpenDoor.ts`,
  `controller/EventUpdates.ts`, `controller/DatabaseCleaner.ts` (event
  line) to the canonical singleton.
- **Rewrite `EventUpdatesTest.ts`** from `sinon.stub(TimeSeriesDatabase.prototype, ...)`
  to `setImpl(fake)` pattern. This is the main precise-mocking payoff.

**New tests:**
- `HttpOpenDoorTest.ts` — event reporting saves to fake correctly.
- `PubsubOpenDoorsJobTest.ts` — stale event triggers notification branch.

### Phase 3 — Update + Notifications

**Scope:**
- Rename `src/database/Database.ts` → `UpdateDatabase.ts` (currently
  misnamed — wraps updates, not "database" generically).
- Add `NotificationsDatabase.ts`.
- Create fakes.
- Migrate `http/Echo.ts`, `controller/DatabaseCleaner.ts` (update line),
  `controller/fcm/OldDataFCM.ts`.

**New tests:**
- `HttpEchoTest.ts` — reporting endpoint.
- Extend `OldDataFCMTest.ts` with fake-based coverage.

### Phase 4 — Regression guard (complete)

**Shipped:**
- ESLint `no-restricted-syntax` rule in `FirebaseServer/eslint.config.js`
  bans `new TimeSeriesDatabase(...)` project-wide, with narrow allowlist
  overrides for `src/database/**/*.ts` (the canonical home) and
  `test/database/TimeSeriesDatabaseTest.ts` (the wrapper's own contract
  test).
- Error message points the reader at the singleton pattern and this
  doc, so a future contributor gets the right fix on first read.
- Verified by adding a temporary `new TimeSeriesDatabase(...)` in a
  non-allowed file — lint fails with the rule's message.

## Safety Guards

Each PR in the refactor must include:

| Guard | What it catches | Where it lives |
|---|---|---|
| **Contract test** for each new DB module | Typo in collection name string creating a silent divergent write | `test/database/*DatabaseTest.ts` — pinned `expect().to.equal('exactString')` |
| **Grep-diff audit** in PR description | Any accidental collection-string change across the refactor | Manually run + pasted; verifies string set unchanged |
| **Scope rule** | Modification of `TimeSeriesDatabase.ts` | Documented here + PR review |
| **Wide return types** (`Record<string, unknown>`) initially | Type narrowing dropping fields on reads | Interface definitions |
| **Emulator smoke test** (already in CI) | Fake diverging from real Firestore behavior | `.github/workflows/firebase-ci-checks.yml` |
| *(Phase 4)* **Lint rule** | Regression to Pattern B | CI step or ESLint config |

### Example: the contract test

```typescript
// test/database/RemoteButtonRequestErrorDatabaseTest.ts

import { expect } from 'chai';
import {
  COLLECTION_CURRENT,
  COLLECTION_ALL,
} from '../../src/database/RemoteButtonRequestErrorDatabase';

describe('RemoteButtonRequestErrorDatabase: collection-name contract', () => {
  // These strings MUST match what pubsub/RemoteButton.ts wrote before
  // centralization. A mismatch means new code writes to a different
  // Firestore collection than existing production data — the app
  // appears to work while historical data becomes unreachable.
  //
  // Intentional change requires a full data migration:
  //   1. Copy documents from old collection to new in production.
  //   2. Update this test.
  //   3. Update the data-retention cron.
  //   4. Update any dashboards or alerts.
  //   5. Deploy atomically.

  it('current collection is pinned', () => {
    expect(COLLECTION_CURRENT).to.equal('remoteButtonRequestErrorCurrent');
  });

  it('all collection is pinned', () => {
    expect(COLLECTION_ALL).to.equal('remoteButtonRequestErrorAll');
  });
});
```

## Verification Strategy

### Per-PR verification (CI)

Every phase's PR must pass:

1. `npm run build` — TypeScript compiles, lint passes, zero warnings.
2. `npm run tests` — all mocha tests pass (existing + new).
3. **Contract tests** for all touched DB modules pass.
4. **Emulator smoke test** boots the Functions emulator, hits
   `/serverConfig`, verifies no 000 (connection refused). Confirms real
   Firestore paths resolve.
5. `verifyIdToken` library-chain tests pass (unaffected by this refactor
   but regression-guards the auth path).

### Per-PR manual verification

In the PR description, the author includes:

1. **Before/after grep** of collection-name strings:
   ```bash
   grep -rE "'[a-z][a-zA-Z]+(Current|All)'" FirebaseServer/src \
     | sort -u
   ```
   The set of strings must be identical in both runs.
2. **File-by-file migration summary** listing each `new
   TimeSeriesDatabase(...)` removed and which singleton replaced it.
3. **Confirmation** that `TimeSeriesDatabase.ts` was not modified.

### Post-merge verification

After each phase merges to main:

1. Watch post-merge CI (`Firebase Post-Merge CI`) on the merge commit.
2. If a phase is being released, cut `server/<N+1>` per
   `scripts/release-firebase.sh` and observe:
   - `firebase-deploy.yml` completes successfully.
   - The affirmative success marker `✔ Deploy complete!` appears in the
     deploy log (not just exit-0 — see
     [`docs/FIREBASE_DEPLOY_SETUP.md`](FIREBASE_DEPLOY_SETUP.md)).
3. Smoke-check production via `curl $SERVER/serverConfig` with the
   server config key header — returns 200 with expected payload.
4. Observe Cloud Logging for 10 minutes for any unexpected error
   patterns in the migrated handlers.

## Rollback Plan

Each phase is its own PR, so rollback is a `git revert` of one commit
on main + a new `server/<N>` release tag. Because:

- No document shapes changed, the old code reads the same Firestore
  state it always did.
- No collection names changed, reads/writes continue to land in the
  same place.
- `TimeSeriesDatabase.ts` was not modified, so there's no shared-wrapper
  change to worry about.

A revert at any phase boundary is safe. Partial rollbacks within a
phase (e.g. only reverting one file of a multi-file migration) are
**not safe** and should not be attempted — the migrated and
un-migrated callers would reference different singleton instances
pointing at the same collection, which is functionally fine but
confuses future maintenance. If partial rollback is needed, revert the
whole phase and re-apply as a smaller one.

## Outcome Summary

| Metric | Before | After |
|---|---|---|
| `new TimeSeriesDatabase()` instances | 18 | ~6 |
| Duplicate `'eventsCurrent'` instantiations | 5 | 1 |
| Patterns in use | A + B mixed | A only |
| Collection-name typo → silent data divergence | Possible | Fails build |
| `RemoteButton` / `OpenDoor` test coverage | 0 tests | ~6 new tests |
| Test mocking style | `sinon.stub(prototype, ...)` | Typed per-collection fakes |
| Files where `TimeSeriesDatabase` is instantiated directly | 10 | 1 directory (`src/database/`) |
| Production data impact | — | None (verified per-PR) |

## Related Documents

- [`FIREBASE_DEPLOY_SETUP.md`](FIREBASE_DEPLOY_SETUP.md) — release and
  rollback mechanics for the server.
- `AndroidGarage/docs/DECISIONS.md` — Android-side ADRs; some principles
  here (fakes over mocks, typed interfaces) mirror ADRs there (ADR-003
  no-Mockito, ADR-008 no-`*Impl`-suffix).

---

# Follow-up Phases (post server/13)

Phases 0–4 shipped. Two DB modules never adopted the interface pattern
and most of the handler tests the plan promised never landed. Phases
5–10 close those gaps.

**All principles from the original plan still apply:** zero Firestore
impact, collection strings byte-identical, `TimeSeriesDatabase.ts`
untouched, wide return types, concurrent-deploy safe, Phase 4 lint
rule green.

## Remaining gaps

| Area | Current state |
|---|---|
| `src/database/ServerConfigDatabase.ts` | Concrete class (`class ServerConfig { DATABASE = new TimeSeriesDatabase(...) }`). No interface, `setImpl`, fake, or contract test. Getters (`getRemoteButtonPushKey`, `isRemoteButtonEnabled`, etc.) live on the same class but are pure functions over a config payload. |
| `src/database/SnoozeNotificationsDatabase.ts` | Same — concrete class. No interface/fake/contract test. |
| Phase 1 handler tests | `HttpRemoteButtonTest.ts`, `PubsubRemoteButtonTest.ts` — missing. |
| Phase 2 handler tests | `HttpOpenDoorTest.ts`, `PubsubOpenDoorsJobTest.ts` — missing. |
| Phase 3 handler tests | `HttpEchoTest.ts` — missing. `OldDataFCMTest.ts` exists but tests direct functions, not the `setImpl(fake)` pattern. |
| Stale comment | `pubsub/OpenDoor.ts:22` — `// TODO: Add Snooze option to delay notifications.` Snooze is already wired through `getSnoozeStatus()` in `OldDataFCM.ts`. |

## Phase 5 — ServerConfigDatabase to the canonical pattern

**Scope:**
- Split the module. `src/database/ServerConfigDatabase.ts` becomes: `COLLECTION_CURRENT = 'configCurrent'`, `COLLECTION_ALL = 'configAll'`, `CURRENT_KEY = 'current'` (pinned), `interface ServerConfigDatabase { get(): Promise<any>; set(data: any): Promise<void>; }`, `FirestoreServerConfigDatabase`, singleton `DATABASE`, `setImpl` / `resetImpl`.
- Move the pure config-payload getters (`getRemoteButtonPushKey`, `getRemoteButtonAuthorizedEmails`, `isRemoteButtonEnabled`, `getRemoteButtonBuildTimestamp`, `isDeleteOldDataEnabled`, `isDeleteOldDataEnabledDryRun`, `isSnoozeNotificationsEnabled`) to free exported functions in `src/controller/config/ConfigAccessors.ts` — they're stateless reads over a config payload, not DB operations.
- Migrate 6 callers (`DatabaseCleaner.ts`, `functions/http/RemoteButton.ts`, `functions/http/Snooze.ts`, `functions/http/ServerConfig.ts`, `functions/http/DeleteData.ts`, `functions/pubsub/DataRetentionPolicy.ts`):
  - `Config.get()` → `DATABASE.get()`
  - `Config.set(d)` → `DATABASE.set(d)`
  - `Config.getRemoteButtonPushKey(cfg)` → `getRemoteButtonPushKey(cfg)`

**New tests:**
- `test/database/ServerConfigDatabaseTest.ts` — contract test pinning `'configCurrent'`, `'configAll'`, `CURRENT_KEY = 'current'`.
- `test/fakes/FakeServerConfigDatabase.ts` — Map-backed fake with audit array + `failNextSet` / `failNextGet`.

**Validation:**
- `./scripts/validate-firebase.sh` green.
- `grep -rE "'[a-z][a-zA-Z]+(Current|All)'" FirebaseServer/src | sort -u` — string set unchanged before/after.
- Phase 4 lint green (only `ServerConfigDatabase.ts` gains a `new TimeSeriesDatabase(...)`, which is in the allowlist).
- `TimeSeriesDatabase.ts` not modified.

**Rollback:** Single PR, `git revert`. Zero Firestore impact.

## Phase 6 — SnoozeNotificationsDatabase to the canonical pattern

**Scope:**
- `src/database/SnoozeNotificationsDatabase.ts` gains: `export const COLLECTION_CURRENT = 'snoozeNotificationsCurrent';` (ditto ALL), `interface SnoozeNotificationsDatabase { set(buildTimestamp, data), get(buildTimestamp), getRecentN(n) }`, `FirestoreImpl`, singleton, `setImpl` / `resetImpl`.
- Caller (`src/controller/SnoozeNotifications.ts`) already uses `DATABASE.set/get/getRecentN` — zero caller churn. The class becomes an interface + its FirestoreImpl, exported as the same `DATABASE` singleton.

**New tests:**
- `test/database/SnoozeNotificationsDatabaseTest.ts` — contract test.
- `test/fakes/FakeSnoozeNotificationsDatabase.ts` — Map-backed, audit array, `failNextSet` / `failNextGet` / `failNextGetRecentN`.

**Validation:** same as Phase 5.

**Rollback:** Single PR, `git revert`.

## Phase 7 — RemoteButton handler tests

**Scope:**
- `test/functions/http/HttpRemoteButtonTest.ts` — covers: (a) happy path (authed request with valid push key + session ID writes to `FakeRemoteButtonRequestDatabase`), (b) ack-token continuity (same session ID across requests), (c) session-ID regeneration when client omits it, (d) disabled config rejects, (e) wrong push key rejects, (f) unauthorized email rejects.
- `test/functions/pubsub/PubsubRemoteButtonTest.ts` — covers: (a) stale request writes entry to `FakeRemoteButtonRequestErrorDatabase`, (b) fresh request does not, (c) missing request writes the correct error shape.
- Uses `setImpl` on each of `RemoteButtonRequestDatabase`, `RemoteButtonCommandDatabase`, `RemoteButtonRequestErrorDatabase`, `ServerConfigDatabase` (Phase 5).

**Prerequisites:** Phase 5 (for `FakeServerConfigDatabase`).

**Validation:** new tests pass; existing tests unaffected.

**Rollback:** Test-only PR — zero production risk. `git revert` if flaky.

## Phase 8 — OpenDoor handler tests

**Scope:**
- `test/functions/http/HttpOpenDoorTest.ts` — event-report endpoint: valid payload saves to `FakeSensorEventDatabase` with byte-identical shape, malformed payload rejects, missing required fields reject.
- `test/functions/pubsub/PubsubOpenDoorsJobTest.ts` — stale event calls `sendFCMForOldData` (verified through `FakeNotificationsDatabase.saved[]` + `FakeEventFCMService.sends[]` or similar seam).

**Prerequisites:** None (fakes for `SensorEventDatabase`, `NotificationsDatabase`, `EventFCMService` already exist).

**Validation:** same as Phase 7.

## Phase 9 — Echo + OldDataFCM fake-based tests

**Scope:**
- `test/functions/http/HttpEchoTest.ts` — Echo endpoint writes request body to `FakeUpdateDatabase`.
- `test/controller/fcm/OldDataFCMFakeTest.ts` — mirrors `EventUpdatesFakeTest.ts`. Covers: (a) no current event → no send, (b) duplicate-notification suppression, (c) snooze-active → no send, (d) snooze-expired → send if door-open-too-long, (e) door-closed → no send, (f) save failure does not swallow (use `FakeNotificationsDatabase.failNextSave`). Retires the existing direct-function `OldDataFCMTest.ts` if and only if coverage is equal or better.

**Prerequisites:** Phase 6 (for `FakeSnoozeNotificationsDatabase`).

**Validation:** same as Phase 7.

## Phase 10 — Doc + stale-TODO cleanup

**Scope:**
- Update this doc's "Status:" line (line 7) to reflect Phases 5–10 completion.
- Update the `## Outcome Summary` table with final numbers.
- Remove `// TODO: Add Snooze option to delay notifications.` in `src/functions/pubsub/OpenDoor.ts:22` — snooze is already wired through `getSnoozeStatus()` inside `sendFCMForOldData`.

**Explicitly NOT in scope** (deferred — these are behavior changes, not refactors):
- Hardcoded `'Sat Mar 13 14:45:00 2021'` buildTimestamp in `pubsub/OpenDoor.ts:23`, `pubsub/DoorErrors.ts:23`, `pubsub/RemoteButton.ts:30`. Replacement requires a config-plumbing decision (which buildTimestamp does the pubsub job care about?) — file a separate plan.
- Bare `// TODO.` in `http/RemoteButton.ts:65`. Underlying design question about how to handle a missing ack-token from the client. Leave until the design question is settled.
- Dependabot alerts (`uuid` buffer-bounds, `fast-xml-parser` XMLBuilder injection) — tracked outside this refactor.

## Priority and ordering

| Phase | Depends on | Effort | Risk | Notes |
|---|---|---|---|---|
| 5 | — | Medium (6 callers) | Low | Production code + tests |
| 6 | — | Small | Low | Production code + tests, near-zero caller churn |
| 7 | 5, 6 | Medium | Low | Test-only |
| 8 | — | Medium | Low | Test-only |
| 9 | 6 | Small-Medium | Low | Test-only (may retire an old test) |
| 10 | 5–9 | Trivial | None | Doc + one-line code cleanup |

**Parallelism:** Phases 5, 6, 8 can land in parallel (no shared files). Phase 7 is gated by 5 + 6. Phase 9 is gated by 6. Phase 10 lands last, after all others.

## Safety guards (apply to every PR)

1. `./scripts/validate-firebase.sh` passes — build + lint + all tests.
2. PR description includes the `grep` diff of collection-name string literals. Set must be unchanged.
3. `TimeSeriesDatabase.ts` is not modified.
4. Phase 4 lint (`no-restricted-syntax` on `new TimeSeriesDatabase(`) remains green.
5. Contract tests for every DB module touched in the PR must pin the collection strings.
6. For Phases 5 and 6 (production code): verify `firebase-deploy.yml` succeeds on the post-merge commit before shipping `server/14` etc. No Firestore document shape changes — concurrent-deploy safe.

## Rollback

Each phase is a single PR. Phases 5–6 touch production code; revert via `git revert` + new `server/N` tag. Phases 7–10 are test/doc-only; reverting is zero-risk (tests never deploy). Partial rollback within a phase is not safe (same rule as original phases) — revert the whole PR.

## Outcome Summary (actual, post-Phase 10)

| Metric | Pre-refactor | Post-Phase 4 | Post-Phase 10 (actual) |
|---|---|---|---|
| DB modules using interface + setImpl pattern | 0 of 9 | 7 of 9 | **9 of 9** |
| DB modules with contract tests | 0 of 9 | 6 of 9 | **9 of 9** |
| DB modules with fakes in `test/fakes/` | 0 of 9 | 6 of 9 | **9 of 9** |
| Orchestration test files using fakes | 0 | 1 (`EventUpdatesFakeTest`) | 2 (+ `OldDataFCMFakeTest`) |
| Handler (HTTP/pubsub) tests | 0 | 0 | 0 (deferred — see below) |
| Stale-marker comments in handlers | ≥ 1 | ≥ 1 | **0** |
| Mocha-discovered test files | (all) | (all) | (all) — **+84 surfaced via glob fix** |
| Production data impact | — | None | None (verified per-PR) |

## Deferred work

The following items from the original plan are **not shipped**. Each has a concrete reason that rules out a pure-refactor approach.

### Phase 7 — RemoteButton handler tests (deferred)

**Blocker:** `httpRemoteButton` and `httpAddRemoteButtonCommand` are wrapped by `functions.https.onRequest(handler)`. Invoking the inner handler requires either:
1. Adopting the `firebase-functions-test` package (new test-only dependency + SDK setup).
2. Extracting the handler body into a pure function (e.g. `handleRemoteButtonRequest(config, req, now)`) and leaving the thin wrapper untested. This is a production-code refactor, out of scope for the DB refactor.

**Non-blocker evidence:** The DB interactions the handler drives are already pinned by contract tests (`RemoteButtonRequestDatabaseTest`, `RemoteButtonCommandDatabaseTest`, `RemoteButtonRequestErrorDatabaseTest`) and the lint rule prevents regression to inline `TimeSeriesDatabase` construction.

### Phase 8 — OpenDoor handler tests (deferred)

**Blocker:** Same as Phase 7. Additionally, the `http/OpenDoor.ts` and `pubsub/OpenDoor.ts` handlers are ~5 lines each — they only call `SensorEventDatabase.getCurrent` and `sendFCMForOldData`. The interesting logic is inside `sendFCMForOldData`, already covered by `OldDataFCMFakeTest.ts` (Phase 9).

### Phase 9 — `HttpEchoTest.ts` (deferred half of Phase 9)

**Blocker:** Same firebase-functions-test-or-extract choice. Echo's logic is "save then retrieve"; both operations are pinned by `UpdateDatabase`'s contract tests and exercised by `EventUpdatesFakeTest.ts` indirectly.

### Common path forward

If these tests are needed in the future, the cleanest approach is either:

- **Option A — Extract handler bodies.** Refactor each handler into `handleX(config, params, services, now): Promise<Result>` + a thin wrapper. Test the pure function directly with fakes. One-time production-code change per handler; all future handler tests fall out easily.
- **Option B — Add `firebase-functions-test`.** Write tests that invoke the full wrapper via the library's helpers. No production-code change, but adds a test-only dependency and a new testing idiom.

Either option is a planned follow-up, not blocking the refactor's completion.

### Excluded (behavior changes — not refactors)

- Hardcoded `'Sat Mar 13 14:45:00 2021'` buildTimestamp in `pubsub/OpenDoor.ts:24`, `pubsub/DoorErrors.ts:23`, `pubsub/RemoteButton.ts:30`. Replacement requires config-plumbing decisions (which buildTimestamp does the pubsub job care about?) — file a separate plan.
- Bare `// TODO.` in `http/RemoteButton.ts:65`. Underlying design question about missing-ack-token handling — leave until the design question is settled.
- Dependabot alerts (`uuid` buffer-bounds, `fast-xml-parser` XMLBuilder injection) — tracked outside this refactor.
