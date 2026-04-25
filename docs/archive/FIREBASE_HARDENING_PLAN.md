---
category: archive
status: shipped
---
# Firebase Server Hardening Plan

Covers two non-refactor tranches: (A) replacing hardcoded
`buildTimestamp` strings with config-driven reads, and (B) closing the
two open Dependabot alerts. Both are bounded scope with concrete safety
guards.

**Status:** COMPLETE. Shipped through `server/17` (A3 landed there after `server/16`'s 24h observation window cleared). Part A (buildTimestamp config-read) landed via a revert + rewrite after the first attempt shipped to `main` with bugs (wrong config key name + URL-encoding not handled); A3 then removed the fallback safety net now that config is authoritative. Part B (Dependabot) closed one of two alerts; the remaining one is a transitive under `firebase-admin` and will clear when `firebase-admin` itself bumps (see *Deferred / next* below).

### Shipping history

| Item | Plan phase | Shipped PR | Released in |
|---|---|---|---|
| uuid 8 → 14 + regression + CVE-guard | B1 + B2 + B3 (uuid half) | [#491](https://github.com/cartland/SmartGarageDoor/pull/491) | `server/15` |
| fast-xml-parser override + CVE-guard | B4 + B3 (fast-xml half) | [#493](https://github.com/cartland/SmartGarageDoor/pull/493) | `server/15` |
| buildTimestamp config-read — first attempt | A1 | [#492](https://github.com/cartland/SmartGarageDoor/pull/492) | **reverted** in [#494](https://github.com/cartland/SmartGarageDoor/pull/494) before release — read the wrong config key (`doorSensorBuildTimestamp` didn't exist) and returned the URL-encoded `remoteButtonBuildTimestamp` raw |
| buildTimestamp config-read — corrected | A1 (rewrite) | [#496](https://github.com/cartland/SmartGarageDoor/pull/496) | `server/16` |
| Fallback logging helper (`resolveBuildTimestamp`) | (added during A1 rewrite at user request) | [#496](https://github.com/cartland/SmartGarageDoor/pull/496) | `server/16` |
| Fallback removal — `requireBuildTimestamp` replaces `resolveBuildTimestamp` | A3 | (see *A3 — Fallback removal* below) | `server/17` |

**Invariants held (verified):**
- Zero Firestore data impact — collection strings, document shapes, write patterns unchanged throughout.
- Zero runtime behavior change on every deploy — production config verified to resolve to the same strings as the pre-refactor hardcodes.
- Phase 4 lint guard stayed green across all PRs.
- `./scripts/validate-firebase.sh` passed on every PR.
- Production health check post-`server/16` deploy: zero fallback-fired logs, zero decode-failure logs, all 4 pubsub/HTTP functions executing with `status: 'ok'`.

### Deferred / next

- **uuid alert #67 (transitive versions 9.0.1, 11.1.0 under `firebase-admin`).** Remaining Dependabot alert. Not in our exploit path (we call `v4()` with no `buf`; transitives are used by `@google-cloud/*` for request IDs). Decision: **wait for `firebase-admin` itself to bump** rather than force a transitive override. The direct dep is at 14.0.0; the CVE-guard test in `CveGuardTest.ts` pins that.
- **~~Remove `_FALLBACK` constants (A3 in the original plan).~~** **Shipped** — see *A3 — Fallback removal* section below.

**Invariants (both parts, as written pre-execution):**
- Zero Firestore data impact. Collection strings, document shapes,
  write patterns unchanged.
- Zero runtime behavior change without explicit verification.
- Phase 4 lint guard stays green — no new inline `new
  TimeSeriesDatabase(...)`.
- `./scripts/validate-firebase.sh` passes on every PR.

---

## A3 — Fallback removal (history + revert)

Shipped after `server/16` verified that production config has both
`body.buildTimestamp` and `body.remoteButtonBuildTimestamp` populated
and the warn-level fallback logs added during A1 stayed empty for
24+ hours across every pubsub cycle.

### What changed

Before (server/16):

```typescript
// src/functions/http/OpenDoor.ts  (and three sibling files)
const DOOR_SENSOR_BUILD_TIMESTAMP_FALLBACK = 'Sat Mar 13 14:45:00 2021';

export const httpCheckForOpenDoors = functions.https.onRequest(async (_request, response) => {
  const config = await ServerConfigDatabase.get();
  const buildTimestamp = resolveBuildTimestamp(
    getBuildTimestamp(config),
    DOOR_SENSOR_BUILD_TIMESTAMP_FALLBACK,     // ← silent fallback + warn-log
    'httpCheckForOpenDoors',
  );
  // ...
});
```

After (A3):

```typescript
// src/functions/http/OpenDoor.ts  (and three sibling files)

// History: DOOR_SENSOR_BUILD_TIMESTAMP_FALLBACK = 'Sat Mar 13 14:45:00 2021'
// lived here through server/16; removed in A3.

export const httpCheckForOpenDoors = functions.https.onRequest(async (_request, response) => {
  try {
    const config = await ServerConfigDatabase.get();
    const buildTimestamp = requireBuildTimestamp(      // ← throws on null
      getBuildTimestamp(config),
      'httpCheckForOpenDoors',
    );
    // ...
  } catch (error) {
    response.status(500).send({ error: ... });
  }
});
```

The `resolveBuildTimestamp` helper was replaced with
`requireBuildTimestamp`, which throws on null instead of returning a
fallback. The error message includes the context string and a
pointer back to this doc section.

### Why the trade-off

| Aspect | Fallback (server/16) | Strict (A3) |
|---|---|---|
| Config missing → runtime effect | Silent success with stale hardcode | Throw + ERROR log |
| Detectability | Warn-level log (easy to miss) | Error-level log (pages/alerts) |
| Blast radius of config deletion | None | The affected function fails its next run |
| Blast radius of fallback drift (hardcode and prod config diverging) | Silent divergence, possibly for months | Impossible — no hardcode |
| Time-to-detect a real config problem | Hours/days | Minutes |

The A3 trade-off is explicit: we're trading a safety net that masked
bugs for a louder error surface. The safety net was verified dormant
before removal, so we're not losing any working state — just the
illusion that the hardcode was a viable plan B.

### Verification before shipping

Before merging A3:

1. `server/16` deployed and stable for 24+ hours. ✅
2. `gcloud logging read '... "buildTimestamp not in config" ...'` in
   the 24-hour window: **0 hits**. ✅
3. `gcloud logging read '... "failed to URL-decode" ...'`: **0 hits**.
   ✅
4. All 4 functions executed successfully (pubsub + HTTP): **confirmed
   via Cloud Logs**. ✅
5. Production config `body.buildTimestamp` + `body.remoteButtonBuildTimestamp`
   verified manually via `httpServerConfig`. ✅

### Revert path (if A3 causes problems in prod)

If an `ERROR [<context>] buildTimestamp missing from config` log
fires post-deploy, the cause is almost certainly one of:

1. Production config field got deleted (via `httpServerConfigUpdate`
   or manual Firestore edit).
2. Production config field's value went empty.
3. `ServerConfigDatabase.get()` returned unexpectedly (Firestore
   outage — unrelated to A3).

**Fastest recovery:** restore the config field via
`httpServerConfigUpdate` with the correct value. No code change
needed.

**Full revert (emergency only — reintroduces the fallback):**

```bash
# 1. Revert the A3 commit on main.
git revert <A3-PR-squash-SHA>

# 2. Validate + release.
./scripts/validate-firebase.sh
./scripts/release-firebase.sh --check          # prints `server/N` command
./scripts/release-firebase.sh --confirm-tag server/N

# 3. Monitor: the warn-level fallback log returns as the signal that
#    config is still broken, while the function keeps working on the
#    hardcoded fallback.
```

The revert restores:
- `DOOR_SENSOR_BUILD_TIMESTAMP_FALLBACK = 'Sat Mar 13 14:45:00 2021'`
  in 3 door-sensor call sites.
- `REMOTE_BUTTON_BUILD_TIMESTAMP_FALLBACK = 'Sat Apr 10 23:57:32 2021'`
  in the remote-button call site.
- `resolveBuildTimestamp` in `ConfigAccessors.ts` (with its
  warn-level log on fallback use).

### Long-term rule

**Config is authoritative.** If you change a device ID, update
production config via `httpServerConfigUpdate` — never edit the
hardcoded literals (there are none to edit any more). A future
contributor hitting the throw path in Cloud Logs should investigate
the config, not re-add a hardcode.

If the rule ever changes (e.g. we need to support per-environment
hardcode-as-default because staging's config differs from prod), the
right move is a new accessor parameter or a second config field —
not restoring the silent fallback.

---

## Part A — `buildTimestamp` config-read migration

### Current state

Four files hardcode `buildTimestamp` literals. There are **two distinct
device IDs** in use:

| File | Line | Value | Device |
|---|---|---|---|
| `src/functions/http/OpenDoor.ts` | 23 | `'Sat Mar 13 14:45:00 2021'` | door sensor |
| `src/functions/pubsub/OpenDoor.ts` | 23 | `'Sat Mar 13 14:45:00 2021'` | door sensor |
| `src/functions/pubsub/DoorErrors.ts` | 23 | `'Sat Mar 13 14:45:00 2021'` | door sensor |
| `src/functions/pubsub/RemoteButton.ts` | 33 | `'Sat Apr 10 23:57:32 2021'` | remote button |

Server config already has a `body.remoteButtonBuildTimestamp` field
(accessor exists in `src/controller/config/ConfigAccessors.ts`), but no
call site reads it — the remote-button code reads the hardcoded value
with the config-read commented out. There is **no config field** for
the door-sensor device.

### Goal

Read each device's `buildTimestamp` from server config. **Do not change
the runtime value** — same string ends up in the database queries
before and after.

### Non-goals

- Changing which Firestore documents are queried.
- Making pubsub jobs multi-device.
- Introducing per-environment config overlays.

### Design

Two new accessors (pure functions over the config payload):

```typescript
// src/controller/config/ConfigAccessors.ts

export function getDoorSensorBuildTimestamp(config: any): string | null {
  if (config?.body?.doorSensorBuildTimestamp) {
    return config.body.doorSensorBuildTimestamp;
  }
  return null;
}

// getRemoteButtonBuildTimestamp already exists — no change needed.
```

Each call site reads with an explicit fallback to the current
hardcoded value, so runtime behavior is preserved whether or not the
production config has the field populated:

```typescript
const DOOR_SENSOR_BUILD_TIMESTAMP_FALLBACK = 'Sat Mar 13 14:45:00 2021';
// ...
const buildTimestamp =
  getDoorSensorBuildTimestamp(config) ?? DOOR_SENSOR_BUILD_TIMESTAMP_FALLBACK;
```

The fallback constant is named + co-located with the call site so a
reader sees exactly what value ships if config is missing.

### Rollout phases

**A1 — Add accessor + read-with-fallback (safe, zero behavior change)**
- Add `getDoorSensorBuildTimestamp` to `ConfigAccessors.ts`.
- Update 3 door-sensor call sites: `http/OpenDoor`, `pubsub/OpenDoor`,
  `pubsub/DoorErrors`. Each now reads config; falls back to the
  existing hardcoded literal.
- Uncomment + wire up `pubsub/RemoteButton.ts` to use
  `getRemoteButtonBuildTimestamp(config) ?? 'Sat Apr 10 23:57:32 2021'`.
- **Zero behavior change** as long as production config's
  `doorSensorBuildTimestamp` and `remoteButtonBuildTimestamp` are
  either missing OR match the fallbacks.

**A2 — Populate production config (manual, out-of-band)**
- Via `httpServerConfigUpdate`, add `doorSensorBuildTimestamp:
  'Sat Mar 13 14:45:00 2021'` and confirm
  `remoteButtonBuildTimestamp: 'Sat Apr 10 23:57:32 2021'` is set.
- Verify via `httpServerConfig` GET — both fields present with
  expected values.
- Observe Cloud Logging for 24 hours — confirm jobs still run, events
  update, no new errors.

**A3 — Remove fallbacks (optional, later)**
- Delete the `_FALLBACK` constants once A2 is verified stable.
- This makes the config field load-bearing — if someone zeros out the
  config value, behavior changes. That's the point: production config
  becomes the single source of truth.
- Not required; the A1 state is a valid permanent resting point.

### Tests

For A1:

```typescript
// test/controller/config/ConfigAccessorsTest.ts (new or extension)

describe('getDoorSensorBuildTimestamp', () => {
  it('returns the value when present in config body', () => {
    expect(getDoorSensorBuildTimestamp({ body: { doorSensorBuildTimestamp: 'abc' } }))
      .to.equal('abc');
  });
  it('returns null when body is missing', () => {
    expect(getDoorSensorBuildTimestamp({})).to.be.null;
  });
  it('returns null when the field is missing from body', () => {
    expect(getDoorSensorBuildTimestamp({ body: {} })).to.be.null;
  });
  it('returns null when config is null', () => {
    expect(getDoorSensorBuildTimestamp(null)).to.be.null;
  });
});
```

Similar tests for `getRemoteButtonBuildTimestamp` (which currently
lacks them).

### Safety guards (per PR)

1. `validate-firebase.sh` passes.
2. Grep diff: the literal `'Sat Mar 13 14:45:00 2021'` and `'Sat Apr
   10 23:57:32 2021'` still appear somewhere in `FirebaseServer/src/`
   after A1 (as fallback constants). They should NOT disappear until
   A3.
3. No new `new TimeSeriesDatabase(` outside `src/database/` (Phase 4
   lint).
4. New accessor tests cover: present, missing field, missing body,
   null config.
5. Manual: staging deploy + verify handlers still respond before
   cutting the production tag.

### Rollback

A1 is a single PR. `git revert` + redeploy restores pre-PR behavior.
Because A1 preserves runtime behavior via fallback, a rollback is
zero-risk even if config was partially populated.

A2 is a config-only operation — rollback via `httpServerConfigUpdate`
with the old payload.

---

## Part B — Dependabot alerts

### Current alerts (both medium severity)

**Alert #66 — `fast-xml-parser` XMLBuilder injection**
- Our version: **4.5.6** (transitive via
  `firebase-admin → @google-cloud/storage → fast-xml-parser`)
- Patched: **5.7.0**
- Vulnerable range: `< 5.7.0`
- **Direct usage in our code:** none. `grep -r fast-xml-parser
  FirebaseServer/src` returns zero hits. The dependency is pulled in
  by Cloud Storage's internal AWS-style response parsing.
- **Exploitability in our app:** low. The injection requires
  user-controlled data flowing into `XMLBuilder`'s comment or CDATA
  fields — we never call `XMLBuilder`.
- **But** the patched version is a **major bump (4 → 5)**, which
  means `@google-cloud/storage@7.17.0` may or may not be compatible.

**Alert #67 — `uuid` buffer bounds check in v3/v5/v6**
- Our versions: direct `8.3.2` + transitive `9.0.1` (three call
  sites) + `11.1.0` + `8.3.2` (firebase-admin).
- Patched: **14.0.0**
- Vulnerable range: `< 14.0.0` (Dependabot's range — the specific CVE
  is about `v3/v5/v6 with buf` but the advisory is stamped across all
  earlier versions).
- **Direct usage in our code:** `import { v4 as uuidv4 } from 'uuid';
  uuidv4()` in `http/RemoteButton.ts`. We call `v4()` with no `buf`
  argument, so we're **not exposed** to the specific CVE regardless of
  version.
- **Exploitability in our app:** effectively none via our code. We're
  held back only because Dependabot's range catches everything before
  v14.

### Rollout phases

**B1 — Add a regression test for current `uuid` usage (safety anchor)**

Before bumping, pin current behavior:

```typescript
// test/UuidTest.ts
import { v4 as uuidv4 } from 'uuid';
import { expect } from 'chai';

describe('uuid v4 contract', () => {
  it('returns 36-character strings in canonical form', () => {
    const id = uuidv4();
    expect(id).to.match(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
  });
  it('returns distinct values on each call', () => {
    const a = uuidv4();
    const b = uuidv4();
    expect(a).to.not.equal(b);
  });
});
```

Purpose: catch any signature break in v14.

**B2 — Bump direct `uuid` 8.3.2 → ^14.0.0**

- Edit `FirebaseServer/package.json`: `"uuid": "14.0.0"` (exact pin,
  matching the project's pin-everything convention — see server/3
  changelog entry).
- Update `@types/uuid` if needed — check `package-lock.json` after
  install.
- Run `npm install` via `./scripts/firebase-npm.sh install`.
- Run B1 regression test + full validation.
- Transitives (`9.0.1`, `11.1.0`) stay on their own versions until the
  next `firebase-admin` bump pulls them forward; they're not in our
  exploit path.

**B3 — Add CVE-guard test (regression pin, blocks future downgrade)**

Mirror the existing `jws` guard test pattern (see
`test/controller/VerifyIdTokenTest.ts`):

```typescript
// test/CveGuardTest.ts
import { expect } from 'chai';

describe('CVE guards — fail if a flagged version is re-introduced', () => {
  // GHSA-<id>: uuid < 14.0.0 Dependabot alert #67
  it('uuid direct dep is ≥ 14.0.0', async () => {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { version } = require('uuid/package.json');
    expect(semverGte(version, '14.0.0'), `uuid ${version} < 14.0.0`).to.be.true;
  });

  // GHSA-<id>: fast-xml-parser < 5.7.0 Dependabot alert #66
  it('fast-xml-parser resolved version is ≥ 5.7.0', async () => {
    const { version } = require('fast-xml-parser/package.json');
    expect(semverGte(version, '5.7.0'), `fast-xml-parser ${version} < 5.7.0`).to.be.true;
  });
});
```

(Helper `semverGte` lives locally in the test file — 3 lines, no new
dep.)

**This test FAILS until B4 lands.** That's intentional: it documents
the target state and blocks the refactor from ending early.

**B4 — Override `fast-xml-parser` to ≥ 5.7.0 (requires verification)**

Add an npm override in `FirebaseServer/package.json`:

```json
"overrides": {
  "fast-xml-parser": "^5.7.0",
  ...
}
```

This forces the resolution — Cloud Storage gets 5.x instead of 4.5.6.

**Verification required before merging:**
1. `npm install` succeeds.
2. `./scripts/validate-firebase.sh` passes including the emulator
   smoke test (exercises firebase-admin, which could exercise Cloud
   Storage internals).
3. **Manual compat check:** inspect `@google-cloud/storage`'s
   release notes / CHANGELOG for 4.x → 5.x compatibility. If they
   don't document support for fast-xml-parser 5.x, this override is
   risky — escalate or skip.
4. **Deploy to staging/emulator** before cutting the production tag.
   Any regression in Cloud Storage (which `firebase-admin` uses for
   some internal features even if we don't call it directly) would
   surface here.
5. **Fallback plan:** if the override breaks install or runtime, skip
   B4 and mark the alert as "tolerated — transitive, low
   exploitability, awaiting upstream bump." Document the decision in
   this file. The CVE-guard test for `fast-xml-parser` is commented
   out until the bump is safe.

### Safety guards (Part B, per PR)

1. `validate-firebase.sh` passes on every PR.
2. B1 and B3 run as regular unit tests (caught by CI).
3. For B2 (direct bump): PR description includes `npm ls uuid` output
   before/after — confirms the direct version changed and transitives
   stayed put.
4. For B4 (override): PR description documents the version matrix
   (`npm ls fast-xml-parser` + Cloud Storage's documented compat).
5. **Existing guard:** CI already runs `npm audit || true` as a warn
   step. After B2 and B4, alerts #66 and #67 should disappear from the
   dashboard — verify before closing the PRs.

### Rollback

- **B2:** single-line revert of `package.json` + `npm install`.
  `uuid.v4()` is signature-stable across versions 4-14 for the
  no-`buf` invocation.
- **B4:** single-line revert of the `overrides` block + `npm install`.
  If Cloud Storage was working before, it works again after revert.

### What this doesn't fix

- Transitive `uuid@9.0.1`/`@11.1.0` under `firebase-admin`. These
  remain in the lockfile until `firebase-admin` itself bumps them. We
  don't call them directly; they're outside our exploit path. Track
  via Dependabot, pull in via regular `firebase-admin` updates.
- Any future CVE — the CVE-guard test in B3 is the template for
  future alerts: one `it()` per advisory, one-line revert to skip.

---

## Combined ordering

Parts A and B are fully independent — no shared files. Can land in any
order.

Within each part, phases are sequential (A1 → A2 → A3; B1 → B2 → B3 →
B4).

Recommended order for fastest risk reduction:
1. **B1** (test anchor — zero risk, zero deploy)
2. **B2** (direct uuid bump — low risk, closes half the Dependabot
   noise)
3. **A1** (buildTimestamp read with fallback — zero behavior change,
   sets up the future)
4. **B3 + B4** (fast-xml-parser override) — requires the most
   verification, save for last
5. **A2** (populate production config — manual)
6. **A3** (optional — remove fallbacks)

Each can release as its own `server/N` or batch a few into one release
once the pipeline is verified green.

---

## Related documents

- [`FIREBASE_DATABASE_REFACTOR.md`](FIREBASE_DATABASE_REFACTOR.md) —
  the completed refactor that introduced `ConfigAccessors.ts` and
  `ServerConfigDatabase` interface. Part A extends the accessor list.
- [`FIREBASE_DEPLOY_SETUP.md`](FIREBASE_DEPLOY_SETUP.md) — release
  mechanics.
