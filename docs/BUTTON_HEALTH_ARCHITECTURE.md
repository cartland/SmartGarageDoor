---
category: plan
status: active
---

# Button Health Architecture

Design for a server-detected, mobile-displayed health indicator for the remote-button ESP32 device. **Not yet implemented.** This doc is the canonical design; implementation PRs should reference it.

## Goal

Show the user a small "Button offline" pill next to the Remote-control section title when the remote-button device hasn't checked in recently. Three states (server-side): `ONLINE`, `OFFLINE`, `UNKNOWN`. Only `OFFLINE` is visible to the user; the other two states render identically to today's UI.

## Hard constraints

1. **Zero ESP32 firmware changes.** The button device's HTTP behavior must be byte-identical to today.
2. **Device contract preserved.** No change to request shape, response shape, response timing, or failure semantics for any endpoint the device calls. Specifically, `httpRemoteButton` must remain unmodified.
3. **Allowlist-gated end-to-end.** Same `remoteButtonAuthorizedEmails` allowlist that already gates the button feature itself. Non-allowlisted users see no UI change vs. today.
4. **Data-only FCM.** Never send a system-tray notification for this feature.
5. **Door and button concepts stay separate on Android.** No shared interfaces between the door event flow and the button health flow — every layer gets its own button-side type.

## State machine

States: `UNKNOWN`, `ONLINE`, `OFFLINE`. Persisted server-side in `buttonHealthCurrent/{buildTimestamp}`. Each transition emits one data-only FCM.

| Transition | Detected by | Latency |
|---|---|---|
| `UNKNOWN → ONLINE` | Firestore trigger (first poll seen for this `buildTimestamp`) | < 1 sec |
| `OFFLINE → ONLINE` | Firestore trigger (device polls again after being marked offline) | < 1 sec |
| `ONLINE → OFFLINE` | Pubsub (every 10 min, 60-sec freshness threshold) | up to 10 min |
| `UNKNOWN → OFFLINE` | Same pubsub (no record exists, no recent poll) | up to 10 min |
| `OFFLINE → ONLINE` (fallback) | Same pubsub (defense-in-depth in case the trigger missed it) | up to 10 min |

ONLINE threshold: most recent poll within `60 sec`. Worst-case OFFLINE-detection latency: ~10 min (acknowledged tradeoff).

## Why a Firestore trigger, not an HTTP-handler modification

The naive design appends health-detection code to `handleRemoteButtonPoll`. That was rejected because it puts new Firestore reads, new writes, and new FCM sends on the device's request path:

- A throw in any new code becomes a `500` response to the device.
- Each added Firestore op (~50–100ms) eats into the device's HTTP timeout budget — a budget defined in firmware we can't change and haven't measured.

Switching to a Firestore `onWrite` trigger on `remoteButtonRequestAll/{docId}` (the collection the existing handler writes to on every poll) achieves the same detection without touching the device path:

```
Device → httpRemoteButton (UNCHANGED, byte-identical to today)
                ↓
       writes to remoteButtonRequestAll
                ↓ (Firestore onWrite trigger)
       firestoreCheckButtonHealth (NEW)
                ↓
       reads buttonHealthCurrent
       computes transition
       conditionally writes + sends FCM
```

The trigger fires asynchronously after the device's response is already on the wire. Trigger failures, Firestore failures, and FCM failures all have **zero effect** on the response the device receives. This mirrors the existing door-event pattern (`firestoreUpdateEvents` triggers on `updateAll/{docId}` writes).

Cost: ~17,000 trigger invocations/day at the button's polling cadence. Cloud Functions invocations + Firestore reads ≈ $0.50/month. Negligible.

## Server design

### Data model

NEW collection, single doc per button device:

```
buttonHealthCurrent/{buildTimestamp}
buttonHealthAll/{auto-id}        // history; daily retention pubsub sweeps it

Document shape:
  state: 'ONLINE' | 'OFFLINE' | 'UNKNOWN'
  stateChangedAtSeconds: number          // when this state was entered (transition timestamp)
  lastObservedPollAtSeconds: number | null
```

### New files

All NEW files; no modification to any existing function file the device calls.

| File | Purpose |
|---|---|
| `database/ButtonHealthDatabase.ts` | Firestore CRUD; `setImpl`/`resetImpl` for tests. Same shape as `RemoteButtonRequestDatabase`. |
| `controller/ButtonHealthInterpreter.ts` | Pure functions: `computeHealthFromLastPoll(lastPollSec, nowSec)` + `detectTransition(prior, computed, nowSec)`. |
| `controller/ButtonHealthUpdates.ts` | `handleButtonHealthFromPollWrite(data)` — called by the Firestore trigger. |
| `controller/fcm/ButtonHealthFCM.ts` | `sendForTransition(buildTimestamp, record)`. Pattern matches `EventFCM.ts`. |
| `model/ButtonHealthFcmTopic.ts` | `buildTimestampToButtonHealthFcmTopic(buildTimestamp)` — distinct from door's topic builder; handles the URL-encoded button `buildTimestamp`. |
| `functions/firestore/ButtonHealth.ts` | `firestoreCheckButtonHealth` — `onWrite` trigger on `remoteButtonRequestAll/{docId}`. Thin wrapper around `handleButtonHealthFromPollWrite`. |
| `functions/pubsub/ButtonHealth.ts` | `pubsubCheckButtonHealth` — every 10 min. Detects `ONLINE → OFFLINE` (and fallback `OFFLINE → ONLINE`). |
| `functions/http/ButtonHealth.ts` | `httpButtonHealth` — mobile cold-start endpoint. Same auth chain as `handleAddRemoteButtonCommand` (push key + Google ID token + email allowlist). |

### Modified files (all changes are isolated from the device path)

| File | Change |
|---|---|
| `controller/DatabaseCleaner.ts` | One-line addition to sweep `buttonHealthAll` in the existing daily-midnight retention pubsub. |
| `index.ts` | Three new exports: `firestoreCheckButtonHealth`, `pubsubCheckButtonHealth`, `httpButtonHealth`. |

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

The button `buildTimestamp` is stored URL-encoded in server config (since April 2021 — see `CLAUDE.md` "Dormant config readers"). The door's topic builder doesn't handle this, so the button needs its own:

```typescript
const TOPIC_PREFIX = 'buttonHealth-';

export function buildTimestampToButtonHealthFcmTopic(buildTimestamp: string): string {
  const decoded = decodeURIComponent(buildTimestamp);  // idempotent for plain ASCII
  const sanitized = decoded.replace(/[^a-zA-Z0-9_\-\.~]/g, '_');
  return `${TOPIC_PREFIX}${sanitized}`;
}
```

`ButtonHealthFcmTopicTest` pins the format on both sides per the FCM safety rule in `CLAUDE.md`.

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
- `DefaultButtonHealthRepository` — owns `MutableStateFlow<LoadingResult<ButtonHealth>>`. `fetchButtonHealth()` always force-refreshes. FCM updates dispatch through `applyFcmUpdate`.
- `ButtonHealthFcmRepository` — separate from `DoorFcmRepository`. Different topic prefix, different lifecycle (subscribe gated by allowlist; door isn't).
- `ButtonHealthFcmPayloadParser` — parses `buttonHealth-*` data payloads. Contract test mirrors `FcmPayloadParsingTest`.

### UseCase (`usecase/.../buttonhealth/`)

- `FetchButtonHealthUseCase`, `ObserveButtonHealthUseCase`
- `ButtonHealthViewModel` — combines `AuthState + AllowlistAccess + ButtonHealth` → exposes a sealed display:

```kotlin
sealed interface ButtonHealthDisplay {
    data object Unauthorized : ButtonHealthDisplay   // user not allowlisted — no pill
    data object Online : ButtonHealthDisplay         // healthy / known good — no pill
    data object Loading : ButtonHealthDisplay        // unknown / no data yet — no pill
    data class Offline(val sinceLabel: String) : ButtonHealthDisplay   // pill shown
}
```

The four-arm sealed type preserves *why* the pill is hidden — debuggable, and resilient to future UX iterations on the non-Offline states.

### UI

- NEW `androidApp/.../ui/buttonhealth/ButtonOfflinePill.kt` — stateless Composable. `errorContainer` background, `Icons.Outlined.SignalWifiOff`, "Button offline" text. Mirrors the structure of `TitleBarCheckInPill` (the device check-in pill shipped in 2.9.4) but is a separate file — no shared base.
- Modify `RemoteButtonContent.kt`:

```kotlin
val display = viewModel { component.buttonHealthViewModel }.display.collectAsState()
// In the section header row, right-aligned next to the title:
when (val d = display.value) {
    is ButtonHealthDisplay.Offline -> ButtonOfflinePill()
    ButtonHealthDisplay.Unauthorized,
    ButtonHealthDisplay.Online,
    ButtonHealthDisplay.Loading -> Unit  // Render nothing — same as today.
}
```

- Modify `androidApp/.../fcm/FCMService.kt` — dispatch by topic prefix:

```kotlin
when {
    topic.startsWith("buttonHealth-") ->
        ButtonHealthFcmPayloadParser.parse(data)?.let { buttonHealthRepository.applyFcmUpdate(it) }
    else -> existingDoorPayloadParser(data)
}
```

- Modify `AppStartup.kt` — new task observes (auth state + allowlist) and calls `buttonHealthFcmRepository.subscribe(buildTimestamp)` when both are true, `.unsubscribe(buildTimestamp)` when either flips false. Cold-start `fetchButtonHealth()` fires once when the user first becomes allowlisted in this app session.
- Modify `AppComponent.kt` — wire `ButtonHealthRepository`, `ButtonHealthFcmRepository`, `ButtonHealthViewModel`.

### Subscription lifecycle

| Event | Action |
|---|---|
| User signs in AND allowlist returns `true` | Subscribe to `buttonHealth-<buttonBuildTimestamp>`; cold-start fetch |
| User signs out | Unsubscribe; clear state |
| Allowlist flips to `false` mid-session | Unsubscribe; flip display to `Unauthorized` |

## Naming convention summary

| Concept | Name |
|---|---|
| State enum | `ONLINE`, `OFFLINE`, `UNKNOWN` |
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
| Display sealed type | `ButtonHealthDisplay.Unauthorized` / `.Online` / `.Loading` / `.Offline(sinceLabel)` |
| Pill Composable | `ButtonOfflinePill` |
| User-visible text | "Button offline" |

The states are connectivity-shaped (`ONLINE`/`OFFLINE`) but the feature noun is "health" (`ButtonHealth*`, `buttonHealth-` topic). This deliberately leaves room for future health signals (battery, sensor errors) without renaming the present feature, while keeping current state names matching the user-visible "offline" string.

## Safety property

Every server change is in a function file the device does not touch:

| Function | Device path? | Notes |
|---|---|---|
| `httpRemoteButton` (existing) | YES | Unchanged — same response shape, same Firestore ops, same latency |
| `httpAddRemoteButtonCommand` (existing) | NO | Mobile only |
| `firestoreCheckButtonHealth` (NEW) | NO | Fires asynchronously after the device's response is sent |
| `pubsubCheckButtonHealth` (NEW) | NO | Runs on schedule |
| `httpButtonHealth` (NEW) | NO | Mobile only |
| `pubsubDataRetentionPolicy` (existing, modified one line) | NO | Doesn't touch device-written collections |

**The device cannot observe whether this feature is deployed.** A failure in any new code path — Firestore trigger crash, Firestore read failure, FCM send failure, malformed health record — has zero effect on the response the device receives from `httpRemoteButton`. Worst case: the indicator stays stale until the next pubsub run; the button itself works exactly as today.

## Acceptable failure modes

- A `OFFLINE` state that recovers within 10 min may never reach mobile (pubsub didn't fire while it was OFFLINE). User stated this is acceptable.
- FCM delivery failures are not retried — mobile re-fetches on next cold start. Eventually consistent.
- A pubsub run that crashes mid-flight may leave state stale until the next run — bounded by 10-min cadence.

## Failure modes designed out

- **Unauthorized user seeing the pill** — eliminated by the four-arm sealed display: `Unauthorized` and `Online` and `Loading` all render no pill, and the rendering site has no way to express "show pill to non-`Offline`" state.
- **Topic format drift between server and Android** — pinned by `ButtonHealthFcmTopicTest` running on both sides.
- **Payload key drift** — pinned by `wire-contracts/buttonHealth/` fixtures consumed by tests in strict mode on both sides.

## Out of scope

- ESP32 firmware changes.
- Modifications to existing door event flow, existing button command flow, or existing pubsubs.
- Room caching of button health (state is short-lived; FCM keeps it fresh; cold-start endpoint reseeds in <1s).
- New server config keys.
- History view or "uptime" metric — `buttonHealthAll` is for diagnostics if ever needed.
- Visible system-tray notifications.

## Versioning impact

- **Android**: minor bump (new user-visible capability — offline indicator). Add `distribution/whatsnew/` line and `CHANGELOG.md` entry.
- **Firebase server**: minor entry in `FirebaseServer/CHANGELOG.md` covering the new Firestore trigger, pubsub, HTTP endpoint, and collection.

## CLAUDE.md compliance

- **FCM safety rule**: this feature *adds* a new topic format and new payload keys; existing `FcmTopicTest` and `FcmPayloadParsingTest` stay green. New contract tests (`ButtonHealthFcmTopicTest`, `ButtonHealthFcmPayloadParsingTest`) ship in the same PR as the topic builder and parser.
- **Handler pattern**: new HTTP handler follows the pure `handle<Action>(input)` core + thin wrapper convention from `docs/FIREBASE_HANDLER_PATTERN.md`.
- **Database refactor pattern**: new `ButtonHealthDatabase` follows the per-collection module pattern from `docs/FIREBASE_DATABASE_REFACTOR.md` (interface + Firestore impl + `setImpl`/`resetImpl`).
- **Config authority**: no new server config keys; reuses existing button config (`remoteButtonBuildTimestamp`, `remoteButtonPushKey`, `remoteButtonAuthorizedEmails`).
