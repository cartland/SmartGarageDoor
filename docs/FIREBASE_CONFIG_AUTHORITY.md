---
category: reference
status: active
last_verified: 2026-04-24
---

# Firebase Config Authority

**Rule:** production server config — not code — is the authoritative source for device build timestamps. The four pubsub/HTTP handlers that need them read `body.buildTimestamp` / `body.remoteButtonBuildTimestamp` from `getServerConfig()`. They throw with an ERROR-level Cloud Log if the value is missing. There are no hardcoded fallbacks in the production code path.

## Why

Until `server/16` the four handlers carried hardcoded literal strings (`'Sat Mar 13 14:45:00 2021'`, etc.) for the device build timestamp. Whenever a device was reflashed with a new build, the handlers needed a code change + deploy to recognize the new device.

`server/16` introduced the read-from-config path with named `_FALLBACK` constants preserving the old behavior if the config value went missing. After 24+ hours of warn-level logs confirmed the fallback never fired, `server/17` (A3 of the hardening plan) removed the fallbacks entirely. Production config is now the single source of truth; reflashing a device means a config update via `httpServerConfigUpdate`, not a code deploy.

## The two affected fields

| Config field | Reader | Encoding | Used by |
|---|---|---|---|
| `body.buildTimestamp` | `getBuildTimestamp(config)` | plain | `httpCheckForOpenDoors`, `pubsubCheckForOpenDoorsJob`, `pubsubCheckForDoorErrors` |
| `body.remoteButtonBuildTimestamp` | `getRemoteButtonBuildTimestamp(config)` | URL-encoded since April 2021 | `pubsubCheckForRemoteButtonErrors` |

`getRemoteButtonBuildTimestamp` applies `decodeURIComponent()` so callers see the plain form.

## The strict-mode contract

`requireBuildTimestamp(value, context)` in `FirebaseServer/src/controller/config/ConfigAccessors.ts`:

```typescript
export function requireBuildTimestamp(
  configValue: string | null,
  context: string,
): string {
  if (configValue === null) {
    const msg = `[${context}] buildTimestamp missing from config — cannot proceed. See docs/archive/FIREBASE_HARDENING_PLAN.md Part A / A3.`;
    console.error(msg);
    throw new Error(msg);
  }
  return configValue;
}
```

Failure mode:
- HTTP handlers wrap in try/catch and return 500.
- Pubsub jobs let the throw propagate; Firebase marks the run failed; the next scheduled tick retries.
- Either way, Cloud Logging gets an ERROR-level entry that pages or alerts.

## Operator runbook

If you see `[<context>] buildTimestamp missing from config — cannot proceed` in Cloud Logs:

1. **Check production config first** — restore the missing field via `httpServerConfigUpdate`. This is almost always the fix.
2. **Don't immediately revert** — the throw is the system telling you config is missing, not that the code is broken.
3. The historical rationale + revert path (if you genuinely need the fallback back) is in `docs/archive/FIREBASE_HARDENING_PLAN.md` § A3.

## Why this is a top-of-stack rule

Across 18 functions, four read these timestamps. The temptation to add a "safe fallback" when something looks risky is strong. The fallback masks bugs: a deleted config field would silently continue with a stale 2021 hardcode, and the team would never know production was running on the wrong device ID.

Strict mode trades a small operational risk (throw on misconfig) for a large observability win (the throw is loud, the silence isn't).

## References

- `FirebaseServer/src/controller/config/ConfigAccessors.ts` — `getBuildTimestamp`, `getRemoteButtonBuildTimestamp`, `requireBuildTimestamp`
- `FirebaseServer/test/controller/config/ConfigAccessorsTest.ts` — pins the throw-on-null behavior + the doc pointer in the error message
- `docs/archive/FIREBASE_HARDENING_PLAN.md` § A3 — the historical removal of `_FALLBACK` constants
- `FirebaseServer/CHANGELOG.md` `server/16`, `server/17` — the two-stage rollout
