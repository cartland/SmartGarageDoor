---
category: archive
status: shipped
---
# Firebase Handler Testing Plan

Complete the handler-test coverage that the database refactor plan
promised but deferred. Applies the "extract handler body into a pure
function" pattern (Option B in `FIREBASE_DATABASE_REFACTOR.md` →
*Deferred work*) so the handlers can be tested with the fakes already
built during Phases 1–6.

**Status:** COMPLETE. All phases shipped — every HTTP and pubsub handler has a pure `handle<Action>(input)` core with unit tests against fakes.

| Phase | Scope | Status |
|---|---|---|
| H1 | `httpEcho` (pilot) | ✅ shipped (#504) |
| H2 | door-sensor trio: `httpCheckForOpenDoors`, `pubsubCheckForOpenDoorsJob`, `pubsubCheckForDoorErrors` | ✅ shipped (#505) |
| H3 — pubsub | `pubsubCheckForRemoteButtonErrors` | ✅ shipped (#506) |
| H3 — HTTP | `httpRemoteButton`, `httpAddRemoteButtonCommand` | ✅ shipped (#515, #516) |
| H4 — read | `httpSnoozeNotificationsLatest` + introduction of `HandlerResult<T>` | ✅ shipped (#507) |
| H4 — write | `httpSnoozeNotificationsRequest` | ✅ shipped (#516) |
| H5 — retention | `pubsubDataRetentionPolicy` | ✅ shipped (#508) |
| H5 — delete-data | `httpDeleteOldData` | ✅ shipped (#509) |
| H5 — server-config | `httpServerConfig`, `httpServerConfigUpdate`, `readServerConfigSecret` helper | ✅ shipped (#510) |
| H5 — events | `httpCurrentEventData`, `httpEventHistory`, `httpNextEvent` | ✅ shipped (#511) |
| H6 | Doc cleanup | ✅ shipped (#512) |

Prerequisite: `AuthService` bridge + fake + helper shipped in #514 so H3/H4 could wrap `verifyIdToken` in tests.

Handler coverage: 14 / 14. The pattern below stays authoritative for new handlers.

## Purpose

Every HTTP and pubsub handler today is a `functions.https.onRequest` /
`functions.pubsub.schedule` wrapper around logic that either lives
inline or calls into `controller/*`. The inline logic can't be unit
tested because the wrapper object isn't directly callable in mocha.

The database refactor's Phase 1/2/3 promised handler tests
(`HttpRemoteButtonTest.ts`, `PubsubOpenDoorsJobTest.ts`, etc.) but
deferred them because writing those tests requires either (a) adopting
`firebase-functions-test` as a new dependency or (b) refactoring each
handler to separate pure logic from HTTP wrapper.

This plan executes option (b) — the cleaner long-term answer that also
aligns with the existing `controller/` / `functions/` split already in
the codebase.

## Why Option B (extract) over Option A (firebase-functions-test)

From `FIREBASE_DATABASE_REFACTOR.md` → *Deferred work* discussion:

- Option B **extends an existing pattern**. The project already splits
  `controller/` (pure business logic, tested with fakes) from
  `functions/` (thin Firebase wrappers). Extracting inline handler
  logic just completes that split consistently.
- Tests **reuse the existing fake infrastructure** — no new
  testing idiom, no new dependency versioning story.
- Every **future handler** falls out of the pattern automatically —
  new contributors see one way to write handlers, not two.
- Risk is **mechanical per handler** (small, local diffs), not
  architectural.

Option A (`firebase-functions-test`) would test the wrapper plumbing
(which Firebase already tests on their side) and add a dependency with
its own versioning constraints. We don't need that coverage.

## Target pattern

### Before — inline logic inside the wrapper

```typescript
// src/functions/http/Echo.ts (current)

export const httpEcho = functions.https.onRequest(async (request, response) => {
  const data = { queryParams: request.query, body: request.body };
  if (SESSION_PARAM_KEY in request.query) { /* ... */ }
  // ... 20 lines of logic ...
  try {
    await UpdateDatabase.save(session, data);
    const retrievedData = await UpdateDatabase.getCurrent(session);
    response.status(200).send(retrievedData);
  } catch (error) {
    response.status(500).send(error);
  }
});
```

### After — pure function + thin wrapper

```typescript
// src/functions/http/Echo.ts (target)

/** Pure core — testable with plain object args. */
export async function handleEchoRequest(input: {
  query: Record<string, unknown>;
  body: unknown;
}): Promise<unknown> {
  // ... 20 lines of logic, unchanged ...
  await UpdateDatabase.save(session, data);
  return UpdateDatabase.getCurrent(session);
}

/** Thin HTTP wrapper — untested boilerplate. */
export const httpEcho = functions.https.onRequest(async (request, response) => {
  try {
    const result = await handleEchoRequest({ query: request.query, body: request.body });
    response.status(200).send(result);
  } catch (error) {
    console.error('httpEcho failed', error);
    response.status(500).send(error);
  }
});
```

### For handlers with multiple status codes (auth, validation)

```typescript
export type HandlerResult<T> =
  | { kind: 'ok'; data: T }
  | { kind: 'error'; status: number; body: { error: string } };

export async function handleRemoteButtonPoll(input: {
  query: Record<string, unknown>;
}): Promise<HandlerResult<RemoteButtonCommand>> {
  const config = await ServerConfigDatabase.get();
  if (!isRemoteButtonEnabled(config)) {
    return { kind: 'error', status: 400, body: { error: 'Disabled' } };
  }
  // ... 40 more lines ...
  return { kind: 'ok', data: updatedCommand };
}

export const httpRemoteButton = functions.https.onRequest(async (request, response) => {
  try {
    const result = await handleRemoteButtonPoll({ query: request.query });
    if (result.kind === 'error') {
      response.status(result.status).send(result.body);
    } else {
      response.status(200).send(result.data);
    }
  } catch (error) {
    console.error('httpRemoteButton failed', error);
    response.status(500).send(error);
  }
});
```

The `HandlerResult<T>` type lives in one shared file
(`src/functions/HandlerResult.ts`), used by every extracted handler.

## Principles

- **One function per handler.** Name: `handle<Action>` (e.g.
  `handleEchoRequest`, `handleRemoteButtonPoll`). Exported from the
  same file as the wrapper.
- **The pure function takes plain object args** (not `Request` /
  `Response`). Input is `{query, body}` or similar — no Firebase
  wrapper types.
- **The pure function returns typed results** (`Promise<T>` or
  `Promise<HandlerResult<T>>`) — never takes the `Response` object.
- **The wrapper is trivial** — destructure request, call the pure
  function, map result to `response.status().send()`. No business
  logic in the wrapper.
- **Tests go in `test/functions/http/*Test.ts` mirroring the src
  layout.** Use existing fakes via `setImpl`.
- **Firestore data contract unchanged.** Extraction is a code-shape
  change only. All collection strings, document shapes, and Firestore
  call patterns stay byte-identical.

### Inject external-state reads via the wrapper

When a handler reads from a framework global — `functions.config()`,
`Date.now()`, `firebase.firestore.Timestamp.now()`, `process.env`, etc.
— have the **wrapper** read it and pass the plain value to the pure
core as an argument. The pure function then takes a regular string /
number / object and tests drive it without stubbing the global.

Two concrete examples already shipped:

- **`functions.config()` → `expectedKey: string | null`** — the
  wrapper in `http/ServerConfig.ts` calls
  `readServerConfigSecret(functions.config(), 'key' | 'updatekey')`
  (a small exported helper) and passes the resulting string or
  `null` to `handleServerConfigRead({..., expectedKey})`. Tests pass
  `expectedKey: null` directly to reach the 500 branch and a real
  string to reach the 401/403/200 branches.

- **`Date.now()` → `nowMillis: number = Date.now()`** — the pure
  `handleDataRetentionPolicy(nowMillis?)` takes an optional
  millisecond override that defaults to `Date.now()`. Tests pin the
  value; the wrapper relies on the default. Same idea applies to
  `firebase.firestore.Timestamp.now()` when that's the seam (here the
  existing approach is a sinon stub because the threshold logic is
  tested against a frozen "now" — either pattern is acceptable).

Rule of thumb: if the global is a **value read** (config, clock), pass
it as an argument. If it's a **service call** (Firestore, FCM), use
the `setImpl`/fake pattern instead — those are already designed for
that shape.

## Phased rollout

Each phase = one handler group = one PR. Handlers can land in any
order; phases are independent.

### Phase H1 — `httpEcho` (pilot, simplest)

**Scope:**
- Extract `handleEchoRequest({query, body})` from `httpEcho` wrapper.
- Write `test/functions/http/HttpEchoTest.ts` — happy path saves to
  `FakeUpdateDatabase` with correct shape; uses `setImpl` on
  `UpdateDatabase`.

**Why first:** Echo has no auth, no config, no branching. Tiniest
possible surface to establish the extraction pattern and the
`test/functions/http/*` directory convention.

**Tests (minimum 3):**
- Saves to `UpdateDatabase` with session from query if provided
- Saves with auto-generated session UUID if query omits it
- Returns the saved data from `UpdateDatabase.getCurrent`

### Phase H2 — Door-sensor handlers

**Files:**
- `http/OpenDoor.ts` → `handleCheckForOpenDoorsRequest`
- `pubsub/OpenDoor.ts` → `handleCheckForOpenDoorsJob`
- `pubsub/DoorErrors.ts` → `handleCheckForDoorErrors`

**Why grouped:** all three read `buildTimestamp` from config
(already refactored in server/16), then call existing controller
functions. Thin wrappers; the extracted pure functions are ~10 lines
each.

**Tests:**
- `PubsubOpenDoorsJobTest.ts` — config returns value → stale-event
  path runs `sendFCMForOldData`. Config returns null → fallback fires,
  warn logged, same path runs.
- `PubsubCheckForDoorErrorsTest.ts` — constructs `data` payload,
  calls `updateEvent` with `scheduledJob=true`.
- `HttpCheckForOpenDoorsTest.ts` — same as pubsub version, verifies
  response shape.

### Phase H3 — Remote-button handlers

**Files:**
- `http/RemoteButton.ts` → `handleRemoteButtonPoll`,
  `handleAddRemoteButtonCommand`
- `pubsub/RemoteButton.ts` → `handleCheckForRemoteButtonErrors`

**Why:** these have the most logic per handler — auth (push key +
Google ID token + authorized email), ack-token state machine,
rate limiting. Biggest test-coverage win.

**Tests:**
- `HttpRemoteButtonTest.ts`: (a) happy path saves request + returns
  command, (b) ack-token acknowledgment clears command, (c) session
  ID generation when missing from query, (d) disabled config rejects
  400.
- `HttpAddRemoteButtonCommandTest.ts`: (a) happy path saves command,
  (b) disabled config 400, (c) missing/wrong push key 401/403,
  (d) unauthorized email 403, (e) rate-limit 409.
- `PubsubCheckForRemoteButtonErrorsTest.ts`: (a) fresh request —
  no error. (b) stale request — writes error entry with correct
  shape. (c) missing request — writes error entry.

**Dependency:** none — `FakeServerConfigDatabase`,
`FakeRemoteButtonRequestDatabase`, `FakeRemoteButtonCommandDatabase`,
`FakeRemoteButtonRequestErrorDatabase` all already exist.

### Phase H4 — Snooze handlers

**Files:**
- `http/Snooze.ts` → `handleSnoozeNotificationsRequest`,
  `handleSnoozeNotificationsLatest`

**Why:** Existing `SnoozeNotificationsTest.ts` covers the controller
layer (`getSnoozeStatus`, `submitSnoozeNotificationsRequest`). The
wrappers add the HTTP-specific auth + param-parsing layer; this phase
tests those.

**Tests:**
- `HttpSnoozeNotificationsRequestTest.ts`: (a) happy path submits via
  controller, returns 200 with snooze. (b) disabled config 400.
  (c) missing params 400. (d) auth failures 401/403.
- `HttpSnoozeNotificationsLatestTest.ts`: (a) returns current snooze
  status. (b) missing buildTimestamp param 400.

### Phase H5 — ServerConfig, Events, DataRetention, DeleteData

**Files:**
- `http/ServerConfig.ts` → 2 handlers
- `http/Events.ts` → 3 handlers
- `http/DeleteData.ts` → 1 handler
- `pubsub/DataRetentionPolicy.ts` → 1 handler

**Why last:** least-critical paths; mostly read endpoints with simple
auth. Coverage completion rather than new insight.

**Tests:** one test file per handler, 3–5 tests each covering
happy path + auth failure + param validation.

### Phase H6 — Doc cleanup

**Scope:**
- Update `FIREBASE_DATABASE_REFACTOR.md` → *Deferred work* section:
  mark the handler-tests items as complete, link to this plan + PRs.
- Update `FIREBASE_HARDENING_PLAN.md` if anything newly applies.

## Firestore trigger handlers — explicitly skipped

`firestore/Events.ts` (`firestoreUpdateEvents`) is a Firestore
`onWrite` trigger that calls `updateEvent()`. The pure logic is
already tested in `EventUpdatesFakeTest.ts` at the controller level.
The trigger wrapper itself is 3 lines of `change.after.data()` →
`updateEvent(data, false)`. Not worth extracting.

## Safety guards (per PR)

1. `./scripts/validate-firebase.sh` passes.
2. **The pure function's return value, when success, matches the old
   HTTP response body byte-identical.** No field shape changes.
3. **The wrapper's response.status(...).send(...) arguments match the
   old wrapper's exactly.** Same status codes, same error body shape.
4. No collection strings, document shapes, or Firestore call patterns
   change. Phase 4 lint stays green.
5. Tests cover at minimum: happy path + one failure branch. More if
   the handler has non-trivial auth/validation.
6. Grep diff on the handler's Firestore-facing calls — should be
   unchanged.

## Verification strategy

### Per-PR CI

- `npm run build` — TypeScript compiles.
- `npm run tests` — all tests pass, including new ones.
- Emulator smoke test — confirms the wrapper still registers a
  callable function.

### Per-PR manual review

The PR description should include:
1. Diff stats — pure function LOC vs wrapper LOC (wrapper should be
   ~10 lines).
2. Test file list with test count per handler.
3. Confirmation that the response status codes + body shapes are
   byte-identical to the old wrapper.

### Post-merge

No release gate — these are test-only code changes for the wrapper
extraction (the pure function is exactly the old inline code). Can
release when convenient.

## Rollback

Each phase is one PR. `git revert` restores the old inline logic. No
production-data risk at any phase — extraction doesn't change what
Firestore sees.

## Outcome

After H1–H5 (projected):

| Metric | Current | After |
|---|---|---|
| Handler test files | 2 (`SnoozeNotificationsTest`, `EventUpdatesFakeTest`) | ~12 (one per handler) |
| Handlers with unit-test coverage | 0 | 14 |
| `functions.https.onRequest` wrappers containing business logic | 14 | 0 |
| New fake files needed | — | 0 (all reuse existing) |
| New test dependencies | — | 0 |

## Related documents

- [`FIREBASE_DATABASE_REFACTOR.md`](FIREBASE_DATABASE_REFACTOR.md) —
  the refactor that built the fake infrastructure and deferred the
  handler-test work to this plan.
- [`FIREBASE_HARDENING_PLAN.md`](FIREBASE_HARDENING_PLAN.md) —
  parallel hardening work (buildTimestamp config-read, Dependabot).
