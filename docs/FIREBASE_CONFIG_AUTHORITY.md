---
category: reference
status: active
last_verified: 2026-06-20
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

## Backup & restore

`httpServerConfigUpdate` is a **whole-document replace, not a merge**
(`ServerConfig.ts` → `ServerConfigDatabase.set({queryParams, body})`). A partial
POST silently drops every `body` field you didn't include — including
`buildTimestamp` — which then trips the strict-mode throw above for all users.
So **back up the document before any config edit**, and know how to restore it.

Two endpoints (project `escape-echo`; region is the v1 default `us-central1` —
confirm against `BASE_URL` in your decrypted `local.properties`, or
`firebase functions:list --project escape-echo`, before trusting the URL):

| Endpoint | Method | Header | Notes |
|---|---|---|---|
| `httpServerConfig` | GET | `X-ServerConfigKey: <serverconfig.key>` | read key — same value as the app's `SERVER_CONFIG_KEY`. Returns the full doc. |
| `httpServerConfigUpdate` | POST | `X-ServerConfigKey: <serverconfig.updatekey>` | separate write key. Request JSON body → `config.body`; URL query → `config.queryParams`. |

Get both keys: `firebase functions:config:get serverconfig --project escape-echo`.

### Back up (before every edit)

```bash
curl -sS -H "X-ServerConfigKey: <READ_KEY>" \
  "https://us-central1-escape-echo.cloudfunctions.net/httpServerConfig" \
  -o "serverConfig-backup-$(date +%Y%m%d-%H%M%S).json"
# Sanity-check the load-bearing fields survived:
jq '.body | {buildTimestamp, remoteButtonBuildTimestamp}' serverConfig-backup-*.json
```

If `buildTimestamp` is present and non-empty, the backup is good.

### Restore (re-send the complete known-good body)

The whole-doc replace that makes a *partial* POST dangerous is exactly what makes
a *full* restore clean — you re-send the entire backed-up `body`:

```bash
jq '.body' serverConfig-backup-YYYYMMDD-HHMMSS.json > /tmp/restore-body.json
curl -sS -X POST -H "X-ServerConfigKey: <WRITE_KEY>" -H "Content-Type: application/json" \
  --data @/tmp/restore-body.json \
  "https://us-central1-escape-echo.cloudfunctions.net/httpServerConfigUpdate" \
  | jq '.body | {buildTimestamp, remoteButtonBuildTimestamp}'
```

`config.queryParams` resets to empty on restore — harmless; every accessor reads
`config.body.*`, nothing reads `queryParams`.

### Editing a single field safely

Never hand-write a partial body. Either:

- **Firebase console (lowest risk)** — Firestore → `configCurrent` → `current` →
  `body` → edit the one field in place. A true single-field edit, no whole-doc
  replace. This is how the per-user allowlists and feature flags are toggled.
- **GET → modify → POST the full body**, so every other field is preserved:
  ```bash
  curl -sS -H "X-ServerConfigKey: <READ_KEY>" ".../httpServerConfig" \
    | jq '.body + {<field>: <value>}' > /tmp/edit.json
  curl -sS -X POST -H "X-ServerConfigKey: <WRITE_KEY>" -H "Content-Type: application/json" \
    --data @/tmp/edit.json ".../httpServerConfigUpdate"
  ```

**Ritual:** back up → edit (console or full-body POST) → verify → if anything
looks wrong, revert the field (or restore the whole body from the backup).

## Why this is a top-of-stack rule

Across 18 functions, four read these timestamps. The temptation to add a "safe fallback" when something looks risky is strong. The fallback masks bugs: a deleted config field would silently continue with a stale 2021 hardcode, and the team would never know production was running on the wrong device ID.

Strict mode trades a small operational risk (throw on misconfig) for a large observability win (the throw is loud, the silence isn't).

## References

- `FirebaseServer/src/controller/config/ConfigAccessors.ts` — `getBuildTimestamp`, `getRemoteButtonBuildTimestamp`, `requireBuildTimestamp`
- `FirebaseServer/test/controller/config/ConfigAccessorsTest.ts` — pins the throw-on-null behavior + the doc pointer in the error message
- `docs/archive/FIREBASE_HARDENING_PLAN.md` § A3 — the historical removal of `_FALLBACK` constants
- `FirebaseServer/CHANGELOG.md` `server/16`, `server/17` — the two-stage rollout
