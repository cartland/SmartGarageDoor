---
category: plan
status: active
---

# Button Health Architecture

Server-detected, mobile-displayed online/offline indicator for the remote-button ESP32 device. **Not yet implemented.**

## Goal

When the remote-button device hasn't checked in within the last minute, show a small "Remote offline" pill next to the Remote-control section title on the Home tab. Otherwise the UI is unchanged.

## Hard constraints

1. **Zero ESP32 firmware changes.** `httpRemoteButton` is byte-identical to today.
2. **Allowlist-gated end-to-end.** Same `remoteButtonAuthorizedEmails` allowlist as the existing button feature.
3. **Data-only FCM.** Never a system-tray notification.
4. **Door and button concepts stay separate on Android.** Every layer gets its own button-side type.
5. **No regressions to existing patterns.** Follows current ADRs; no shortcuts that "we'll fix later."

## Architectural goal note (informational, out of scope here)

The ideal architecture is **one ViewModel per navigation destination, with all data and actions injected as UseCases.** This design adds new state to the existing `RemoteButtonViewModel` (which is already exempt from ADR-026 as a sub-component aggregator). Migrating the Home tab to a single-screen-VM aggregator is a future PR.

## State machine

States: `UNKNOWN`, `ONLINE`, `OFFLINE`. Persisted server-side in `buttonHealthCurrent/{buildTimestamp}`. Each transition emits one data-only FCM. No-op writes (state unchanged) MUST NOT bump `stateChangedAtSeconds`.

Constants:
- `ONLINE_THRESHOLD_SEC = 60` — fresh-poll cutoff.
- `BOOTSTRAP_GRACE_SEC = 120` — pubsub `UNKNOWN → OFFLINE` requires no record OR last poll older than this (Firestore replication grace).
- `FALLBACK_GRACE_SEC = 600` — pubsub `OFFLINE → ONLINE` only fires when state has been `OFFLINE` for more than this (the trigger had ample time and didn't recover).

| Trigger source | Prior | Condition | New state | FCM |
|---|---|---|---|---|
| Firestore trigger (poll arrived) | any | always | `ONLINE` | only on transition |
| Pubsub (every 10 min) | `ONLINE` | last poll ≤ 60 sec ago | `ONLINE` (no-op) | no |
| Pubsub | `ONLINE` | last poll > 60 sec ago | `OFFLINE` | yes |
| Pubsub | `UNKNOWN` | no record OR last poll > 120 sec ago | `OFFLINE` | yes |
| Pubsub | `UNKNOWN` | fresh poll (recovers from a dropped trigger) | `ONLINE` | yes |
| Pubsub | `OFFLINE` | fresh poll AND OFFLINE for > 600 sec | `ONLINE` | yes |
| Pubsub | `OFFLINE` | otherwise | `OFFLINE` (no-op) | no |

Worst-case OFFLINE-detection latency: ~10 min (pubsub cadence). Trigger-side recovery is sub-second.

## Why a Firestore trigger, not an HTTP-handler modification

The naive design appends health detection to `handleRemoteButtonPoll`. Rejected: a throw becomes a 500 to the device; added Firestore ops eat into the device's HTTP timeout. Switching to an `onWrite` trigger on `remoteButtonRequestAll/{docId}` (the collection the existing handler writes to on every poll) achieves detection without touching the device path. Mirrors the existing `firestoreUpdateEvents` pattern. Trigger failures provably cannot affect the device.

The trigger re-reads `RemoteButtonRequestDatabase.getCurrent()` rather than trusting `change.after.data()`, so a Cloud-Functions retry sees the latest poll instead of a stale event payload. The trigger uses default no-retry policy (no `failurePolicy`), matching `firestoreUpdateEvents`.

## Server design

### Data model

```
buttonHealthCurrent/{buildTimestamp}
buttonHealthAll/{auto-id}        // history; daily retention pubsub sweeps it

Document shape:
  state: 'ONLINE' | 'OFFLINE' | 'UNKNOWN'
  stateChangedAtSeconds: number          // when this state was ENTERED (not bumped on no-op)
  lastObservedPollAtSeconds: number | null
```

### New files

| File | Purpose |
|---|---|
| `database/ButtonHealthDatabase.ts` | Firestore CRUD + `setImpl`/`resetImpl` for tests. Same shape as `RemoteButtonRequestDatabase`. |
| `controller/ButtonHealthInterpreter.ts` | Pure functions: `computeHealthFromLastPoll`, `detectTransition`. |
| `controller/ButtonHealthUpdates.ts` | `handleButtonHealthFromPollWrite(buildTimestamp)` — called by the Firestore trigger; re-reads `RemoteButtonRequestDatabase`. |
| `controller/fcm/ButtonHealthFCM.ts` | `sendForTransition(buildTimestamp, record)`. Pattern matches `EventFCM.ts`. |
| `model/ButtonHealthFcmTopic.ts` | `buildTimestampToButtonHealthFcmTopic` — distinct from door's; handles the URL-encoded button `buildTimestamp` with try/catch. |
| `functions/firestore/ButtonHealth.ts` | `firestoreCheckButtonHealth` — `onWrite` trigger on `remoteButtonRequestAll/{docId}`. No `failurePolicy`. |
| `functions/pubsub/ButtonHealth.ts` | `pubsubCheckButtonHealth` — every 10 min. |
| `functions/http/ButtonHealth.ts` | `httpButtonHealth` — mobile cold-start endpoint. Same auth chain as `handleAddRemoteButtonCommand`. |

### Modified files

| File | Change |
|---|---|
| `controller/DatabaseCleaner.ts` | One-line addition to sweep `buttonHealthAll`. |
| `index.ts` | Three new exports. |

### Topic builder

```typescript
const TOPIC_PREFIX = 'buttonHealth-';

export function buildTimestampToButtonHealthFcmTopic(buildTimestamp: string): string {
  let decoded: string;
  try {
    decoded = decodeURIComponent(buildTimestamp);
  } catch (_err) {
    decoded = buildTimestamp;  // fall back to raw; sanitization makes it safe
  }
  if (decoded.length === 0) {
    throw new Error('buildTimestampToButtonHealthFcmTopic: empty buildTimestamp');
  }
  // Allowed FCM topic chars: [a-zA-Z0-9-_.~%]. Replacement char `.` matches the door builder.
  const sanitized = decoded.replace(/[^a-zA-Z0-9\-_.~%]/g, '.');
  return `${TOPIC_PREFIX}${sanitized}`;
}
```

`ButtonHealthFcmTopicTest` pins format on both server (Mocha) and Android (commonTest), per the FCM safety rule.

### Wire contract

NEW directory `wire-contracts/buttonHealth/`:

```
response_online.json         { buttonState: "ONLINE",  stateChangedAtSeconds: <n>, buildTimestamp: "..." }
response_offline.json        { buttonState: "OFFLINE", stateChangedAtSeconds: <n>, buildTimestamp: "..." }
response_unknown.json        { buttonState: "UNKNOWN", stateChangedAtSeconds: null, buildTimestamp: "..." }
response_unauthorized.json   401
response_forbidden_user.json 403
```

Both server tests and Android Ktor tests load these in strict mode.

### FCM payload (data-only)

```json
{
  "topic": "buttonHealth-<sanitized-buildTimestamp>",
  "data": {
    "buttonState": "ONLINE" | "OFFLINE",
    "stateChangedAtSeconds": "<epoch-seconds>",
    "buildTimestamp": "<original-buildTimestamp>"
  },
  "android": { "priority": "HIGH", "collapse_key": "button_health_update" }
}
```

`UNKNOWN` is server-side only; never sent over FCM. Mobile sees it only via the cold-start endpoint when no record exists.

## Android design

Every layer gets its own button-health type. No shared interfaces with the door.

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
    data class Other(val cause: String) : ButtonHealthError
}

interface ButtonHealthRepository {
    val buttonHealth: StateFlow<LoadingResult<ButtonHealth>>
    suspend fun fetchButtonHealth(): AppResult<ButtonHealth, ButtonHealthError>
    fun applyFcmUpdate(update: ButtonHealth)
}
```

### Data (`data/.../buttonhealth/`)

- `NetworkButtonHealthDataSource` (interface) + `KtorNetworkButtonHealthDataSource` (impl).
- `NetworkButtonHealthRepository` — implements `ButtonHealthRepository`. ADR-008 prefix denotes strategy. All `_state` writes dispatch onto the injected `externalScope` per ADR-019. `fetchButtonHealth()` uses `withContext(externalScope.coroutineContext)` so cancellation by the caller actually cancels the work, and the suspend signature is honored.
- `ButtonHealthFcmRepository` — separate from `DoorFcmRepository`. Methods: `subscribe(buildTimestamp)`, `unsubscribe(buildTimestamp)`. Idempotent.
- `ButtonHealthFcmPayloadParser` — parses `buttonHealth-*` data payloads.

### UseCase (`usecase/.../buttonhealth/`)

```kotlin
class FetchButtonHealthUseCase(private val repo: ButtonHealthRepository) {
    suspend operator fun invoke(): AppResult<ButtonHealth, ButtonHealthError> = repo.fetchButtonHealth()
}

class ApplyButtonHealthFcmUseCase(private val repo: ButtonHealthRepository) {
    operator fun invoke(update: ButtonHealth) = repo.applyFcmUpdate(update)
}

class ComputeButtonHealthDisplayUseCase(
    private val authState: StateFlow<AuthState>,
    private val allowlistAccess: StateFlow<Boolean?>,
    private val buttonHealth: StateFlow<LoadingResult<ButtonHealth>>,
    private val liveClock: LiveClock,
    private val externalScope: CoroutineScope,
) {
    operator fun invoke(): StateFlow<ButtonHealthDisplay> =
        combine(authState, allowlistAccess, buttonHealth, liveClock.tickSeconds) { auth, allowed, health, now ->
            computeDisplay(auth, allowed, health, now)
        }.stateIn(externalScope, SharingStarted.Eagerly, ButtonHealthDisplay.Loading)
}

// Pure function; trivial to unit-test.
internal fun computeDisplay(
    auth: AuthState,
    allowed: Boolean?,
    health: LoadingResult<ButtonHealth>,
    nowSeconds: Long,
): ButtonHealthDisplay = when {
    auth !is AuthState.SignedIn -> ButtonHealthDisplay.Unauthorized
    allowed != true             -> ButtonHealthDisplay.Unauthorized
    health is LoadingResult.Loading -> ButtonHealthDisplay.Loading
    health is LoadingResult.Error   -> ButtonHealthDisplay.Loading   // transient; retry will fix
    health is LoadingResult.Complete -> when (health.data.state) {
        ButtonHealthState.UNKNOWN -> ButtonHealthDisplay.Unknown
        ButtonHealthState.ONLINE  -> ButtonHealthDisplay.Online
        ButtonHealthState.OFFLINE -> ButtonHealthDisplay.Offline(
            durationLabel = formatDurationAgo(health.data.stateChangedAtSeconds, nowSeconds)
        )
    }
}
```

### Subscription manager (`usecase/.../buttonhealth/ButtonHealthFcmSubscriptionManager.kt`)

ADR-015 Manager. App-scoped singleton. Started from `AppStartup` after FCM token registration completes.

```kotlin
class ButtonHealthFcmSubscriptionManager(
    authState: StateFlow<AuthState>,
    allowlistAccess: StateFlow<Boolean?>,
    buttonBuildTimestamp: StateFlow<String?>,   // from ServerConfigRepository — handles config rotation
    private val fcm: ButtonHealthFcmRepository,
    private val fetchButtonHealthUseCase: FetchButtonHealthUseCase,
    private val externalScope: CoroutineScope,
) {
    fun start() {
        externalScope.launch {
            combine(authState, allowlistAccess, buttonBuildTimestamp) { auth, allowed, buildTimestamp ->
                Triple(auth, allowed, buildTimestamp)
            }.distinctUntilChanged().collectLatest { (auth, allowed, buildTimestamp) ->
                // Always unsubscribe from any prior topic before deciding whether to subscribe.
                // Idempotent — Firebase handles unsubscribe-from-not-subscribed gracefully.
                fcm.unsubscribeAll()
                if (auth is AuthState.SignedIn && allowed == true && buildTimestamp != null) {
                    fcm.subscribe(buildTimestamp)
                    fetchButtonHealthUseCase()  // cold-start fetch
                }
            }
        }
    }
}
```

### ViewModel (modify the existing `RemoteButtonViewModel`)

Add one new property. No new VM class:

```kotlin
class RemoteButtonViewModel(
    /* existing params */,
    computeButtonHealthDisplayUseCase: ComputeButtonHealthDisplayUseCase,
) : ViewModel() {
    /* existing state */

    val buttonHealthDisplay: StateFlow<ButtonHealthDisplay> =
        computeButtonHealthDisplayUseCase()
}
```

No new fetch action on the VM — the Manager owns cold-start fetch. User-triggered refresh (if ever wanted) is a future addition.

### Display

```kotlin
sealed interface ButtonHealthDisplay {
    data object Unauthorized : ButtonHealthDisplay              // user not allowlisted
    data object Loading      : ButtonHealthDisplay              // fetch in flight, no result yet
    data object Unknown      : ButtonHealthDisplay              // server returned UNKNOWN
    data object Online       : ButtonHealthDisplay              // server returned ONLINE
    data class  Offline(val durationLabel: String) : ButtonHealthDisplay   // ONLY state that renders the pill
}
```

Five arms, exhaustive `when`. Only `Offline` ever renders the pill.

### UI

NEW `androidApp/.../ui/buttonhealth/RemoteOfflinePill.kt` — stateless Composable taking `display: ButtonHealthDisplay.Offline`. Renders `errorContainer` background, `Icons.Outlined.SignalWifiOff`, text `"Remote offline · {display.durationLabel}"`. TalkBack content description: `"Remote offline, last seen {durationLabel}"`.

Modify `HomeContent.kt` — pill goes in the existing `HomeSection(label = "Remote control", trailing = { ... })` slot. `RemoteButtonContent` itself is unchanged (preserves all `@Preview` callers and screenshot tests):

```kotlin
val display by remoteButtonViewModel.buttonHealthDisplay.collectAsState()

HomeSection(
    label = "Remote control",
    trailing = {
        when (val d = display) {
            is ButtonHealthDisplay.Offline -> RemoteOfflinePill(d)
            ButtonHealthDisplay.Unauthorized,
            ButtonHealthDisplay.Loading,
            ButtonHealthDisplay.Unknown,
            ButtonHealthDisplay.Online -> Unit
        }
    },
) {
    RemoteButtonContent(state = ..., onTap = ...)
}
```

Modify `FCMService.kt` — dispatch by topic prefix:

```kotlin
val topic = remoteMessage.from?.removePrefix("/topics/").orEmpty()
when {
    topic.startsWith("buttonHealth-") ->
        ButtonHealthFcmPayloadParser.parse(data)?.let { applyButtonHealthFcmUseCase(it) }
    else -> existingDoorPayloadDispatch(data)
}
```

Modify `AppStartup.kt` — start `ButtonHealthFcmSubscriptionManager` after FCM token registration.

Modify `AppComponent.kt` — wire as `@Singleton` providers with matching `abstract val` entry points (per `DI_SINGLETON_REQUIREMENTS.md`):
- `abstract val buttonHealthRepository: ButtonHealthRepository`
- `abstract val buttonHealthFcmRepository: ButtonHealthFcmRepository`
- `abstract val buttonHealthFcmSubscriptionManager: ButtonHealthFcmSubscriptionManager`
- Non-`@Singleton` factories for the three UseCases.

## Naming summary

| Concept | Name |
|---|---|
| State enum (server + Android) | `ONLINE`, `OFFLINE`, `UNKNOWN` |
| Timestamp field (wire + Android) | `stateChangedAtSeconds` |
| Topic prefix | `buttonHealth-` |
| Collection | `buttonHealthCurrent` / `buttonHealthAll` |
| Functions | `httpButtonHealth`, `pubsubCheckButtonHealth`, `firestoreCheckButtonHealth` |
| Repository impl | `NetworkButtonHealthRepository` (ADR-008) |
| Display sealed type | `ButtonHealthDisplay.{Unauthorized, Loading, Unknown, Online, Offline}` |
| Pill Composable | `RemoteOfflinePill` |
| User-visible text | `"Remote offline · {durationLabel}"` |

States are connectivity-shaped (ONLINE/OFFLINE) but the feature noun is "health" — leaves room for future health signals (battery, sensor errors) without renaming.

## Safety property

| Function | Device path? |
|---|---|
| `httpRemoteButton` (existing) | YES — unchanged |
| `firestoreCheckButtonHealth` (NEW) | NO — fires async after device's response |
| `pubsubCheckButtonHealth` (NEW) | NO — scheduled |
| `httpButtonHealth` (NEW) | NO — mobile only |
| `pubsubDataRetentionPolicy` (existing, +1 line) | NO — sweeps history collections only |

The device cannot observe whether this feature is deployed. A failure in any new code path has zero effect on the response from `httpRemoteButton`.

## Acceptable failure modes

- **OFFLINE that recovers within 10 min may never reach mobile** (pubsub didn't fire while it was OFFLINE). Per requirements.
- **Trigger-side FCM-send failure may leave mobile briefly stale**. Mobile picks up correct state on next cold-start fetch (typically next app launch). Symmetric with the above.
- **FCM topic subscription is client-side**. A signed-in non-allowlisted user with a custom client could subscribe to `buttonHealth-<buildTimestamp>` and receive ONLINE/OFFLINE state. Allowlist is enforced only at the HTTP cold-start endpoint. Acceptable: the leaked data is "is the device online" — low value, no PII, no control surface.
- **Data-only FCMs are not delivered to force-stopped Android apps**. Cold-start fetch handles this on next app launch.
- **Pubsub or trigger crash during a single run** leaves state stale until the next pubsub run (≤10 min).

## Failure modes designed out

- **Unauthorized user seeing the pill** — `computeDisplay`'s exhaustive `when` routes `!SignedIn` and `!allowed` to `Unauthorized`, which has no path to the pill.
- **Topic format drift between server and Android** — pinned by `ButtonHealthFcmTopicTest` running on both sides.
- **Payload key drift** — pinned by `wire-contracts/buttonHealth/` fixtures consumed in strict mode on both sides.
- **`stateChangedAtSeconds` drifting forward on no-op writes** — `detectTransition` returns "no change" when state matches; no write, no FCM, timestamp preserved.
- **Trigger retry on stale event payload** — trigger re-reads `RemoteButtonRequestDatabase.getCurrent()` rather than trusting `change.after.data()`.
- **Device reflash (new buildTimestamp) leaving mobile subscribed to a stale topic** — Manager observes `buttonBuildTimestamp` from `ServerConfigRepository` and unsubscribes from old + subscribes to new on rotation.

## Out of scope

- ESP32 firmware changes.
- Modifying any existing function the device calls.
- Refactoring `RemoteButtonViewModel` into a one-VM-per-screen structure (future PR).
- Room caching of button health (state is short-lived; FCM keeps it fresh).
- New server config keys.
- History view or "uptime" metric.

## ADR / convention compliance

- **FCM safety**: adds new topic + new payload keys; existing `FcmTopicTest` and `FcmPayloadParsingTest` stay green; new `ButtonHealthFcmTopicTest` + `ButtonHealthFcmPayloadParsingTest` ship in the same PR.
- **Handler pattern**: `httpButtonHealth` follows pure-core + thin-wrapper convention.
- **Database refactor pattern**: `ButtonHealthDatabase` follows the per-collection module pattern.
- **Config authority**: no new server config keys; reuses existing button config.
- **ADR-008**: `NetworkButtonHealthRepository` (descriptive prefix).
- **ADR-015**: subscription lifecycle in `ButtonHealthFcmSubscriptionManager`.
- **ADR-019**: all repository state mutations dispatched onto `externalScope`.
- **ADR-021/022**: no passthrough VM — derivation lives in `ComputeButtonHealthDisplayUseCase`.
- **ADR-026**: `RemoteButtonContent` (sub-component) takes no new VM dependency; pill placed in `HomeContent`'s `HomeSection` trailing slot.
- **`DI_SINGLETON_REQUIREMENTS`**: every new `@Singleton` provider has a matching `abstract val` entry point.
