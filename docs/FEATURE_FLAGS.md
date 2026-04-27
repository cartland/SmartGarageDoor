# Feature Flags — Per-User Server-Maintained Allowlist

How to add a new per-user feature flag, gated by an email allowlist edited
in the Firebase console. The canonical example is the **Function List**
screen (PR #573). This doc names the convention so the next flag is a
mechanical copy.

## Architecture summary

- **Server side of truth.** The allowlist of allowed emails per feature lives
  in Firestore at `configCurrent/current` under
  `body.featureXAllowedEmails: string[]`. Edited directly in the Firebase
  console's Firestore Data tab — no redeploy needed.
- **Per-feature endpoint.** Each flag gets its own authenticated HTTP
  endpoint `GET /<feature>Access` returning `{enabled: boolean}`. The
  endpoint verifies the Firebase ID token, extracts the email, and checks
  membership.
- **UI hint, not security boundary.** The endpoint is a UI hint. Action
  endpoints (`pushButton`, `snoozeNotifications`) **independently** verify
  the allowlist server-side — bypassing the client gate (modified APK,
  debugger) leaks zero capability because the action endpoint will 403.
  Never let a future refactor consolidate "we already checked at the
  feature-access endpoint" — that turns the gate into a real security
  boundary that's trivially bypassable.
- **Android client absorbs the wire shape in its data layer.** Domain,
  UseCase, and UI types are wire-agnostic. A future server-side refactor
  to a bulk `/featureAccess` endpoint changes one Ktor data source file
  and nothing else.
- **Wire-contract fixtures lock the bytes.** Both server and Android tests
  load the same JSON files from `wire-contracts/<feature>Access/`. A
  unilateral rename on either side fails the test on at least one side.
  See [`wire-contracts/README.md`](../wire-contracts/README.md).

## Naming conventions

| Layer | Convention | Example |
|---|---|---|
| Firestore field | `body.featureXAllowedEmails: string[]` | `featureFunctionListAllowedEmails` |
| Server accessor | `getXAuthorizedEmails(config)` in `ConfigAccessors.ts` | `getFunctionListAuthorizedEmails` |
| Server handler file | `XAccess.ts` in `FirebaseServer/src/functions/http/` | `FunctionListAccess.ts` |
| Server handler core | `handleXAccess(input)` | `handleFunctionListAccess` |
| Server handler export | `httpXAccess` | `httpFunctionListAccess` |
| Server route + function name | `XAccess` | `functionListAccess` |
| Wire fixture dir | `wire-contracts/<XAccess>/` | `wire-contracts/functionListAccess/` |
| Wire DTO (Android, private) | `KtorXAccessResponse` | `KtorFunctionListAccessResponse` |
| Domain field on `FeatureAllowlist` | `featureX: Boolean` (or just `x: Boolean` if unambiguous) | `functionList: Boolean` |
| UseCase method | `ObserveFeatureAccessUseCase.x(): Flow<Boolean?>` | `functionList()` |
| VM-exposed flag | `accessGranted: StateFlow<Boolean?>` (one per VM if 1:1 with screen, or `xAccess`/`yAccess` if shared) | `accessGranted` |

## Boolean tri-state semantics

`accessGranted: StateFlow<Boolean?>` carries a load-bearing tri-state:

| Value | Meaning | UI behavior |
|---|---|---|
| `null` | Unknown (pre-fetch, fetch failed, or signed out) | Gate closed (deny). UI shows the denial state. |
| `false` | Server explicitly denies — email not in allowlist | Gate closed (deny). |
| `true` | Server explicitly allows | Gate open. |

Both `null` and `false` deny. The distinction matters only if you're
debugging or want to differentiate "haven't asked yet" from "answered no" —
production UI does not. **Do not** migrate this to a `LoadingResult<Boolean>`
without auditing every gate site: `LoadingResult.Loading(null)` collapses
the tri-state and a careless gate might fall through to "allow" during
loading (`if (loading) showButtons()` is the trap to avoid).

## Files to touch when adding feature `X`

### Server

1. **`FirebaseServer/src/controller/config/ConfigAccessors.ts`** — add
   `getXAuthorizedEmails(config): string[] | null` mirroring
   `getFunctionListAuthorizedEmails`. Null-tolerant — missing field reads
   as `null` so a fresh deploy starts deny-all.
2. **`FirebaseServer/src/functions/http/XAccess.ts`** — copy
   `FunctionListAccess.ts`. Use `isEmailInAllowlist` from
   `controller/Auth.ts` (do **not** inline an `Array.isArray(...).includes(...)`
   check — that is the duplication seed PR #574 killed).
3. **`FirebaseServer/src/index.ts`** — register the handler under the
   `process.env.FUNCTION_NAME` gate so dev/debug deploys can target it.
4. **`FirebaseServer/test/functions/http/HttpXAccessTest.ts`** — copy the
   FunctionListAccess test, update fixture path. The fixture-loading +
   deep-equal pattern is the wire-contract lock; preserve it.

### Wire contract

5. **`wire-contracts/<feature>Access/response_enabled_true.json`** —
   `{"enabled":true}`. (Plus `response_enabled_false.json` and any error
   fixtures.) Keep the bytes minimal; one fixture per response shape.
   See [`wire-contracts/README.md`](../wire-contracts/README.md).

### Android

6. **`AndroidGarage/domain/.../model/FeatureAllowlist.kt`** — add field
   `val featureX: Boolean` (or just `val x: Boolean`). Existing call sites
   that construct the data class break — that's intentional, the compile
   errors are the migration checklist.
7. **`AndroidGarage/data/.../NetworkXAccessDataSource.kt`** + Ktor impl —
   copy `NetworkFeatureAllowlistDataSource` and its Ktor cousin. Each
   feature endpoint gets its own data source; the cached repo aggregates.
   *(When the user later refactors to a bulk `/featureAccess` endpoint at
   ~feature #3, these per-feature data sources collapse to one. Until
   then: one per endpoint.)*
8. **`AndroidGarage/data/.../repository/CachedFeatureAllowlistRepository.kt`**
   — extend to fan out to the new data source on auth-driven refresh.
   Preserve the user-switch race guard (capture-email-then-recheck after
   the network call).
9. **`AndroidGarage/usecase/.../ObserveFeatureAccessUseCase.kt`** — add
   `fun x(): Flow<Boolean?> = featureAllowlistRepository.allowlist.map { it?.x }`.
10. **The screen's ViewModel** — inject the new UseCase, expose
    `xAccess: StateFlow<Boolean?>` (or whatever name fits the screen).
    Keep tri-state semantics; gate UI on `== true`, deny otherwise.
11. **`AndroidGarage/androidApp/.../di/AppComponent.kt`** — provider for
    the new data source as a `@Singleton` plus an abstract entry point.
    `ComponentGraphTest` gets a new `assertSame` identity test.
12. **`AndroidGarage/test-common/.../FakeNetworkXAccessDataSource.kt`** —
    fake mirroring `FakeNetworkFeatureAllowlistDataSource`.
13. **Tests** — copy `KtorNetworkFeatureAllowlistDataSourceTest` and
    `CachedFeatureAllowlistRepositoryTest` patterns. The wire-fixture
    deep-equal test is the cross-stack drift detector — preserve it.

## What to **NOT** do

- **Do not** consolidate to a bulk `/featureAccess` endpoint until you
  have at least 3 features. Designing a bulk shape against one data point
  is more likely to be wrong than designing it after seeing a real second
  flag. PR #573 explicitly chose ship-now-refactor-later; that decision
  documented in [`AndroidGarage/docs/DECISIONS.md`](../AndroidGarage/docs/DECISIONS.md).
- **Do not** store URL-encoded values in `configCurrent`. The
  `remoteButtonBuildTimestamp` field is URL-encoded for historical
  reasons (April 2021 onward) and the accessor `decodeURIComponent`s it
  on read. New fields **should be plain ASCII**. If a value contains
  characters that need escaping, store them inline and document at the
  accessor.
- **Do not** add allowlist contents to error responses or log lines. The
  endpoint returns `{enabled: bool}` and nothing else. Server logs
  `console.log/error` only on failure paths, never the allowlist itself.
- **Do not** rely on the client gate as a security boundary. Action
  endpoints **independently** check the allowlist server-side. This is
  load-bearing — see [`AndroidGarage/docs/DECISIONS.md`](../AndroidGarage/docs/DECISIONS.md)
  ADR-026 and `RemoteButton.ts` / `Snooze.ts` for the canonical pattern.

## Operational concerns

- **Editing the allowlist.** Open the Firebase console → Firestore Data →
  `configCurrent/current` → edit the `featureXAllowedEmails` array. Add
  the email; save. Effect is instant on the next client refetch (which
  fires on every sign-in transition). No redeploy.
- **Removing access.** Same flow; remove the email from the array. Users
  whose tokens expire (≤1 hour) will see the change on next refetch. To
  force-revoke immediately, a future enhancement could clear the
  client-side cache on a separate signal (FCM push, polling) — but today
  the latency window is the Firebase ID-token refresh interval.
- **Empty allowlist** (`[]`) is deny-all but explicit. **Missing field**
  is also deny-all but implicit. Both safe, both fail-closed.
- **Adding a new flag.** Editing the doc to add the new field is
  zero-downtime. Old code that doesn't know about the new field simply
  won't read it.

## When to revisit this design

- **3+ flags or 5+ endpoints overall.** Consider migrating to a single
  bulk `/featureAccess` endpoint returning `{features: {...}}`. The
  per-feature endpoints become thin shims pointing at the bulk handler,
  and old clients keep working.
- **Need to gate a flag on a non-email signal.** E.g., per-device, per-
  app-version, per-A/B-bucket. The current accessor pattern is email-
  only; extending to a richer condition would require a different
  accessor + (potentially) Firebase Remote Config Conditions.
- **A second consumer joins** (web client, scripts). At that point the
  hand-written JSON fixtures become a real schema; consider OpenAPI +
  codegen. Until then, fixtures + per-side tests are the cheaper lock.
