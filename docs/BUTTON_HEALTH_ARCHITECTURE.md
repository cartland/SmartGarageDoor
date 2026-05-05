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
5. **Strict ADR compliance.** Every new file matches existing patterns; no shortcuts.

## Architectural goal note (informational)

The ideal architecture is **one ViewModel per navigation destination, with all data and actions injected as UseCases.** This design adds new state to the existing `RemoteButtonViewModel` (already exempt from ADR-026 as a sub-component aggregator). Migrating Home to a single-screen-VM aggregator is a future PR.

## State machine

Persisted server-side: **`ONLINE` | `OFFLINE`** only. (`UNKNOWN` is a presentation concept on the wire/Android — see "HTTP endpoint" below.)

One constant: `ONLINE_THRESHOLD_SEC = 60`.

| Trigger source | Condition | New persisted state | FCM |
|---|---|---|---|
| Firestore trigger (poll arrived) | always | `ONLINE` | only on transition |
| Pubsub (every 10 min) | last poll ≤ 60 sec ago | `ONLINE` | only on transition |
| Pubsub (every 10 min) | last poll > 60 sec ago OR no poll record | `OFFLINE` | only on transition |

No-op writes (state unchanged) MUST NOT bump `stateChangedAtSeconds` and MUST NOT send FCM. Worst-case `OFFLINE`-detection latency: ~10 min. Trigger-side recovery is sub-second.

## Why a Firestore trigger, not an HTTP-handler modification

The naive design appends health detection to `handleRemoteButtonPoll`. Rejected: a throw becomes a 500 to the device; added Firestore ops eat into the device's HTTP timeout. The trigger fires on `remoteButtonRequestAll/{docId}` (the collection the existing handler writes to on every poll), asynchronously after the device's response is on the wire. Trigger failures provably cannot affect the device. Mirrors `firestoreUpdateEvents`.

The trigger re-reads `RemoteButtonRequestDatabase.getCurrent()` rather than trusting `change.after.data()` — Cloud Functions retry triggers with the original event payload, which can become stale. Trigger uses default no-retry policy (no `failurePolicy`).

## Server design

### Data model

```
buttonHealthCurrent/{buildTimestamp}

Document shape:
  state: 'ONLINE' | 'OFFLINE'
  stateChangedAtSeconds: number    // when this state was ENTERED (not bumped on no-op)
```

No history collection (`buttonHealthAll`). Cloud Logs cover any actual diagnostic need.

### New files

| File | Purpose |
|---|---|
| `database/ButtonHealthDatabase.ts` | Firestore CRUD + `setImpl`/`resetImpl`. Same shape as `RemoteButtonRequestDatabase` minus `*All`. |
| `controller/ButtonHealthInterpreter.ts` | Pure functions: `computeHealthFromLastPoll`, `detectTransition`. |
| `controller/ButtonHealthUpdates.ts` | `handleButtonHealthFromPollWrite(buildTimestamp)` — called by the trigger; re-reads `RemoteButtonRequestDatabase`. |
| `controller/fcm/ButtonHealthFCM.ts` | `SERVICE.sendForTransition(buildTimestamp, record)` with module-level `_instance` + `setImpl`/`resetImpl`. Pattern matches `EventFCM.ts`. |
| `model/ButtonHealthFcmTopic.ts` | `buildTimestampToButtonHealthFcmTopic` — distinct from door's; handles URL-encoded button `buildTimestamp` with try/catch. |
| `functions/firestore/ButtonHealth.ts` | `firestoreCheckButtonHealth` — `onWrite` trigger on `remoteButtonRequestAll/{docId}`. No `failurePolicy`. |
| `functions/pubsub/ButtonHealth.ts` | `pubsubCheckButtonHealth` — every 10 min. |
| `functions/http/ButtonHealth.ts` | `httpButtonHealth` — mobile cold-start. Same auth chain as `handleAddRemoteButtonCommand`. Maps "no doc" → `state: UNKNOWN` on the wire. |

### Modified files

| File | Change |
|---|---|
| `index.ts` | Three new exports. |

No `DatabaseCleaner.ts` change (no `*All` history collection to sweep).

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
  // Allowed FCM topic chars: [a-zA-Z0-9-_.~%]. Replacement `.` matches the door builder.
  const sanitized = decoded.replace(/[^a-zA-Z0-9\-_.~%]/g, '.');
  return `${TOPIC_PREFIX}${sanitized}`;
}
```

`ButtonHealthFcmTopicTest` pins format on both server (Mocha) and Android (commonTest). Test cases: empty input throws, malformed `decodeURIComponent` falls back, known-good inputs produce known-good topics.

### Wire contract

NEW directory `wire-contracts/buttonHealth/`:

```
response_online.json         { buttonState: "ONLINE",  stateChangedAtSeconds: <n>,    buildTimestamp: "..." }
response_offline.json        { buttonState: "OFFLINE", stateChangedAtSeconds: <n>,    buildTimestamp: "..." }
response_unknown.json        { buttonState: "UNKNOWN", stateChangedAtSeconds: null,   buildTimestamp: "..." }
response_unauthorized.json   401
response_forbidden_user.json 403
```

`UNKNOWN` only appears on the wire when the server has no doc for that `buildTimestamp` (cold-start before first poll seen). Android side: `KtorButtonHealthDataSourceTest` (in `data/src/commonTest/.../buttonhealth/`) loads these in strict mode (`ignoreUnknownKeys = false`); production decode stays `ignoreUnknownKeys = true`. Mocha-side test loads the same fixtures for `httpButtonHealth`.

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

`UNKNOWN` is never sent over FCM.

### Pubsub test convention

`pubsubCheckButtonHealthTest` MUST enumerate the three state-table rows 1:1 with descriptive names (e.g., `"row 1: pubsub fresh poll → ONLINE + FCM only on transition"`). Reading the test file alongside the doc table verifies completeness in seconds.

## Android design

Every layer gets its own button-health type. No shared interfaces with door.

### Domain (`domain/.../buttonhealth/`)

```kotlin
enum class ButtonHealthState { UNKNOWN, ONLINE, OFFLINE }   // UNKNOWN appears only when wire returns it

data class ButtonHealth(
    val state: ButtonHealthState,
    val stateChangedAtSeconds: Long?,    // null when state == UNKNOWN
)

sealed interface ButtonHealthError {
    data object Network : ButtonHealthError
    data object Forbidden : ButtonHealthError
}

interface ButtonHealthRepository {
    val buttonHealth: StateFlow<LoadingResult<ButtonHealth>>
    suspend fun fetchButtonHealth(): AppResult<ButtonHealth, ButtonHealthError>
    fun applyFcmUpdate(update: ButtonHealth)
}
```

### Data (`data/.../buttonhealth/`)

```kotlin
@Serializable
internal data class KtorButtonHealthResponse(
    @SerialName("buttonState") val buttonState: String,
    @SerialName("stateChangedAtSeconds") val stateChangedAtSeconds: Long? = null,
    @SerialName("buildTimestamp") val buildTimestamp: String,
)
```

Files:
- `NetworkButtonHealthDataSource` (interface) + `KtorNetworkButtonHealthDataSource` (impl). Returns `NetworkResult<ButtonHealth>`.
- `NetworkButtonHealthRepository` — implements `ButtonHealthRepository`. ADR-008 prefix denotes strategy. All `_state` writes dispatched onto injected `externalScope` per ADR-019 (matches `NetworkSnoozeRepository`'s `externalScope.launch { ... }.join()` pattern; preserves the suspend signature).
- `ButtonHealthFcmRepository` — separate from `DoorFcmRepository`. Methods: `subscribe(buildTimestamp)`, `unsubscribe(buildTimestamp)`, `unsubscribeAll()`. Idempotent.
- `ButtonHealthFcmPayloadParser` — parses `buttonHealth-*` data payloads.

ADR-020 (R8): the new `@Serializable` data classes are covered by the existing generic `proguard-rules.pro` rule (`@kotlinx.serialization.Serializable class **`). Verify against release build before tagging.

### UseCase (`usecase/.../buttonhealth/`)

```kotlin
class FetchButtonHealthUseCase(private val repo: ButtonHealthRepository) {
    suspend operator fun invoke(): AppResult<ButtonHealth, ButtonHealthError> = repo.fetchButtonHealth()
}

class ApplyButtonHealthFcmUseCase(private val repo: ButtonHealthRepository) {
    operator fun invoke(update: ButtonHealth) = repo.applyFcmUpdate(update)
}

class ComputeButtonHealthDisplayUseCase(
    private val authRepository: AuthRepository,
    private val featureAccessRepository: FeatureAccessRepository,    // existing pattern (FunctionList allowlist)
    private val buttonHealthRepository: ButtonHealthRepository,
    private val liveClock: LiveClock,
) {
    operator fun invoke(): Flow<ButtonHealthDisplay> =
        combine(
            authRepository.authState,
            featureAccessRepository.access(Feature.RemoteButton),
            buttonHealthRepository.buttonHealth,
            liveClock.tickSeconds,
        ) { auth, allowed, health, now ->
            ButtonHealthDisplayLogic.compute(auth, allowed, health, now)
        }
}
```

Returns `Flow`, not `StateFlow` (ADR-022: no `stateIn(viewModelScope, ...)`; UseCase doesn't need to share state across collectors). Composable collects via `collectAsStateWithLifecycle(initialValue = ButtonHealthDisplay.Loading)`.

### Display sealed type

```kotlin
sealed interface ButtonHealthDisplay {
    data object Unauthorized : ButtonHealthDisplay              // user not allowlisted
    data object Loading      : ButtonHealthDisplay              // fetch in flight, no result yet
    data object Unknown      : ButtonHealthDisplay              // server has no doc yet
    data object Online       : ButtonHealthDisplay              // server says ONLINE
    data class  Offline(val durationLabel: String) : ButtonHealthDisplay   // ONLY arm that renders the pill
}
```

### Pure derivation logic (ADR-009: grouped in an `object`)

```kotlin
internal object ButtonHealthDisplayLogic {
    fun compute(
        auth: AuthState,
        allowed: Boolean?,
        health: LoadingResult<ButtonHealth>,
        nowSeconds: Long,
    ): ButtonHealthDisplay {
        // Pre-conditions as guard clauses (not enum-shaped, not exhaustive-when):
        if (auth !is AuthState.SignedIn) return ButtonHealthDisplay.Unauthorized
        if (allowed != true)             return ButtonHealthDisplay.Unauthorized

        // Sealed-type exhaustive when on LoadingResult — compiler-enforced completeness.
        return when (health) {
            is LoadingResult.Loading  -> ButtonHealthDisplay.Loading
            is LoadingResult.Error    -> ButtonHealthDisplay.Loading   // transient; retry will fix
            is LoadingResult.Complete -> when (health.data.state) {
                ButtonHealthState.UNKNOWN -> ButtonHealthDisplay.Unknown
                ButtonHealthState.ONLINE  -> ButtonHealthDisplay.Online
                ButtonHealthState.OFFLINE -> ButtonHealthDisplay.Offline(
                    durationLabel = ButtonHealthDurationFormatter.formatAgo(
                        health.data.stateChangedAtSeconds, nowSeconds,
                    ),
                )
            }
        }
    }
}
```

### Subscription manager (ADR-015)

```kotlin
class ButtonHealthFcmSubscriptionManager(
    private val authRepository: AuthRepository,
    private val featureAccessRepository: FeatureAccessRepository,
    private val serverConfigRepository: ServerConfigRepository,
    private val fcm: ButtonHealthFcmRepository,
    private val fetchButtonHealthUseCase: FetchButtonHealthUseCase,
    private val externalScope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    fun start() {
        externalScope.launch(dispatcher) {
            combine(
                authRepository.authState,
                featureAccessRepository.access(Feature.RemoteButton),
                serverConfigRepository.buttonBuildTimestamp,    // handles config rotation
            ) { auth, allowed, buildTimestamp -> Triple(auth, allowed, buildTimestamp) }
                .distinctUntilChanged()
                .collectLatest { (auth, allowed, buildTimestamp) ->
                    // Always unsubscribe first — idempotent. Avoids "did Firebase Task cancel?" question.
                    fcm.unsubscribeAll()
                    if (auth is AuthState.SignedIn && allowed == true && buildTimestamp != null) {
                        fcm.subscribe(buildTimestamp)
                        fetchButtonHealthUseCase()    // cold-start fetch
                    }
                }
        }
    }
}
```

Started from `AppStartup` after FCM token registration completes.

### ViewModel (modify existing `RemoteButtonViewModel`)

```kotlin
class RemoteButtonViewModel(
    /* existing params */,
    computeButtonHealthDisplayUseCase: ComputeButtonHealthDisplayUseCase,
) : ViewModel() {
    /* existing state */
    val buttonHealthDisplay: Flow<ButtonHealthDisplay> = computeButtonHealthDisplayUseCase()
}
```

### UI

NEW `androidApp/.../ui/buttonhealth/RemoteOfflinePill.kt` — stateless Composable taking `display: ButtonHealthDisplay.Offline`. Renders `errorContainer` background, `Icons.Outlined.SignalWifiOff` (Material 24-viewport — avoids the Layoutlib gotcha documented in CLAUDE.md), text `"Remote offline · {display.durationLabel}"`. TalkBack `contentDescription = "Remote offline, last seen {durationLabel}"`. Has `@Preview` variants imported by a screenshot test (preview-coverage check requires this).

Modify `HomeContent.kt` — pill goes in the existing `HomeSection(label = "Remote control", trailing = { ... })` slot. `RemoteButtonContent` itself is unchanged (preserves all `@Preview` callers and screenshot tests):

```kotlin
val display by remoteButtonViewModel.buttonHealthDisplay
    .collectAsStateWithLifecycle(initialValue = ButtonHealthDisplay.Loading)

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

### FCMService dispatcher (extracted for testability)

Today's `FCMService` does parsing inline. Extract the topic-prefix dispatch to a pure function in `commonTest`-reachable code:

```kotlin
// in data/.../fcm/FcmTopicDispatcher.kt
internal object FcmTopicDispatcher {
    fun isButtonHealth(topic: String): Boolean = topic.startsWith("buttonHealth-")
    // door dispatcher already implicit; add isXxx helpers if other features want extraction.
}
```

`FCMService.kt`:

```kotlin
val topic = remoteMessage.from?.removePrefix("/topics/").orEmpty()
when {
    FcmTopicDispatcher.isButtonHealth(topic) ->
        ButtonHealthFcmPayloadParser.parse(data)?.let { applyButtonHealthFcmUseCase(it) }
    else -> existingDoorPayloadDispatch(data)
}
```

`FcmTopicDispatcherTest` in `commonTest`. Existing door FCM path remains the `else` branch (default-preserving).

### AppStartup + AppComponent

Modify `AppStartup.kt` — start `ButtonHealthFcmSubscriptionManager` after FCM token registration.

Modify `AppComponent.kt` — wire as `@Singleton` providers with matching `abstract val` entry points (per `DI_SINGLETON_REQUIREMENTS.md`):
- `abstract val buttonHealthRepository: ButtonHealthRepository`
- `abstract val buttonHealthFcmRepository: ButtonHealthFcmRepository`
- `abstract val buttonHealthFcmSubscriptionManager: ButtonHealthFcmSubscriptionManager`
- Non-`@Singleton` factories for the three UseCases.

## Naming summary

| Concept | Name |
|---|---|
| Server enum | `ONLINE`, `OFFLINE` (UNKNOWN exists only on wire/Android) |
| Android enum | `ButtonHealthState.{ONLINE, OFFLINE, UNKNOWN}` |
| Timestamp field (wire + Android) | `stateChangedAtSeconds` |
| Topic prefix | `buttonHealth-` |
| Collection | `buttonHealthCurrent` |
| Functions | `httpButtonHealth`, `pubsubCheckButtonHealth`, `firestoreCheckButtonHealth` |
| Repository impl | `NetworkButtonHealthRepository` (ADR-008 prefix) |
| Display sealed type | `ButtonHealthDisplay.{Unauthorized, Loading, Unknown, Online, Offline}` |
| Pure derivation | `object ButtonHealthDisplayLogic { fun compute(...) }` |
| Pill Composable | `RemoteOfflinePill` |
| User-visible text | `"Remote offline · {durationLabel}"` |

## Safety property

| Function | Device path? |
|---|---|
| `httpRemoteButton` (existing) | YES — unchanged |
| `firestoreCheckButtonHealth` (NEW) | NO — async after device's response |
| `pubsubCheckButtonHealth` (NEW) | NO — scheduled |
| `httpButtonHealth` (NEW) | NO — mobile only |

The device cannot observe whether this feature is deployed. A failure in any new code path has zero effect on the response from `httpRemoteButton`.

## Acceptable failure modes

- **OFFLINE that recovers within 10 min may never reach mobile.** Per requirements.
- **Trigger-side FCM-send failure may leave mobile briefly stale.** Cold-start fetch handles this on next app launch.
- **FCM topic subscription is client-side.** A signed-in non-allowlisted user with a custom client could subscribe to `buttonHealth-<buildTimestamp>` and receive ONLINE/OFFLINE state. Allowlist is enforced only at the HTTP cold-start endpoint. Acceptable: the leaked data is "is the device online" — low value, no PII, no control surface.
- **Data-only FCMs are not delivered to force-stopped Android apps.** Cold-start fetch handles this on next app launch.

## Failure modes designed out

- **Unauthorized user seeing the pill** — `ButtonHealthDisplayLogic.compute` returns `Unauthorized` before any health-state branch; only `Offline` ever renders the pill.
- **Topic format drift** — pinned by `ButtonHealthFcmTopicTest` on both sides.
- **Payload key drift** — pinned by `wire-contracts/buttonHealth/` fixtures consumed in strict mode on both sides.
- **`stateChangedAtSeconds` drifting forward on no-op writes** — `detectTransition` returns "no change" when state matches; no write, no FCM, timestamp preserved.
- **Trigger retry on stale event** — trigger re-reads `RemoteButtonRequestDatabase.getCurrent()`.
- **Device reflash (new buildTimestamp) leaving mobile subscribed to a stale topic** — Manager observes `buttonBuildTimestamp` from `ServerConfigRepository`; `unsubscribeAll()` runs on every state change before the conditional subscribe.
- **Display-state completeness drift** — `compute` uses sealed-type exhaustive `when` over `LoadingResult` and `ButtonHealthState`; the compiler enforces every arm.

## Out of scope

- ESP32 firmware changes.
- Modifying any existing function the device calls.
- Refactoring `RemoteButtonViewModel` into a one-VM-per-screen structure (future PR).
- Room caching of button health.
- New server config keys.
- History / uptime metrics.

## ADR / convention compliance

- **FCM safety**: adds new topic + payload keys; existing `FcmTopicTest` and `FcmPayloadParsingTest` stay green; new `ButtonHealthFcmTopicTest` + `ButtonHealthFcmPayloadParsingTest` ship in the same PR as the topic builder + parser.
- **Handler pattern**: `httpButtonHealth` follows pure-core + thin-wrapper.
- **Database refactor pattern**: `ButtonHealthDatabase` follows the per-collection module pattern.
- **Config authority**: no new server config keys.
- **ADR-008**: `NetworkButtonHealthRepository` (descriptive prefix).
- **ADR-009**: pure derivation lives in `object ButtonHealthDisplayLogic`, not a bare top-level function (`checkNoBareTopLevelFunctions` enforces).
- **ADR-015**: subscription lifecycle in `ButtonHealthFcmSubscriptionManager`, started from `AppStartup`.
- **ADR-019**: all repository state mutations dispatched onto `externalScope` (matches `NetworkSnoozeRepository`).
- **ADR-020**: new `@Serializable` data classes covered by the existing generic `proguard-rules.pro` rule.
- **ADR-021/022**: no passthrough VM; no `stateIn(viewModelScope, ...)`; UseCase exposes `Flow`, Composable collects via `collectAsStateWithLifecycle`.
- **ADR-026**: `RemoteButtonContent` (sub-component) takes no new VM dependency; pill placed in `HomeContent`'s `HomeSection` trailing slot.
- **`DI_SINGLETON_REQUIREMENTS`**: every new `@Singleton` provider has a matching `abstract val` entry point.
- **Test-mirroring-table convention** (this design): `pubsubCheckButtonHealthTest` enumerates the state-table rows 1:1 by name.
