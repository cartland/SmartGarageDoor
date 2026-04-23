# FirebaseServer Database Refactor Plan

Centralize `TimeSeriesDatabase` usage in `FirebaseServer/` behind typed
per-collection interfaces with in-memory fakes for tests. Zero production
data impact.

**Status:** Plan — not yet executed. Phase 0 starts when approved.

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
