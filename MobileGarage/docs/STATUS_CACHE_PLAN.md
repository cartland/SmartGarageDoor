---
category: plan
status: shipped
---

# Status cache plan — persisted last-known-value snapshots (KMP)

> **Shipped 2026-07-14** across PRs #1072 #1073 #1074 #1076 #1077 #1078. The
> living rule is **ADR-034** in `DECISIONS.md`; this document is kept in place
> (rather than moved to `archive/`) because KDoc throughout the status-cache
> code references its section numbers (D1–D6) for design rationale.

Plan for caching server-fetched statuses (button health, snooze state, feature allowlist) locally in the shared KMP layer, so screens show the last-known verdict instantly instead of "Checking…"/loading states, and redundant per-screen-load fetches are removed.

**Provenance:** the design went through two adversarial multi-agent review rounds before this doc was written (2026-07-11). Round 1 confirmed 27 findings against the initial draft (serialization wiring, cloud-backup fail-open, hydration races, decode-failure policy, snooze/allowlist redesigns); round 2 confirmed 27 more against the amended draft (4 spec bugs in the amendments themselves, all fixed here). Verified-magnitude notes from those reviews are inlined where relevant.

## Problems being solved (user-reported, verified 2026-07-11)

1. **Remote-control pill shows a prominent "Checking…" on every cold launch.** The button-health value lives only in an in-memory `MutableStateFlow` on the singleton `NetworkButtonHealthRepository`, seeded `Loading(null)` — survives navigation, not process restart. Desired UX: show NOTHING while unresolved; only the verdict.
2. **Snooze status re-fetched on every Android Settings entry** (`ProfileContent` `LaunchedEffect` fetch + 60s poll), even when recently loaded. iOS fetches only on pull-to-refresh — and has a pre-existing stale "Snoozing until [past]" bug (no expiry flip).
3. **Developer section pops in after a network fetch on every cold start.** The allowlist is fetched once per sign-in and cached in memory only; the visible reload is the cold-start re-fetch, not a per-screen fetch.
4. Cache such statuses locally in the KMP layer; share maximally in commonMain; platform-side only where idiomatic.

Unifying gap: nothing except door events (Room) and app settings (DataStore) survives a process restart.

## Design

### D1. Snapshot store (PR 1)

- **Module placement:** `:data-local` already depends on `:data`, so the store cannot live wholly in `:data-local` and be consumed by the repos (all in `:data`). Mirror the `LocalDoorDataSource` pattern: the `StatusSnapshotStore` **interface, the envelope type, and the `@Serializable` snapshot DTOs live in `:data`** (which already has the kotlinx.serialization plugin); only the DataStore-backed implementation (`DataStoreStatusSnapshotStore`) lives in `:data-local` beside `DataStoreFactory`, bound impl→interface in both DI components. `:domain` stays annotation-free.
- New DataStore file `status_cache.preferences_pb` via `DataStoreFactory.createStatusCacheDataStore()` (lazy-cached, same discipline as the existing two files). One preferences key per status; value = one self-contained envelope string read/written atomically:

```json
{ "schemaVersion": 1, "fetchedAtEpochSeconds": ..., "confirmedAtEpochSeconds": ..., "accountEmail": "... (per-user entries only)", "payload": { …DTO… } }
```

- **The store's public API NEVER throws — reads AND writes.** `read()` catches every failure (envelope parse, schemaVersion mismatch, payload decode), returns cache-absent, and deletes the corrupt entry (self-healing). `write()`/`clear()` also catch-and-log internally — disk-full/IO errors must not crash the `CoroutineExceptionHandler`-less `applicationScope`. Json: `ignoreUnknownKeys = true`; new DTO fields get defaults; schemaVersion bump = deliberate invalidation.
- File corruption: `ReplaceFileCorruptionHandler { emptyPreferences() }` on store creation; hydration callers still wrap reads (belt-and-braces).
- Clock-skew guard: `fetchedAt`/`confirmedAt` more than 60s in the future ⇒ treated as stale (a backwards clock correction must never suppress revalidation indefinitely).
- **Backup exclusion in the SAME PR:** add the filename to both `backup_rules.xml` and `data_extraction_rules.xml` (the M6 security-audit posture excludes all local data from Google Drive Auto Backup, and the exclude lists are per-exact-filename — a new file silently fails open into backup). Add a buildSrc guardrail asserting every `*_FILE_NAME` constant in `DataStoreFactory.kt` appears in both XML exclude lists (wired into validate.sh), closing the fail-open class permanently.
- **`checkDataStoreSingleton` extension must be a real edit:** the existing task greps `fun <name>(` under `androidApp/src/main/java` only, so merely adding `createStatusCacheDataStore` to its method list would never match. Extend the task (or the companion check) so the new factory method + provider wiring in BOTH components is actually guarded; verify the check fails before the wiring exists (red-then-green).
- **Central sign-out clear site is created in PR 1** (no such mechanism exists today): a small `@Singleton` clearer observing authState; on `Unauthenticated` it clears a registered set of store keys. PRs 2–4 only register keys, avoiding cross-PR file conflicts. Known residual (accepted): an in-flight write-through can re-write an entry seconds after the clear — harmless for button-health/snooze (household-device state; display is auth-gated), and the allowlist is protected by account-keying (D4).
- `test-common`: `FakeStatusSnapshotStore` (pre-seed, `failNextRead`, `failNextWrite`, recorded writes).
- DI: providers + abstract vals in `AppComponent` (`@Singleton`) and `NativeComponent` (`@SharedSingleton`); `ComponentGraphTest` **and** `NativeComponentTest` identity tests.

### D2. Button health (PR 2)

- Hydration routes through the existing `tryWrite`/`shouldOverwrite` — never a direct StateFlow assignment — **plus two corrections to the arbitration:**
  - **A disk seed gets no UNKNOWN privilege.** Track `currentIsDiskSeed` in the repo; the first server result (fetch or FCM, ANY state including UNKNOWN) replaces a disk-seeded value regardless of `shouldOverwrite`'s "non-UNKNOWN beats UNKNOWN" rule, then clears the flag. (Verified window without this: ~1 minute of stale "Available" after a firmware-rotation reset — the server's 1-min pubsub sweep converts the no-doc UNKNOWN to a persisted OFFLINE + FCM. Cheap to close.)
  - **Serialize the writers.** `tryWrite` + its disk write-through run under a repo-internal `Mutex` so hydration racing an FCM update cannot interleave check-then-act, and memory/disk can never record different winners.
- **Write-through and freshness bookkeeping:** every ACCEPTED write persists the envelope. Additionally, every **successful fetch refreshes `confirmedAtEpochSeconds`** even when the payload write is rejected as timestamp-equal (a successful revalidate is an authoritative "still true now"). The **24h display-TTL keys off `confirmedAt`**, not last-accepted-write — without this, a stable device's revalidates never refresh freshness and a daily user gets a spurious Hidden flash roughly every other day.
- Fetch policy: always revalidate on cold start (no fetch-skip TTL); the snapshot exists purely for instant display.
- Display-TTL: snapshot with `confirmedAt` older than 24h (or future-skewed) treated as absent → Hidden. Never show an affirmative verdict older than the TTL.
- `Forbidden` clears both persisted snapshot and in-memory value (targeted exception to `writeErrorPreservingComplete`), mirroring the manager's Forbidden→FCM-unsubscribe — a de-allowlisted user converges to Hidden, not a permanent "Available" lie. Accepted: `ButtonHealthError.Forbidden` also covers stale-pushKey 403s, so a config-rotation blip clears the cache — consequence is Hidden until the next successful fetch (fail-safe direction).
- Sign-out: button-health key registered with the D1 clearer.
- **Display: replace `ButtonHealthDisplay.Loading` with `Hidden` wholesale** (rename/repurpose — do NOT leave a permanently unreachable `Loading` arm). Three change sites: `ButtonHealthDisplayLogic` mapping (no-verdict → `Hidden`), `ComputeButtonHealthDisplayUseCase` `stateIn` `initialValue = Hidden` (the iOS wrapper seeds synchronously from `.value`), both UIs render nothing for `Hidden` (Compose `when` + Swift exhaustive switch force handling). **Update the committed Home preview fixtures that pin the "Checking…" pill** + regenerate the Android screenshot gallery and the iOS snapshot gallery in the same PR, so committed references can't advertise a state production can no longer render.
- Verdict arms (Available / Unavailable / Unknown / Unauthorized) keep rendering — only no-verdict becomes invisible.

### D3. Snooze (PR 3)

- Persist raw `endTimeSeconds` (+ envelope), never the serialized `SnoozeState`; hydration recomputes via `snoozeStateFromEndTime(endTime, now)` so an expired snooze hydrates as NotSnoozing, not a stale "Snoozing until [past]".
- **Hydration/first-publish ordering:** the repo holds an internal `hydrated: CompletableDeferred<Unit>`. The init coroutine hydrates (CAS-seed iff still the `Loading` sentinel), completes `hydrated`, then TTL-decides the init fetch. Every OTHER writer that can touch the `Loading` sentinel — in particular `doFetchSnoozeStatus`'s failure path (`clearLoadingState` → NotSnoozing) and the VM's screen-entry revalidate — **awaits `hydrated` first**. Without this, a failed fetch racing hydration converts `Loading` → NotSnoozing and the CAS discards a valid fresh snapshot exactly in the offline case. The VM's TTL gate reads `fetchedAt` from the same store read and must wait for hydration, never fetch pre-hydration.
- **Shared expiry derivation in commonMain** (`stateIn(applicationScope, Eagerly)` pattern combining snooze state + `LiveClock`), consumed by both platforms' ProfileViewModel — fixes the pre-existing iOS stale "Snoozing until [past]" label; zero platform expiry code.
- **Door-event voiding hook:** the server voids a snooze on ANY door event (anchor-event mismatch), and the deleted Android poll was the only channel that detected voiding and cross-device changes. Trigger: **"any FCM-received door event while repo state is Snoozing → one debounced refetch"** — NO anchor comparison (the client never receives the anchor today; the server voids on any event, so this is faithful). Anchored in the FCM receive path only (never a doorRepository flow observer, so cold-start fetches/hydration can't fire it). **The hook must not force-construct the lazy snooze repo:** it accesses the repo through a nullable handle the repo registers at construction — if the repo was never constructed (user never opened Settings), the hook is a no-op; background FCM wakes never pay repo-construction + fetch cost. Debounce: at most one in-flight refetch; a refetch result must not clobber an in-flight snooze submit (submit wins; ignore refetch completions while a submit is in flight). *Deferred alternative, recorded for the ADR: the server's GET already returns the anchor (`currentEventTimestampSeconds`) — the client DTO just drops it; parsing it would enable the precise comparison later as a purely additive client change.*
- Delete the Android `LaunchedEffect` fetch + 60s poll. Replacements: instant cached display; TTL-gated (~5 min) VM revalidate on screen entry (post-hydration); manual refresh on both platforms; the door-event hook; the shared expiry derivation.
- The snooze ROW stays visible always (it is the tap target for setting a snooze); neutral subtitle while unresolved, never a "Checking" treatment.
- Snooze key registered with the D1 sign-out clearer.

### D4. Feature allowlist (PR 4)

- Hydrate for instant display; ALWAYS revalidate on cold start (no fetch-skip TTL — restart remains the grant-propagation path; a fetch-skip TTL would remove the only way a mid-day access grant reaches the device, since Settings has no pull-to-refresh).
- **Keyed hydration lives INSIDE the auth gate** (auth state is `Unknown` at repo construction — there is no email to compare against at plain init time): the repo's single init coroutine runs `authState.first { it !is AuthState.Unknown }` — on `Unauthenticated`: delete the entry, done; on `Authenticated`: read the snapshot, compare `accountEmail` to the signed-in email, CAS-seed iff match and `_allowlist.value == null`, delete only on a CONFIRMED mismatch (both emails present and different — **never delete on email-unavailable**); THEN enter the existing collect loop (which replays the current value, so the Authenticated fetch still fires after the seed — hydrate-before-fetch preserved inside the collector). Account-keying also covers the clear-miss holes (StateFlow conflation skipping an `Unauthenticated` emission on a fast account switch; process death between memory clear and disk clear).
- Sign-out: key registered with the D1 clearer (account-keying is the real guard).
- Display unchanged (null allowlist = rows absent); hydration removes the cold-start pop-in. Display-TTL 24h off `confirmedAt`, same skew guard.

### D5. ServerConfig — decided NOT to persist (2026-07-18)

`CachedServerConfigRepository` stays unpersisted — **this is a settled decision, not a "later" item.** The only payoff would be shortening the *silent* button-health cold-start revalidate (serially gated on a serverConfig fetch + token refresh, 2–3 RTTs → ~1). That window is invisible: `ServerConfig` has no UI of its own, and button-health *display* already hydrates instantly from its own snapshot (D2), so nothing a user sees would change.

Against that zero visible gain sit two real costs the other three statuses don't carry:
1. **Secret on disk.** `ServerConfig` holds `remoteButtonPushKey` (the `X-RemoteButtonPushKey` shared secret). Persisting it writes that secret to disk — mitigated (rides in the backup-excluded `status_cache.preferences_pb`; the key alone does not authorize a push, the server also verifies the ID-token + email allowlist), but still a new surface for no benefit.
2. **Permanent maintenance weight** — a fourth persisted status: DTO, `StatusCacheKey`, both-component DI, identity tests, sign-out registration, carried forever.

The only scenario where a cached config could matter is a cold start on a flaky network — but door control needs the network anyway, so a cached key unblocks nothing. Net-negative trade; not doing it. The accepted consequence is the permanent 2–3-RTT cold-start revalidate, bounded by the D2 display-TTL.

### D6. Platform-side exceptions

- OS notification permission (`POST_NOTIFICATIONS` / `UNUserNotificationCenter`): read live on appear, never persisted (Compose/Swift).
- Pure presentation state (expanded cards, scroll position): `remember` / SwiftUI `@State`.
- Everything else — DTOs, envelope, TTL policy, hydration, expiry derivation, Hidden arm — commonMain.

## Guardrails & process

- Every PR edits BOTH `AppComponent` and `NativeComponent`; `ComponentGraphTest` + `NativeComponentTest` identity tests; local iOS framework build (`:iosFramework:iosSimulatorArm64Test`) + `:iosFramework:spotlessApply` before every push (iOS CI is not a required check).
- Backup-exclusion guardrail + real (verified red-then-green) `checkDataStoreSingleton` coverage for the new store.
- New ADR (~ADR-034) when PR 2 lands: envelope format (schemaVersion / fetchedAt / confirmedAt / accountEmail), never-throw store contract, decode-failure = absent+delete, clock-skew rule, per-repo hydration-ordering shapes (mutex-arbitrated tryWrite / CompletableDeferred-sequenced / auth-gated keyed seed), disk-seed-loses-to-any-server-result rule, display-TTL (`confirmedAt`) vs fetch-TTL distinction, hidden-until-verdict.

## Rollout

1. PR 1 — D1: store interface + envelope in `:data`, impl in `:data-local`, backup excludes, guardrail checks, sign-out clearer, fakes, DI wiring both components. Infrastructure only; no repo behavior change. (Per-status DTOs may land here or with their consuming PRs.)
2. PR 2 — D2 button health end-to-end (mutex, seed-flag, `confirmedAt` bookkeeping, Forbidden-clear, Hidden rename ×3 sites, preview/gallery updates, both UIs).
3. PR 3 — D3 snooze (endTime persistence, `CompletableDeferred` ordering, shared expiry derivation, door-event hook, poll removal, row subtitle).
4. PR 4 — D4 allowlist (auth-gated keyed hydration).
5. ADR + docs update; this plan moves to `status: shipped` / archive.

## Accepted tradeoffs (explicit, review-verified magnitudes)

- Cold-start verdict staleness: seconds (revalidate round-trip; the 2–3-RTT chain is the permanent steady state — ServerConfig is deliberately not persisted, see D5); the ~1-minute stale window after a firmware-rotation reset is closed by the disk-seed rule; offline cold start keeps the last confirmed verdict until the display-TTL — same posture as Room-hydrated door state.
- Cross-device snooze changes detected at: door-event hook, screen-entry TTL revalidate, manual refresh, cold start. Dominant failure direction fail-safe (unexpected warning rather than a missed one).
- A stale-pushKey 403 clears the button-health cache → Hidden until next success (fail-safe).
- In-flight write-through may re-write a just-cleared entry at sign-out (harmless for non-account-keyed entries; allowlist is account-keyed).
