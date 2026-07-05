---
category: plan
status: active
last_verified: 2026-07-05
---

# R1 — Automatic recovery from a missed push (design, not yet built)

**Status: DESIGN ONLY.** Nothing in this document is implemented. It is the
design + verification story for R1 from
[`NOTIFICATION_RELIABILITY.md`](NOTIFICATION_RELIABILITY.md) ("missed-push
recovery is manual: staleness is auto-detected but only raises a banner;
nothing auto-refetches"). Per that doc's recommended sequence, R1 is the
highest-value remaining reliability fix — and per the repo's
verification-gap rule, the CLI-verifiable test story is settled here BEFORE
any implementation PR.

## 1. Problem

Push data (silent FCM state-sync) is the app's primary freshness mechanism.
When a push is missed (Doze, FCM drop, network partition), the app shows
stale door state until the user notices the stale banner and taps it, or
pulls to refresh. Detection already works — `CheckInStalenessManager`
(usecase, commonMain) flips `isCheckInStale` to `true` when the last device
check-in is older than 11 minutes, re-evaluated reactively on every door
event and on a 30 s interval tick. Recovery is the missing half: the flag
only drives banner visibility (`HomeViewModel` / `DoorHistoryViewModel` via
the shared `HistoryAlert` mapper).

## 2. Measurement gate (do this before building)

The reliability doc's precondition stands: **measure the real miss rate
first.**

- Enable FCM delivery data (Firebase console → Cloud Messaging reporting,
  or the BigQuery delivery export) for the `escape-echo` project.
- Reconcile sends against the client's diagnostics counters ("FCM received"
  is already a lifetime DataStore counter surfaced on the Diagnostics
  screen).
- Decision rule: at ~99.9% observed delivery, R1 is polish (build it when
  convenient); materially lower, R1 is urgent.

The measurement changes nothing about the design below — only its priority.

## 3. Design

### 3.1 Shape: one new app-scoped manager (ADR-015)

A new `StaleCheckInRecoveryManager` in `usecase/` commonMain, mirroring the
`CheckInStalenessManager` / `InitialDoorFetchManager` patterns:

- `@Singleton` provider in BOTH `AppComponent.kt` (Android) and
  `NativeComponent.kt` (iOS) — the two-DI-component rule.
- Idempotent `start()` invoked from `AppStartup.run()` (no ViewModel
  involvement — this is screen-less orchestration; ADR-026 complement).
- Collects `CheckInStalenessManager.isCheckInStale`.
- On the `false → true` transition: run one recovery fetch, then, while
  staleness persists, retry on a capped exponential backoff.
- On `true → false` (freshness restored, by push or by our fetch): cancel
  pending retries, reset backoff.

### 3.2 Recovery action = the existing fetch, nothing new

The recovery fetch is exactly what the stale banner's tap already does
(`resetFcmAndRefetch` minus the FCM re-registration on the first attempt):
`fetchCurrentDoorEvent()` through the existing force-refresh UseCase path.
On the **second** consecutive failed cycle, escalate to the full banner
action (deregister FCM + re-register + refetch) once — this covers the
"subscription silently died" case (R4's gap) without touching topic
names or payloads.

- **FCM safety rules are untouched**: no topic name, no payload key, no
  subscription flow changes. The escalation calls the existing
  `resetFcmAndRefetch` machinery that ships today.
- Fetch results flow through the normal repository upsert — idempotent,
  and a successful fetch updates `lastCheckInTimeSeconds`, which flips
  staleness back to fresh through the existing reactive collector (no new
  state coupling).

### 3.3 Backoff schedule (battery posture)

Staleness onset is rare (threshold 11 min; heartbeat-backed detection), so
the schedule can be generous while still bounded:

- Attempt immediately at onset, then 1 min, 5 min, 15 min, then every 30 min
  while stale. Cap: no more than ~6 attempts/hour worst case.
- No timer runs at all while fresh (the manager only schedules inside a
  stale episode). This respects the repo's "every push/wake has a battery
  cost" rule — this is strictly cheaper than the user-visible alternative
  (indefinitely stale UI).
- Jitter is unnecessary (single-user app; no thundering herd).

### 3.4 The banner stays

The banner remains visible whenever `isCheckInStale` is true — auto-recovery
runs behind it. If recovery succeeds, the banner disappears because
staleness actually cleared, not because we suppressed it. Fail-safe
unchanged: if every fetch fails (device offline), the UI still tells the
truth.

## 4. CLI verification story (the gate for the implementation PR)

All in `usecase/src/commonTest` with `runTest` + virtual time + the existing
fakes (`FakeAppClock`, fake repos with fetch counters). No device needed:

1. **Onset triggers exactly one immediate fetch** — flip staleness true;
   assert fetch count 1 (debounce: interval ticks while already stale don't
   re-trigger).
2. **Backoff schedule honored** — keep staleness true, fail the fetches;
   advance virtual time; assert fetch timestamps match 0 / 1 / 5 / 15 / 30
   min and the 30-min steady state.
3. **Escalation on second failure** — assert the FCM reset path is invoked
   exactly once per episode, and only after a failed first cycle.
4. **Fresh cancels** — mid-backoff, flip staleness false; advance time;
   assert no further fetches, and a later new episode starts the schedule
   from the top.
5. **Failure is contained** — fetch throwing/error result neither crashes
   the scope nor stops the schedule.
6. **`start()` idempotent** — double-start produces no duplicate fetches
   (mirrors `CheckInStalenessManagerTest`).
7. **DI identity** — `ComponentGraphTest` `assertSame` for the new
   `@Singleton` (both components), per the AppComponent safety rules.

Device-only residue: none. Real FCM delivery isn't exercised by this feature
(it only *reacts* to its absence via the already-shipped staleness signal),
so there is no verification gap requiring a device — the fixture story above
is complete.

## 5. Out of scope

- **R2** (runtime topic change) — separate, still deferred.
- Any change to heartbeat cadence, topics, payloads, or server code. This is
  Android+iOS client-side only, riding a normal `android/N` patch release
  (and reaching iOS for free via the shared manager, wired in
  `NativeComponent` + `appStartup.run()`).
- Suppressing or restyling the stale banner.

## 6. Rollout / revert

- Ships dormant-risk-free: no flag needed — behavior activates only inside a
  stale episode, and its failure mode is "banner stays," which is today's
  behavior. Revert = `git revert` of the client PR.
- Changelog: patch bump ("the app now refetches automatically when door
  updates stop arriving; the stale banner remains as the manual fallback").
