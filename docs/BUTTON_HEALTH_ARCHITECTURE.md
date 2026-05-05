---
category: plan
status: active
---

# Button Health Architecture

Design for a server-detected, mobile-displayed health indicator for the remote-button ESP32 device. **Not yet implemented.** This doc is the canonical design; implementation PRs should reference it.

## Goal

Show the user a small "Remote offline" pill next to the Remote-control section title when the remote-button device hasn't checked in recently. Three states (server-side): `ONLINE`, `OFFLINE`, `UNKNOWN`. Only `OFFLINE` is visible to the user; the other two states render identically to today's UI.

## Hard constraints

1. **Zero ESP32 firmware changes.** The button device's HTTP behavior must be byte-identical to today.
2. **Device contract preserved.** No change to request shape, response shape, response timing, or failure semantics for any endpoint the device calls. Specifically, `httpRemoteButton` must remain unmodified.
3. **Allowlist-gated end-to-end.** Same `remoteButtonAuthorizedEmails` allowlist that already gates the button feature itself. Non-allowlisted users see no UI change vs. today.
4. **Data-only FCM.** Never send a system-tray notification for this feature.
5. **Door and button concepts stay separate on Android.** No shared interfaces between the door event flow and the button health flow — every layer gets its own button-side type.

## Architectural goal note (out of scope, for future work)

The ideal architecture for any screen is **one ViewModel per navigation destination, with all data and actions injected as UseCases.** This design intentionally does **not** refactor toward that ideal — the existing `RemoteButtonViewModel` is already consumed by `HomeContent` (a sub-component aggregator screen exempt from ADR-026), and pulling that apart belongs in its own PR. This design adds new state to the existing `RemoteButtonViewModel` via a new injected UseCase. Future cleanup: collapse the per-sub-component VMs into one screen-level VM that aggregates the same UseCases.

## State machine

States: `UNKNOWN`, `ONLINE`, `OFFLINE`. Persisted server-side in `buttonHealthCurrent/{buildTimestamp}`. Each *transition* emits one data-only FCM. **No-op writes (state unchanged) MUST NOT bump `stateChangedAtSeconds`** — the UI's "Offline since X" label depends on `stateChangedAtSeconds` being the moment the state was *entered*, not the most recent observation.

| Event | Detected by | New state | Fires FCM | Latency |
|---|---|---|---|---|
| Poll arrives, prior state was `UNKNOWN` | Trigger | `ONLINE` | yes | < 1 sec |
| Poll arrives, prior state was `OFFLINE` | Trigger | `ONLINE` | yes | < 1 sec |
| Poll arrives, prior state was `ONLINE` | Trigger | `ONLINE` (no-op, no write) | no | n/a |
| Pubsub finds last-poll > 60 sec ago, prior `ONLINE` | Pubsub | `OFFLINE` | yes | up to 10 min |
| Pubsub finds NO record + last-poll > 2 min ago, prior `UNKNOWN` | Pubsub | `OFFLINE` | yes | up to 10 min |
| Pubsub finds last-poll < 60 sec ago, prior `OFFLINE` for >10 min (fallback) | Pubsub | `ONLINE` | yes | up to 10 min |
| Pubsub finds last-poll < 60 sec ago, prior `OFFLINE` for <10 min | Pubsub | (no action — trigger should have already handled) | no | n/a |
| Pubsub finds last-poll fresh, prior `ONLINE` | Pubsub | `ONLINE` (no-op, no write) | no | n/a |
| Pubsub finds last-poll fresh, prior `OFFLINE` for >10 min | Pubsub | `ONLINE` | yes | (this is the fallback row above) |

ONLINE threshold: most recent poll within `60 sec` (steady state). Bootstrap grace: `UNKNOWN → OFFLINE` requires last-poll older than `120 sec` (or no record), guarding against Firestore replication lag where the pubsub reads `RemoteButtonRequestDatabase` before a fresh first-deploy poll has propagated. Pubsub fallback grace: `OFFLINE → ONLINE` from pubsub only fires if state has been `OFFLINE` for more than 10 min (i.e., the trigger had ample time to recover and didn't), avoiding duplicate FCMs when trigger and pubsub race on the same fresh poll. Worst-case `OFFLINE`-detection latency: ~10 min (acknowledged tradeoff).

## Why a Firestore trigger, not an HTTP-handler modification

The naive design appends health-detection code to `handleRemoteButtonPoll`. That was rejected because it puts new Firestore reads, new writes, and new FCM sends on the device's request path:

- A throw in any new code becomes a `500` response to the device.
- Each added Firestore op (~50–100ms) eats into the device's HTTP timeout budget — a budget defined in firmware we can't change and haven't measured.

Switching to a Firestore `onWrite` trigger on `remoteButtonRequestAll/{docId}` (the collection the existing handler writes to on every poll) achieves the same detection without touching the device path:

```
Device → httpRemoteButton (UNCHANGED, byte-identical to today)
                ↓
       writes to remoteButtonRequestAll
                ↓ (Firestore onWrite trigger; default no-retry policy)
       firestoreCheckButtonHealth (NEW)
                ↓
       re-reads RemoteButtonRequestDatabase.getCurrent() (NOT the event payload — see below)
       reads buttonHealthCurrent
       computes transition
       conditionally writes + sends FCM
```

The trigger fires asynchronously after the device's response is already on the wire. Trigger failures, Firestore failures, and FCM failures all have **zero effect** on the response the device receives. This mirrors the existing door-event pattern (`firestoreUpdateEvents` triggers on `updateAll/{docId}` writes).

**Critical: trigger must re-read `RemoteButtonRequestDatabase.getCurrent()` rather than trust `change.after.data()`.** Cloud Functions retry triggers with the *original* event payload, which can become stale by the time the retry runs. A retry of an old poll could otherwise incorrectly compute `OFFLINE` despite the device having polled successfully many times since.

**Critical: trigger MUST NOT enable `runWith({failurePolicy: true})`.** Default is no-retry, matching `firestoreUpdateEvents`. Retried instances on a buggy trigger could pile up; cost spike, no device impact.

Cost: ~17,000 trigger invocations/day at the button's polling cadence. Cloud Functions invocations + Firestore reads ≈ $0.50/month *marginal* (verify total Firestore-read budget across all features before declaring negligible at the org level).

## Server design

### Data model

NEW collection, single doc per button device:

```
buttonHealthCurrent/{buildTimestamp}
buttonHealthAll/{auto-id}        // history; daily retention pubsub sweeps it

Document shape:
  state: 'ONLINE' | 'OFFLINE' | 'UNKNOWN'
  stateChangedAtSeconds: number          // when this state was ENTERED (NOT bumped on no-op)
  lastObservedPollAtSeconds: number | null
```

### New files

All NEW files; no modification to any existing function file the device calls.

| File | Purpose |
|---|---|
| `database/ButtonHealthDatabase.ts` | Firestore CRUD; `setImpl`/`resetImpl` for tests. Same shape as `RemoteButtonRequestDatabase`. |
| `controller/ButtonHealthInterpreter.ts` | Pure functions: `computeHealthFromLastPoll(lastPollSec, nowSec)` (clamped: returns `ONLINE` if `lastPollSec > nowSec` to handle clock skew) + `detectTransition(prior, computed, nowSec)` — never bumps `stateChangedAtSeconds` on no-op. |
| `controller/ButtonHealthUpdates.ts` | `handleButtonHealthFromPollWrite(buildTimestamp)` — called by the Firestore trigger. Re-reads `RemoteButtonRequestDatabase` (does NOT trust the event payload). |
| `controller/fcm/ButtonHealthFCM.ts` | `sendForTransition(buildTimestamp, record)`. Pattern matches `EventFCM.ts` (`SERVICE` exported object indirection, module-level `let _instance`, `setImpl`/`resetImpl`). |
| `model/ButtonHealthFcmTopic.ts` | `buildTimestampToButtonHealthFcmTopic(buildTimestamp)` — distinct from door's topic builder; handles the URL-encoded button `buildTimestamp` with try/catch. |
| `functions/firestore/ButtonHealth.ts` | `firestoreCheckButtonHealth` — `onWrite` trigger on `remoteButtonRequestAll/{docId}`. Thin wrapper around `handleButtonHealthFromPollWrite`. **No `failurePolicy`.** |
| `functions/pubsub/ButtonHealth.ts` | `pubsubCheckButtonHealth` — every 10 min. Detects `ONLINE → OFFLINE`, `UNKNOWN → OFFLINE` (with 120-sec grace), and fallback `OFFLINE → ONLINE` (only when state has been OFFLINE for >10 min). |
| `functions/http/ButtonHealth.ts` | `httpButtonHealth` — mobile cold-start endpoint. Same auth chain as `handleAddRemoteButtonCommand` (push key + Google ID token + email allowlist). |

### Modified files (all changes are isolated from the device path)

| File | Change |
|---|---|
| `controller/DatabaseCleaner.ts` | One-line addition to sweep `buttonHealthAll` in the existing daily-midnight retention pubsub. |
| `index.ts` | Three new exports: `firestoreCheckButtonHealth`, `pubsubCheckButtonHealth`, `httpButtonHealth`. |

### Race-condition mitigation

Trigger and pubsub can race; both can read `buttonHealthCurrent`, compute, and write concurrently. Mitigations:

- **Last-write-wins on `buttonHealthCurrent`** is acceptable for the doc itself — the state it records will converge to truth on the next event.
- **Duplicate FCM avoidance** comes from three places combined:
  1. The `OFFLINE → ONLINE` pubsub fallback only fires when state has been `OFFLINE` for >10 min (so a fresh trigger-write of `ONLINE` won't be undone by a concurrent pubsub).
  2. The trigger always re-reads `RemoteButtonRequestDatabase.getCurrent()` so a Cloud-Functions retry sees the latest poll, not a stale event.
  3. The bootstrap grace window (120 sec) prevents a first-deploy pubsub from racing the first-poll trigger.
- **Mobile must be idempotent on receive.** `applyFcmUpdate(update)` overwrites with the full state. If the same FCM lands twice, the result is the same. The display does not blink.
- A small race window remains where flapping is possible (trigger writes ONLINE, pubsub *just before its fallback grace window* writes nothing — fine; trigger writes ONLINE, slow pubsub still finishes its OFFLINE write — possible. Mobile sees pill flash on, then off within seconds. Acceptable: the underlying state did flap; the display reflects reality.

### Wire contract

NEW directory `wire-contracts/buttonHealth/`:

```
response_online.json        { buttonState: "ONLINE",  stateChangedAtSeconds: 1730000000, buildTimestamp: "..." }
response_offline.json       { buttonState: "OFFLINE", stateChangedAtSeconds: 1730000000, buildTimestamp: "..." }
response_unknown.json       { buttonState: "UNKNOWN", stateChangedAtSeconds: null,        buildTimestamp: "..." }
response_unauthorized.json  { error: "Unauthorized (token)." }                    // 401
response_forbidden_user.json { error: "Forbidden (user)." }                       // 403
```

Both server tests and Android Ktor tests load these in strict mode. Production decoding stays `ignoreUnknownKeys = true` for forward-compat.

### FCM payload

```json
{
  "topic": "buttonHealth-<sanitized-buttonBuildTimestamp>",
  "data": {
    "buttonState": "ONLINE" | "OFFLINE",
    "stateChangedAtSeconds": "1730000000",
    "buildTimestamp": "<original-buttonBuildTimestamp>"
  },
  "android": {
    "priority": "HIGH",
    "collapse_key": "button_health_update"
  }
  // No `notification` block — data-only by design.
}
```

`UNKNOWN` is never sent over FCM; it's a server-side bootstrap state only. Mobile sees ONLINE or OFFLINE via FCM, and may see UNKNOWN only as the cold-start HTTP response when no record exists yet.

### Topic builder

The button `buildTimestamp` is stored URL-encoded in server config (since April 2021 — see `CLAUDE.md` "Dormant config readers"). The door's topic builder doesn't handle this, so the button needs its own. The character set and replacement char must match the door's builder so a future agent reading both sees one rule:

```typescript
const TOPIC_PREFIX = 'buttonHealth-';

export function buildTimestampToButtonHealthFcmTopic(buildTimestamp: string): string {
  // Match Firebase's allowed topic char set [a-zA-Z0-9-_.~%].
  // Replacement char `.` matches the door builder; do NOT diverge.
  let decoded: string;
  try {
    decoded = decodeURIComponent(buildTimestamp);  // throws on malformed input
  } catch (_err) {
    decoded = buildTimestamp;  // fall back to raw string; sanitization below makes it safe
  }
  if (decoded.length === 0) {
    throw new Error('buildTimestampToButtonHealthFcmTopic: empty buildTimestamp');
  }
  const sanitized = decoded.replace(/[^a-zA-Z0-9\-_.~%]/g, '.');
  return `${TOPIC_PREFIX}${sanitized}`;
}
```

`ButtonHealthFcmTopicTest` pins the format on both sides per the FCM safety rule in `CLAUDE.md`. The test must include:
- `decodeURIComponent`-throws case (e.g., `"100%25"` re-decoded → `"100%"` then attempted re-decode throws; verify fallback path).
- Empty-input case (must throw).
- A "two different inputs that would collide" check (must produce different topics for inputs that differ in non-sanitized chars).

## Android design

Every layer gets its own button-health type. No reuse of door interfaces.

### Domain (`domain/.../buttonhealth/`)

```kotlin
enum class ButtonHealthState { UNKNOWN, ONLINE, OFFLINE }

data class ButtonHealth(
    val state: ButtonHealthState,
    val stateChangedAtSeconds: Long?,
)

sealed interface ButtonHealthError {
    data object Network : ButtonHealthError
    data object Forbidden : ButtonHealthError
    data class Unknown(val cause: String) : ButtonHealthError
}

interface ButtonHealthRepository {
    val buttonHealth: StateFlow<LoadingResult<ButtonHealth>>
    suspend fun fetchButtonHealth(): AppResult<ButtonHealth, ButtonHealthError>
    fun applyFcmUpdate(update: ButtonHealth)
}
```

### Data (`data/.../buttonhealth/`)

- `NetworkButtonHealthDataSource` (interface) + `KtorNetworkButtonHealthDataSource` (impl) — its own data source, returns `NetworkResult<ButtonHealth>`.
- `NetworkButtonHealthRepository` — owns `MutableStateFlow<LoadingResult<ButtonHealth>>`. Implements `ButtonHealthRepository`. Name uses the "Network" prefix per ADR-008 (not `Default`, since the strategy is clearly network-backed). All state mutations dispatch onto the injected `externalScope` per ADR-019 — `fetchButtonHealth()` and `applyFcmUpdate()` both `externalScope.launch { _state.value = ... }` so caller cancellation can never strand the singleton.
- **FCM-vs-fetch ordering rule:** `applyFcmUpdate(update)` AND the `fetchButtonHealth()` success path BOTH check `update.stateChangedAtSeconds >= current.stateChangedAtSeconds` before writing. A stale fetch result that lands after a fresher FCM is discarded. (`UNKNOWN` is treated as oldest — any non-UNKNOWN result wins over a current UNKNOWN regardless of timestamp.)
- `ButtonHealthFcmRepository` — separate from `DoorFcmRepository`. Different topic prefix, different lifecycle (subscribe gated by allowlist; door isn't). Methods: `subscribe(buildTimestamp)`, `unsubscribe(buildTimestamp)`. Subscription is idempotent (FCM SDK handles).
- `ButtonHealthFcmPayloadParser` — parses `buttonHealth-*` data payloads. Contract test mirrors `FcmPayloadParsingTest`.

### UseCase (`usecase/.../buttonhealth/`)

- `FetchButtonHealthUseCase`, `ObserveButtonHealthUseCase` — thin wrappers around the repository.
- `ComputeButtonHealthDisplayUseCase` — returns `StateFlow<ButtonHealthDisplay>` derived from `combine(authState, allowlistAccess, buttonHealthLoadingResult)`. **No new ViewModel.** This UseCase is the derivation; consumed by the existing `RemoteButtonViewModel`.
- `ButtonHealthFcmSubscriptionManager` (ADR-015 Manager) — observes `combine(authState, allowlistAccess)` with `collectLatest` semantics so a sign-out in flight cancels an in-progress subscribe. Owns the `subscribe` / `unsubscribe` calls plus retry. App-scoped singleton, started from `AppStartup`.

### ViewModel (modify the existing `RemoteButtonViewModel`)

Add a new property to the existing `RemoteButtonViewModel`, fed by `ComputeButtonHealthDisplayUseCase`:

```kotlin
class RemoteButtonViewModel(
    // ...existing constructor params...
    computeButtonHealthDisplayUseCase: ComputeButtonHealthDisplayUseCase,
    fetchButtonHealthUseCase: FetchButtonHealthUseCase,
) : ViewModel() {

    // ...existing remote-button state...

    val buttonHealthDisplay: StateFlow<ButtonHealthDisplay> =
        computeButtonHealthDisplayUseCase()  // already a StateFlow from the UseCase

    fun fetchButtonHealth() {
        viewModelScope.launch {
            // Loading is set by the repository when fetchButtonHealth is called
            // (it sets _state.value = LoadingResult.Loading inside externalScope.launch)
            fetchButtonHealthUseCase()  // result lands in repository state
        }
    }
}
```

The `fetchButtonHealth` action follows the user's stated pattern: VM calls the suspend UseCase; the repository sets `Loading` at the start of the call and writes the result on completion. `Loading` and `Unknown` are *different concepts* — `Loading` = the suspend fun is in flight; `Unknown` = the suspend fun returned `UNKNOWN`. Both render no pill but mean different things.

### Display sealed type

```kotlin
sealed interface ButtonHealthDisplay {
    data object Unauthorized : ButtonHealthDisplay              // user not allowlisted — no pill
    data object Loading      : ButtonHealthDisplay              // suspend fun in flight, no result yet — no pill
    data object Unknown      : ButtonHealthDisplay              // server returned UNKNOWN (no data) — no pill
    data object Online       : ButtonHealthDisplay              // server returned ONLINE, fresh — no pill
    data class  Offline(val durationLabel: String) : ButtonHealthDisplay   // ONLY state that renders the pill
}
```

The five-arm sealed type preserves *why* the pill is hidden — `Loading` and `Unknown` look identical to the user but tell a future debugger which path led there.

### `durationLabel` format

`durationLabel` is computed by a pure function (`ButtonOfflineDuration.format(stateChangedAtSeconds, nowSeconds)`) ticked by the existing app-scoped `LiveClock` at 1-second cadence — same pattern as `DeviceCheckIn.format` driving `TitleBarCheckInPill`. Format mirrors the existing pill: `"5 min ago"`, `"11 min ago"`. Keeps both pills speaking the same grammar.

### UI

- NEW `androidApp/.../ui/buttonhealth/RemoteOfflinePill.kt` — stateless Composable taking `display: ButtonHealthDisplay.Offline` (already includes the `durationLabel`). `errorContainer` background, `Icons.Outlined.SignalWifiOff`, text format `"Remote offline · <durationLabel>"` (e.g., `"Remote offline · 11 min ago"`). TalkBack `contentDescription = "Remote offline, last checked in <durationLabel>"`.
- Modify `RemoteButtonContent.kt` — takes a new `buttonHealthDisplay: ButtonHealthDisplay` parameter (NO ViewModel import; complies with ADR-026 sub-component rule):

```kotlin
@Composable
fun RemoteButtonContent(
    state: RemoteButtonState,
    buttonHealthDisplay: ButtonHealthDisplay,  // NEW required parameter
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeSection(
        title = "Remote control",
        trailing = {
            when (val d = buttonHealthDisplay) {
                is ButtonHealthDisplay.Offline -> RemoteOfflinePill(display = d)
                ButtonHealthDisplay.Unauthorized,
                ButtonHealthDisplay.Loading,
                ButtonHealthDisplay.Unknown,
                ButtonHealthDisplay.Online -> Unit  // Render nothing — same as today.
            }
        },
    ) { /* ...existing button... */ }
}
```

- Modify `HomeContent.kt` — collect `RemoteButtonViewModel.buttonHealthDisplay` and pass to `RemoteButtonContent`.
- Modify `androidApp/.../fcm/FCMService.kt` — dispatch by topic prefix:

```kotlin
when {
    topic.startsWith("buttonHealth-") ->
        ButtonHealthFcmPayloadParser.parse(data)?.let { buttonHealthRepository.applyFcmUpdate(it) }
    else -> existingDoorPayloadParser(data)
}
```

- Modify `AppStartup.kt` — start `ButtonHealthFcmSubscriptionManager`. The Manager handles all subscription lifecycle internally with `combine(authState, allowlistAccess) + collectLatest`.
- Modify `AppComponent.kt` — wire as `@Singleton` providers with matching `abstract val` entry points (per `DI_SINGLETON_REQUIREMENTS.md`):
  - `abstract val buttonHealthRepository: ButtonHealthRepository`
  - `abstract val buttonHealthFcmRepository: ButtonHealthFcmRepository`
  - `abstract val buttonHealthFcmSubscriptionManager: ButtonHealthFcmSubscriptionManager`
  - `provideComputeButtonHealthDisplayUseCase`, `provideFetchButtonHealthUseCase` — non-`@Singleton` factories.
  - `RemoteButtonViewModel` provider gains the two new UseCase parameters.

### Subscription lifecycle (encapsulated in `ButtonHealthFcmSubscriptionManager`)

| Event | Action |
|---|---|
| `combine(authState, allowlistAccess)` emits `(SignedIn, Allowed)` | Subscribe to `buttonHealth-<buttonBuildTimestamp>`; trigger one-shot `fetchButtonHealth()` |
| `combine(...)` emits anything else (signed-out, allowlist false) | Unsubscribe; `applyFcmUpdate` becomes a no-op until re-allowed |
| In-flight subscribe + sign-out fires before subscribe completes | `collectLatest` cancels the in-progress subscribe; unsubscribe wins |
| In-flight `fetchButtonHealth()` + sign-out fires before fetch completes | `collectLatest` cancels the fetch; late HTTP response (if any) is discarded by the timestamp gate (UNKNOWN is treated as oldest) and by the auth-check inside `applyFcmUpdate` (no-op when not authorized) |
| `httpButtonHealth` returns 403 (allowlist removed) | Repository surfaces `ButtonHealthError.Forbidden`; ViewModel maps to `Unauthorized` display; Manager unsubscribes |

## Naming convention summary

| Concept | Name |
|---|---|
| State enum (server + Android) | `ONLINE`, `OFFLINE`, `UNKNOWN` |
| Timestamp field (wire + Android) | `stateChangedAtSeconds` |
| FCM payload field for state | `buttonState` |
| Topic prefix | `buttonHealth-` |
| Topic builder | `buildTimestampToButtonHealthFcmTopic` |
| Collection | `buttonHealthCurrent` / `buttonHealthAll` |
| HTTP endpoint | `httpButtonHealth` / `handleButtonHealth` |
| Pubsub | `pubsubCheckButtonHealth` / `handleCheckButtonHealth` |
| Firestore trigger | `firestoreCheckButtonHealth` / `handleButtonHealthFromPollWrite` |
| FCM collapse key | `button_health_update` |
| Domain types | `ButtonHealth`, `ButtonHealthState`, `ButtonHealthError`, `ButtonHealthRepository` |
| Repository impl | `NetworkButtonHealthRepository` (ADR-008 — "Network" prefix denotes strategy) |
| Display sealed type | `ButtonHealthDisplay.Unauthorized` / `.Loading` / `.Unknown` / `.Online` / `.Offline(durationLabel)` |
| UseCases | `FetchButtonHealthUseCase`, `ObserveButtonHealthUseCase`, `ComputeButtonHealthDisplayUseCase` |
| Subscription manager | `ButtonHealthFcmSubscriptionManager` (ADR-015 Manager pattern) |
| Pill Composable | `RemoteOfflinePill` |
| User-visible text | "Remote offline" + `" · <durationLabel>"` (matches "Remote control" section title) |

The states are connectivity-shaped (`ONLINE`/`OFFLINE`) but the feature noun is "health" (`ButtonHealth*`, `buttonHealth-` topic). This deliberately leaves room for future health signals (battery, sensor errors) without renaming the present feature, while keeping current state names matching the user-visible "offline" string.

## Safety property

Every server change is in a function file the device does not touch:

| Function | Device path? | Notes |
|---|---|---|
| `httpRemoteButton` (existing) | YES | Unchanged — same response shape, same Firestore ops, same latency |
| `httpAddRemoteButtonCommand` (existing) | NO | Mobile only |
| `firestoreCheckButtonHealth` (NEW) | NO | Fires asynchronously after the device's response is sent. No `failurePolicy`. |
| `pubsubCheckButtonHealth` (NEW) | NO | Runs on schedule |
| `httpButtonHealth` (NEW) | NO | Mobile only |
| `pubsubDataRetentionPolicy` (existing, modified one line) | NO | Doesn't touch device-written collections |

**The device cannot observe whether this feature is deployed.** A failure in any new code path — Firestore trigger crash, Firestore read failure, FCM send failure, malformed health record — has zero effect on the response the device receives from `httpRemoteButton`. Worst case: the indicator stays stale until the next pubsub run; the button itself works exactly as today.

## Acceptable failure modes

- An `OFFLINE` state that recovers within 10 min may never reach mobile (pubsub didn't fire while it was OFFLINE). User stated this is acceptable.
- FCM delivery failures are not retried — mobile re-fetches on next cold start. Eventually consistent.
- A pubsub run that crashes mid-flight may leave state stale until the next run — bounded by 10-min cadence.
- A brand-new install with a device that has never polled stays in `Loading → Unknown` (no pill) indefinitely. Acceptable per requirements; user has no way to distinguish "Unknown" from "Online" but this is the same as today (today there's no indicator at all).
- During a network failure, `AllowlistAccess` returning the last-known-good value is preferred over flipping to `false` (which would unsubscribe + show `Unauthorized`); the Manager treats network failures as transient and retains the last successful allowlist verdict.
- During a WiFi outage the device-check-in pill (existing, in Status section) AND the remote-offline pill (new) may both render. They reflect independent devices and represent the truth: two devices are unreachable. No coordination needed.

## Failure modes designed out

- **Unauthorized user seeing the pill** — eliminated by the five-arm sealed display: only `Offline` ever renders the pill, and `applyFcmUpdate` is a no-op when display is `Unauthorized` so a late FCM (or stale fetch) cannot leak through.
- **Stale fetch clobbering fresher FCM update** — eliminated by the `stateChangedAtSeconds` timestamp gate in the repository (both `applyFcmUpdate` and `fetchButtonHealth` success path check it).
- **Late fetch landing for signed-out user** — eliminated by `collectLatest` in `ButtonHealthFcmSubscriptionManager` (cancels in-flight fetch on sign-out) plus the auth check in `applyFcmUpdate`.
- **Topic format drift between server and Android** — pinned by `ButtonHealthFcmTopicTest` running on both sides.
- **Payload key drift** — pinned by `wire-contracts/buttonHealth/` fixtures consumed by tests in strict mode on both sides.
- **`stateChangedAtSeconds` drifting forward on no-op writes** — `detectTransition` returns "no change" when state matches; trigger and pubsub do nothing on no-op (no write, no FCM, `stateChangedAtSeconds` preserved).
- **Trigger retry on stale event** — trigger re-reads `RemoteButtonRequestDatabase.getCurrent()` rather than trust `change.after.data()`.
- **Bootstrap race between pubsub and first-poll trigger** — pubsub `UNKNOWN → OFFLINE` requires last-poll older than 120 sec (or no record), giving Firestore replication time to settle.
- **Duplicate `OFFLINE → ONLINE` FCMs from trigger + pubsub fallback** — pubsub fallback only fires when state has been `OFFLINE` for >10 min, so the trigger's recovery path is never duplicated by a contemporaneous pubsub.
- **Topic-builder throw on malformed `buildTimestamp`** — `decodeURIComponent` wrapped in try/catch; falls back to raw string + sanitization.

## Out of scope

- ESP32 firmware changes.
- Modifications to existing door event flow, existing button command flow, or existing pubsubs.
- Refactoring `RemoteButtonViewModel` into a one-VM-per-screen structure (see "Architectural goal note" — that's a future PR).
- Room caching of button health (state is short-lived; FCM keeps it fresh; cold-start endpoint reseeds in <1s).
- New server config keys.
- History view or "uptime" metric — `buttonHealthAll` is for diagnostics if ever needed.
- Visible system-tray notifications.
- Transactional `buttonHealthCurrent` writes — last-write-wins is acceptable (race-condition mitigation lives in the FCM-duplication rules, not in document-write atomicity).

## Versioning impact

- **Android**: minor bump (new user-visible capability — offline indicator). Add `distribution/whatsnew/` line and `CHANGELOG.md` entry.
- **Firebase server**: minor entry in `FirebaseServer/CHANGELOG.md` covering the new Firestore trigger, pubsub, HTTP endpoint, and collection.

## CLAUDE.md compliance

- **FCM safety rule**: this feature *adds* a new topic format and new payload keys; existing `FcmTopicTest` and `FcmPayloadParsingTest` stay green. New contract tests (`ButtonHealthFcmTopicTest`, `ButtonHealthFcmPayloadParsingTest`) ship in the same PR as the topic builder and parser.
- **Handler pattern** (`docs/FIREBASE_HANDLER_PATTERN.md`): new HTTP handler follows the pure `handle<Action>(input)` core + thin wrapper convention.
- **Database refactor pattern** (`docs/FIREBASE_DATABASE_REFACTOR.md`): new `ButtonHealthDatabase` follows the per-collection module pattern (interface + Firestore impl + `setImpl`/`resetImpl`).
- **Config authority** (`docs/FIREBASE_CONFIG_AUTHORITY.md`): no new server config keys; reuses existing button config (`remoteButtonBuildTimestamp`, `remoteButtonPushKey`, `remoteButtonAuthorizedEmails`); pubsub uses `requireBuildTimestamp` to throw on missing config.
- **ADR-008** (no `*Impl` suffix): repository named `NetworkButtonHealthRepository`, not `DefaultButtonHealthRepository`.
- **ADR-015** (app-scoped Managers): subscription lifecycle wrapped in `ButtonHealthFcmSubscriptionManager`, started from `AppStartup`.
- **ADR-019** (repository `externalScope`): all `MutableStateFlow` writes in `NetworkButtonHealthRepository` dispatch onto the injected `externalScope`.
- **ADR-021/022** (no passthrough VMs): no new ViewModel; derivation lives in `ComputeButtonHealthDisplayUseCase` consumed by the existing `RemoteButtonViewModel`.
- **ADR-026** (one VM per screen + sub-components have no VMs): `RemoteButtonContent` takes `buttonHealthDisplay` as a parameter; `HomeContent` collects from `RemoteButtonViewModel` and passes down.
- **DI_SINGLETON_REQUIREMENTS**: every new `@Singleton` provider has a matching `abstract val` entry point in `AppComponent.kt`.
