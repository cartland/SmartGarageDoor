---
category: reference
status: active
last_verified: 2026-08-23
---

# Door update strategy — how each platform stays fresh

Android has always received live door updates and iOS never has. This
document is the seam that lets those two facts coexist without forking the
app: one shared abstraction, one flag, three implementations, and a
rollout that moves iOS forward without touching Android at all.

All paths are relative to `MobileGarage/` unless prefixed with `scripts/`,
`docs/`, or `FirebaseServer/`.

---

## 1. Why iOS is dark

The server's door-event FCM message is built in
`FirebaseServer/src/controller/fcm/EventFCM.ts`. It sets `data`, `topic`,
and `android` — and nothing else. `TopicMessage`
(`FirebaseServer/src/model/FCM.ts`) still has `apns: ApnsConfig` commented
out, as it has been since 2021.

A data-only FCM message reaches an Apple device only when the message
carries `apns.payload.aps.content-available = 1`, which is what makes FCM
send it to APNs as a background push. Without it there is nothing for iOS
to wake for. Every other part of the iOS path is already built and
correct: `AppDelegate.didReceiveRemoteNotification` parses the payload
with the SHARED `FcmPayloadParser` and hands it to
`ReceiveFcmDoorEventUseCase`, exactly as Android's `FCMService` does. The
APNs `.p8` key is uploaded to Firebase. The entitlement ships. **The
client is waiting for a message the server never addresses to it.**

That is fixable, and fixing it is the long-term plan below. It is
deliberately NOT the short-term plan, for two reasons:

1. It is a **server** change on the path that carries every Android
   device's door updates. The blast radius of getting it wrong is
   "Android goes dark too", which is a far worse failure than the one
   being fixed.
2. Even once fixed, iOS background pushes are **budgeted and throttled by
   the OS** and are dropped entirely in Low Power Mode. Push alone was
   never going to be the whole answer on iOS.

## 2. The seam

| Layer | Module | What lives there |
|---|---|---|
| Decision type | `:domain` | `DoorUpdateStrategyId` (behavior), `DoorUpdateStrategyOverride` (what the user picked) |
| Build default | `:domain` | `AppConfig.defaultDoorUpdateStrategy` — no default value, so each platform must answer |
| Persisted flag | `:domain` / `:data-local` | `AppSettingsRepository.doorUpdateStrategy` → `DataStoreEnumSetting` |
| Behavior | `:usecase` | `DoorUpdateStrategy` + `Push` / `Poll` / `PushWithForegroundRefresh` implementations |
| Orchestration | `:usecase` | `DoorUpdateManager` (ADR-015) — resolves the flag, runs one strategy, swaps live |
| Lifecycle input | `:usecase` | `AppVisibilityState` — a sink the platform writes to |
| Platform: Android | `androidApp` | `GarageApplication` activity-lifecycle counter → `setVisible`; default `PUSH` |
| Platform: iOS | `iosApp` | `GarageControlApp` `scenePhase` → `setVisible`; default `POLL` |
| Platform: UI | both | Settings → Developer → "Door updates" |

Two rules keep this from becoming a fork:

**A strategy decides WHEN to ask, never what the answer means.** Every
implementation ends at `DoorRepository.fetchCurrentDoorEvent()`, or at
push, which lands in the same repository via
`ReceiveFcmDoorEventUseCase`. The Room cache, the flows, and every screen
are identical no matter which strategy runs. Nothing downstream can tell
the difference except in timing — which is exactly what makes swapping one
at runtime safe.

**The platform reports, shared code decides.** `AppVisibilityState` is a
sink the platform writes (`setVisible`), matching how door events already
arrive: `FCMService` and `AppDelegate` call into shared code, they are not
polled by it. There is no `expect`/`actual` and no bridge interface for
lifecycle.

### Naming

The constants name a **behavior**, not a transport: `PUSH`, `POLL`,
`PUSH_WITH_FOREGROUND_REFRESH`. Push on Android is FCM; push on iOS is
FCM→APNs. That is a platform fact, and the shared layer has no business
asserting it — a constant called `FCM` would tell an iOS implementer the
question is settled when it is not. This is the `AppBuildFact` rule from
#1188 (CLAUDE.md § "Shared decides, platform words it") applied to a
second case. Each platform words the picker in its own strings.

### Swapping

`DoorUpdateManager.start()` collects the setting and runs the resolved
strategy inside `collectLatest`. The swap mechanism is `collectLatest`
itself: a new value cancels the running strategy's coroutine, and
structured concurrency tears down its timer, its in-flight fetch, and its
visibility collector along with it. No `stop()` to forget, no window where
two strategies write the same cache.

`DoorUpdateStrategyOverride.PLATFORM_DEFAULT` is the stored default, so
"no opinion" stays representable. Storing the resolved id instead would
freeze whatever the default happened to be the day the user first opened
the picker — and iOS is expected to move its default later, which is
precisely the case that would break.

### Why this is not a new data-graph node

`currentDoorEvent` keeps `Cadence.PUSH`. The annotation names what changes
a node *behind the app's back*; fetches the app itself issues have never
changed it (`Cadence.USER_ACTION` reads "a user action **or an
app-initiated fetch**"), and `InitialDoorFetchManager` and pull-to-refresh
already write this node the same way a poll does.

Relabelling it `POLL` would also be wrong mechanically. `POLL` in
`DataGraph` means a subscriber-held collection loop that gating can pause,
and G4 would then force `Sharing.Gated` on every derived node downstream —
gating them to pause a loop no subscriber holds open. `AppVisibilityState`
is likewise not a node: it drives *when* the app fetches, not what any
node's value is, and it is a concrete class rather than a `:usecase`
interface so the C1 flow sweep does not enumerate it.

## 3. The three strategies

| | Timer | Foreground refresh | Needs push | Today |
|---|---|---|---|---|
| `PUSH` | none | no | yes | **Android default** |
| `POLL` | 15s while visible | yes | no | **iOS default** |
| `PUSH_WITH_FOREGROUND_REFRESH` | none | yes | yes | iOS destination |

`PUSH` does nothing at runtime, on purpose — the honest implementation of
"the server pushes to us" is a coroutine that waits. It is also the value
a test picks when it wants a quiet app. It deliberately does NOT own FCM
topic subscription: `FcmRegistrationManager` owns that, and those topics
also carry button-health and resolved-notification traffic, so swapping
strategies must never silently unsubscribe a device from its
notifications.

`POLL` is visibility-gated. A backgrounded app has no one to show the
result to, and an interval timer is the one strategy that costs something
to leave running. iOS suspends the process anyway; on Android nothing
would stop the loop, which is why the gate lives in shared code rather
than being left to platform behavior. Failures back off geometrically
(15s → 30s → 60s → 120s cap) and reset on the first success.

`PUSH_WITH_FOREGROUND_REFRESH` has no timer; becoming visible buys exactly
one request. This is not belt-and-braces — it is the answer to iOS's push
budget. A failed refresh retries until it succeeds, then goes quiet, so a
bad network cannot silently promote it into a poll.

### Known: one duplicate request at cold start

The platform reports visibility at roughly the moment `AppStartup` runs,
so on iOS a `POLL` foreground fetch and `InitialDoorFetchManager`'s
one-shot both ask for the current event within milliseconds of each other
at launch. Accepted rather than suppressed: they are not fully redundant
(the initial fetch also loads door *history*, which no strategy does), and
deduplicating would require one to know about the other's timing — a worse
trade than one idempotent GET per launch.

### Wear already polls — with a second implementation

`WearAppConfigFactory` declares `POLL`, and that is the truth: the watch
has polled since before this enum existed. `WearHomeViewModel.onVisible()`
starts a foreground refresh loop and `onHidden()` stops it. The watch has
no FCM registration at all, so `PUSH` was never available to it.

But it is a **different implementation of the same policy**, and the
differences are all load-bearing:

| | Phone / iOS | Wear |
|---|---|---|
| Host | `DoorUpdateManager` (app-scoped) | `WearHomeViewModel` (screen-scoped) |
| Visibility source | `AppVisibilityState` (`scenePhase` / Activity count) | `onVisible()` / `onHidden()` from the composition root |
| Cadence | fixed 15s | 10s idle, 2s while a press is waiting on the door |
| Failure backoff | geometric to 2 min | **none** — retries at 10s forever |
| Swappable by flag | yes | no (nothing reads the declaration) |

The cadence difference is why the loop lives in the ViewModel: it depends
on `ButtonStateMachine` state and `voicePressAwaitingDoor`, which only the
VM holds, and an app-scoped manager cannot read them (`:usecase` cannot
import `:viewmodel` — G0). So this is not a case of the watch having
missed a refactor. It is a case of **one policy with two legitimate
hosts**, and the declaration in `AppConfig` currently states the policy
without enforcing it.

**Two implementations of `POLL` is the intended end state, not a pending
refactor.** The enum is a vocabulary, not a framework: each constant
states what an implementation must promise and says nothing about how.
Sharing the loop was considered and rejected — `DoorRefreshLoop` is
already parameterized on `isVisible` and could take Wear's sources, but
unifying would make the *mechanism* the shared thing, which is the
config-bag trade this design already declined once when it chose three
named strategies over one parameterized loop. It would also mean bending a
screen-scoped VM around an app-scoped utility to deduplicate a `while`
and a `delay`.

**Wear's missing failure backoff is correct — do not add one.** The loop
discards the fetch result and sleeps a fixed 10s, which on the phone would
be a defect. On the watch it is bounded by construction: `onVisible()` /
`onHidden()` are wired to `Lifecycle.Event.ON_START` / `ON_STOP` in
`WearApp.kt`, the app declares no ambient or always-on mode, and
`keepScreenOn` is capped at 15s per trigger. So the loop lives exactly as
long as someone is looking at the watch — seconds to a minute — and a
network outage costs a couple of requests per viewing session, not an
unbounded retry.

Backoff would also work against the watch's whole point. The user is
looking at the screen *right now*; the moment the network recovers is
exactly when they want fresh state, and a backed-off loop would be asleep
for it. The phone backs off because its loop can run for as long as the
app is foregrounded, which is unbounded; the watch's cannot.

(An earlier revision of this document claimed the opposite — that the
watch retried "every 10 seconds indefinitely" and needed fixing. That was
wrong: it assumed a foreground lifetime the watch does not have.)

## 4. Rollout

**Phase 1 — shipped here.** The seam, the flag, the three strategies, the
platform lifecycle wiring, and the developer picker on both platforms.
Android's default is `PUSH`, which is the behavior it has always had:
Android makes no request it was not already making. iOS's default is
`POLL`, which is the first time it has ever updated live.

**Phase 2 — server APNs config (not started).** Add `apns` to the
door-event message in `EventFCM.ts`:
`apns.payload.aps.content-available = 1`, `apns-push-type: background`,
`apns-priority: 5`. Pin the shape with a `wire-contracts/` fixture the way
`fcmDoorEvent` payloads already are, so a unilateral change breaks a test
on at least one side. This is an **additive** change to a message Android
already ignores the unknown parts of — but it is on the path that carries
every Android device's updates, so it wants its own PR, its own
`server/N`, and a verification that Android still receives events after it
deploys.

**Phase 3 — flip iOS to `PUSH_WITH_FOREGROUND_REFRESH`.** Once Phase 2 is
verified on a real device (the simulator cannot receive real pushes),
change one line in `iosApp/Core/Firebase/AppConfigFactory.swift`. Devices
that never touched the picker follow automatically, which is what
`PLATFORM_DEFAULT` exists for.

The picker makes Phases 2 and 3 testable in either order: a tester can
select "Push only" on iOS before the default moves, and compare.

## 5. Verification

`:usecase` `commonTest` covers the timing on virtual time —
`DoorUpdateStrategyTest` (9) and `DoorUpdateManagerTest` (6). The
load-bearing assertions are the ones about **not** fetching: a strategy
that fetched constantly would satisfy every "did it fetch?" check in the
file. Confirmed with a teeth-check — deleting the `if (!visible)` gate
fails 5 tests.

Two properties worth keeping if these tests are ever rewritten:

- `swappingBackToPushStopsTheRunningPoll` — the property that makes the
  flag safe to flip at runtime.
- `pollStopsWhenTheAppIsNoLongerVisible` — the gate, not merely a slower
  interval.

What tests cannot cover: real push delivery to a device (needs a signed
build and hardware) and iOS's actual throttling behavior. Both belong to
Phase 2's verification, not to this seam.
