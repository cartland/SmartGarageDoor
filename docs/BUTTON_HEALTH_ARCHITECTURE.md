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
| Pubsub (every 1 min) | last poll ≤ 60 sec ago | `ONLINE` | only on transition |
| Pubsub (every 1 min) | last poll > 60 sec ago OR no poll record | `OFFLINE` | only on transition |

No-op writes (state unchanged) MUST NOT bump `stateChangedAtSeconds` and MUST NOT send FCM. Worst-case `OFFLINE`-detection latency: ~1 min (tightened from 10 min on 2026-05-10). Trigger-side recovery is sub-second.

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
response_online.json         { buttonState: "ONLINE",  stateChangedAtSeconds: <n>,    buildTimestamp: "...", lastPollAtSeconds: <n> }
response_offline.json        { buttonState: "OFFLINE", stateChangedAtSeconds: <n>,    buildTimestamp: "...", lastPollAtSeconds: <n> }
response_unknown.json        { buttonState: "UNKNOWN", stateChangedAtSeconds: null,   buildTimestamp: "...", lastPollAtSeconds: null }
response_unauthorized.json   401
response_forbidden_user.json 403
```

`lastPollAtSeconds` is the unix-seconds timestamp of the most recent device poll the server had observed when the response was assembled. Computed fresh from `RemoteButtonRequestDatabase.getCurrent()` rather than persisted in `buttonHealthCurrent` — avoids ~17K writes/day to one doc just to keep a freshness counter, at the cost of one extra Firestore read per cold-start fetch (negligible).

`UNKNOWN` only appears on the wire when the server has no doc for that `buildTimestamp` (cold-start before first poll seen). Android side: `KtorButtonHealthDataSourceTest` (in `data/src/commonTest/.../buttonhealth/`) loads these in strict mode (`ignoreUnknownKeys = false`); production decode stays `ignoreUnknownKeys = true`. Mocha-side test loads the same fixtures for `httpButtonHealth`.

### FCM payload (data-only)

```json
{
  "topic": "buttonHealth-<sanitized-buildTimestamp>",
  "data": {
    "buttonState": "ONLINE" | "OFFLINE",
    "stateChangedAtSeconds": "<epoch-seconds>",
    "buildTimestamp": "<original-buildTimestamp>",
    "lastPollAtSeconds": "<epoch-seconds>"
  },
  "android": { "priority": "HIGH", "collapse_key": "button_health_update" }
}
```

`UNKNOWN` is never sent over FCM. `lastPollAtSeconds` is omitted from the data payload when null (bootstrap edge: device has never polled, but pubsub flipped to OFFLINE anyway). Mobile parser treats missing key as null.

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

## Release plan

### Order of operations
1. **Server first** (`server/N+1`). Deploy trigger + pubsub + HTTP endpoint + FCM service in one Firebase release. Verify in production via Cloud Logs that pubsub fires and computes the expected state for the live `buttonBuildTimestamp`. **No mobile users see anything change** — old clients don't subscribe to the new FCM topic and don't call the new HTTP endpoint.
2. **Android second** (`android/N+1`, minor bump per CHANGELOG semver — adds a user-visible capability). Ships through Play Store internal track first per `scripts/release-android.sh`, then production.

Server-first is mandatory because Android cold-start expects the HTTP endpoint to exist. The reverse order causes new Android installs to sit in `Loading` indefinitely (the cold-start fetch returns 404), but with no UI regression — the pill never renders. So even if order is flipped accidentally, no user sees anything wrong.

### Kill switch (no new config key)

Every new server function (`firestoreCheckButtonHealth`, `pubsubCheckButtonHealth`, `httpButtonHealth`) MUST short-circuit when `isRemoteButtonEnabled(config) == false`. This reuses the existing button-feature kill switch — one toggle disables everything button-related. Mobile clients see the same state as "server before deploy": no FCMs, HTTP returns 400 `Disabled`, display stays `Loading`/`Unknown` (no pill).

### Forward compat (new Android, server not yet deployed OR rolled back)

- HTTP cold-start endpoint returns 404 (or 400 `Disabled`) → repository surfaces `Network`/`Forbidden` error → display stays `Loading` → no pill.
- FCM topic has no publisher → no updates arrive → display unchanged.
- **UI identical to today.** No spinner, no error message, no broken affordance.

### Backward compat (old Android, server fully deployed)

- Old Android doesn't subscribe to `buttonHealth-*` topic → receives no FCMs.
- Old Android doesn't call `httpButtonHealth` → no 404s.
- Old Android doesn't import `ButtonHealthFcmPayloadParser` → topic-prefix dispatch (when shipped later) is the new-side concern only.
- **No regression.**

### Schema evolution forward compat (future-proofing)

- **`buttonState` is `String` on the wire**, mapped to Android `ButtonHealthState` enum at the data layer. Unrecognized strings default to `UNKNOWN`. Future server-added states (e.g., `MAINTENANCE`) won't crash old Android — they'll just see `Unknown`.
- **`ignoreUnknownKeys = true`** in production decode → server can add new fields to the response without breaking old clients.
- **Strict-mode tests** (`ignoreUnknownKeys = false`) consume `wire-contracts/buttonHealth/` fixtures → catch breaking renames or removals at PR time, not in production.
- **Topic format MUST NOT change post-launch** (CLAUDE.md FCM safety rule). `ButtonHealthFcmTopicTest` pins it on both sides. If a future change is genuinely needed, follow the door-topic dance: add the new topic alongside, migrate, then retire the old.
- **`stateChangedAtSeconds` is nullable** on the wire (null only when wire `state == UNKNOWN`). Future Android versions can rely on it being non-null when state is ONLINE/OFFLINE.

### Rollback

**Server rollback is two-tier:**
1. **Soft kill** (instant, no deploy): set `isRemoteButtonEnabled = false` in `httpServerConfigUpdate`. All button + button-health functions short-circuit. Mobile sees the "no pill ever" state.
2. **Hard rollback** (full deploy): `./scripts/release-firebase.sh --check` prints the rollback command (per existing convention). Reverts to `server/N` (pre-feature). The `buttonHealthCurrent` collection persists harmlessly; can be deleted manually if desired (no other code references it after rollback).

**Android rollback** is standard Play Store: roll back to `android/N`. The previous version doesn't subscribe to the new topic or call the new endpoint, so there's no stale-subscription cleanup needed (Firebase tolerates orphan topic subscriptions indefinitely).

### Risks during rollout

- **First production pubsub run after deploy** writes `OFFLINE` if no doc exists and no fresh poll is recorded. Cloud Logs should show `OFFLINE` then `ONLINE` within ~10 sec as the next device poll arrives. If it stays `OFFLINE`, the device is genuinely offline OR the trigger isn't firing — investigate via the existing `firestoreUpdateEvents` runbook.
- **Cost**: trigger fires ~17K/day. Within Cloud Functions free tier. Verify the org-level Firestore-read budget hasn't drifted since last estimate.
- **FCMService dispatcher modification** is the highest-risk Android change — a bug here could break the existing door FCM path for all users. Mitigation: door dispatch stays as the `else` branch (default-preserving); `FcmTopicDispatcherTest` pins both branches; manual smoke-test on internal track with a real door event before promoting to production.

### Per-PR rollout split (recommended order)

Following the PR-shape review:

1. `wire(contracts): button health response fixtures` — JSON only, ~30 lines, no consumers yet. Unblocks server + Android tests.
2. `feat(firebase): button health database + interpreter + FCM topic + FCM service` — pure modules + tests. No `index.ts` change. Dead code, harmless.
3. `feat(firebase): firestoreCheckButtonHealth trigger` — adds `index.ts` export. Live trigger; passive OFFLINE detection only (no FCM yet because no subscribers).
4. `feat(firebase): pubsubCheckButtonHealth (10-min sweep)` — adds `index.ts` export. Sequence after PR 3 (same file).
5. `feat(firebase): httpButtonHealth cold-start endpoint` — adds `index.ts` export. Sequence after PR 4. **End of server-side; ship `server/N+1` after this lands.**
6. `feat(android-domain+data): button health types + repo + datasource + parser + topic test` — dead code on Android side until DI wiring lands.
7. `feat(android-usecase): button health UseCases + FCM subscription manager` — still dead code.
8. `ui(android): RemoteOfflinePill + previews + screenshot test` — UI-only, satisfies preview-coverage check, can't regress production.
9. `feat(android): wire button health into AppComponent + AppStartup + HomeContent + FCMService` — **highest-risk PR**. Manual smoke-test on internal track; do not auto-merge to production.

PRs 1, 2, 6 can land in parallel (no shared files). PRs 3/4/5 must serialize on `index.ts`. PRs 7/8 can land in parallel. PR 9 is the user-visible flip and the only one that can affect existing FCM behavior.

## Test plan

Every new module ships with tests in the same PR. Each test layer has a precedent file in the codebase — implementer should mirror the existing pattern.

### Server tests (Mocha + Chai, fakes via `setImpl`/`resetImpl`)

| Test file | What it covers | Convention |
|---|---|---|
| `test/controller/ButtonHealthInterpreterTest.ts` | Pure functions: `computeHealthFromLastPoll` (4 cases: null poll, fresh ≤60s, boundary at 60s, stale >60s) + `detectTransition` (2 priors × 2 computed = 4 cases; verify `stateChangedAtSeconds` not bumped on no-op) | Pattern: `EventInterpreterTest.ts` |
| `test/controller/ButtonHealthUpdatesTest.ts` | `handleButtonHealthFromPollWrite` with fakes for `ButtonHealthDatabase`, `RemoteButtonRequestDatabase`, `ButtonHealthFCMService`. Verifies trigger re-reads `getCurrent()` (test the stale-event-payload case explicitly: pass an old payload, expect logic to use the fresh re-read) | Pattern: `EventUpdatesFakeTest.ts` |
| `test/controller/fcm/ButtonHealthFCMTest.ts` | `sendForTransition` payload shape: data-only, correct topic via builder, `collapse_key`, no `notification` block | Pattern: `EventFCMTest.ts` |
| `test/model/ButtonHealthFcmTopicTest.ts` | Topic format pinning. Cases: empty input throws; `"100%25"` re-decode throws → fallback to raw; known-good buildTimestamp produces known-good topic; sanitization replaces forbidden chars with `.` | Pattern: `FcmTopicTest.ts` |
| `test/database/ButtonHealthDatabaseTest.ts` | CRUD round-trip via fake. Verifies `getCurrent` returns null when no doc, `save` then `getCurrent` round-trips. | Pattern: `RemoteButtonRequestDatabaseTest.ts` |
| `test/functions/firestore/ButtonHealthTest.ts` | Trigger handler integration: fakes wired end-to-end. Asserts on `database.writeCount` and `fcm.sendCount` per scenario. | Pattern: `EventsTriggerTest.ts` (door equivalent) |
| `test/functions/pubsub/ButtonHealthTest.ts` | **MUST enumerate the state-machine table rows 1:1 by name.** Three tests, named `"row 1: pubsub fresh poll → ONLINE no-op"`, `"row 2: pubsub fresh poll, prior OFFLINE → ONLINE + FCM"`, `"row 3: pubsub stale poll OR no record → OFFLINE + FCM (only on transition)"`. Reading test file alongside doc table verifies completeness. | New convention; doc is the spec |
| `test/functions/http/ButtonHealthTest.ts` | Auth chain mirrors `handleAddRemoteButtonCommand`: missing push key → 401; wrong push key → 403; missing token → 401; bad token → 500; non-allowlisted email → 403; missing buildTimestamp → 400. Plus: success cases deep-equal against `wire-contracts/buttonHealth/response_*.json` fixtures (loaded in strict mode). | Pattern: existing handler tests |

### Android tests (Kotlin, JUnit, fakes only — no Mockito per ADR-003)

| Test file | What it covers | Convention |
|---|---|---|
| `domain/src/commonTest/.../buttonhealth/ButtonHealthDisplayLogicTest.kt` | Pure `compute()`. Truth table: 5 display states × all relevant input combos. ~12 cases minimum: not-SignedIn → Unauthorized (3 sub-cases); allowlist null → Unauthorized; allowlist false → Unauthorized; Loading → Loading; Error → Loading; Complete×ONLINE → Online; Complete×OFFLINE → Offline(label); Complete×UNKNOWN → Unknown | Pure-function tests, no fakes |
| `domain/src/commonTest/.../buttonhealth/ButtonHealthDurationFormatterTest.kt` | Format strings: just-now, minutes, hours, days, negative (clock skew). Pin exact strings. | Pure |
| `data/src/commonTest/.../buttonhealth/KtorButtonHealthDataSourceTest.kt` | Loads `wire-contracts/buttonHealth/*.json` in strict mode (`ignoreUnknownKeys = false`). Each variant decodes to the expected `ButtonHealth`. Unknown `buttonState` string maps to `ButtonHealthState.UNKNOWN` (not a throw). 401 + 403 surface as `Forbidden`/`Network` errors. | Pattern: existing wire-contract test |
| `data/src/commonTest/.../buttonhealth/ButtonHealthFcmPayloadParserTest.kt` | Parses data-only FCM payloads. Tests: known-good payload → `ButtonHealth`; missing field → null (or `Unknown` mapping per design); unknown `buttonState` string → `ButtonHealthState.UNKNOWN`. | Pattern: `FcmPayloadParsingTest.kt` |
| `data/src/commonTest/.../buttonhealth/ButtonHealthFcmTopicTest.kt` | **MUST pin format identically to server `ButtonHealthFcmTopicTest.ts`** — same input/output pairs. Catches drift between server topic builder and Android topic-string assumption. | Pattern: `FcmTopicTest.kt` |
| `data/src/commonTest/.../buttonhealth/NetworkButtonHealthRepositoryTest.kt` | Fake `NetworkButtonHealthDataSource` returns canned `NetworkResult`. Asserts `repository.buttonHealth.value` after `fetchButtonHealth()` and `applyFcmUpdate()`. Uses `runTest` with `backgroundScope` as `externalScope` (matches `NetworkSnoozeRepositoryTest`). | Pattern: `NetworkSnoozeRepositoryTest.kt` |
| `data/src/commonTest/.../fcm/FcmTopicDispatcherTest.kt` | Pure dispatcher: `isButtonHealth("buttonHealth-X") == true`, `isButtonHealth("door-X") == false`, `isButtonHealth("") == false`, `isButtonHealth("buttonHealth") == false` (no trailing `-`). | Pure |
| `usecase/src/commonTest/.../buttonhealth/ComputeButtonHealthDisplayUseCaseTest.kt` | Wires fake `AuthRepository`, `FeatureAccessRepository`, `ButtonHealthRepository`, `LiveClock`. Emits on `MutableStateFlow`s, asserts on collected display values. Verifies `combine` re-emits on every input change. | Pattern: `ObserveAuthStateUseCaseTest.kt` |
| `usecase/src/commonTest/.../buttonhealth/ButtonHealthFcmSubscriptionManagerTest.kt` | Fake `ButtonHealthFcmRepository` (records `subscribe`/`unsubscribe`/`unsubscribeAll` calls + counts) + fake `FetchButtonHealthUseCase` (records invocation count). Scenarios: signed-out → only `unsubscribeAll`; signed-in + allowed + bt → `unsubscribeAll` then `subscribe(bt)` then `fetch`; buildTimestamp rotates → `unsubscribeAll` then `subscribe(new)` (the always-unsubscribe-first pattern); allowlist flip → unsubscribe. Uses `runTest` + `backgroundScope`. | Pattern: `FcmRegistrationManagerTest.kt` |
| `androidApp/src/screenshotTest/.../buttonhealth/RemoteOfflinePillPreviewTest.kt` | Imports `RemoteOfflinePillFreshPreview`, `RemoteOfflinePillAgingPreview` (light + dark variants). Required by `checkPreviewCoverage` (build fails otherwise). | Pattern: `TitleBarCheckInPillPreviewTest.kt` |
| `androidApp/src/test/.../ComponentGraphTest.kt` | Add identity-`assertSame` checks for `buttonHealthRepository`, `buttonHealthFcmRepository`, `buttonHealthFcmSubscriptionManager` — verifies `@Singleton` providers are properly cached per `DI_SINGLETON_REQUIREMENTS.md`. | Existing test file, just append |
| `androidApp/src/androidTest/.../AppStartupInstrumentedTest.kt` | If touched: verify Manager starts after FCM token registration. Per CLAUDE.md, instrumented tests required when AppComponent/AppStartup change. | Pattern: existing instrumented tests |

### What CANNOT be tested without running the app

- **Real FCM delivery** to a device. Mitigation: cold-start fetch on next launch.
- **FCM topic subscription persistence** across app restarts. Mitigation: Manager re-subscribes on every state-change cycle (idempotent).
- **Layoutlib pill rendering** in screenshot tests can fail silently with custom 960-viewport vectors (CLAUDE.md gotcha). Mitigation: use Material `Icons.Outlined.SignalWifiOff` (24-viewport, known-good).
- **Real Firestore trigger firing** under production load. Mitigation: pattern matches `firestoreUpdateEvents` which already runs in production.

### Manual verification (per release)

**Internal-track (`android/N+1`) smoke test before promoting to production:**

1. **Allowlisted user, device online**: open app → no pill. Verify in Cloud Logs that cold-start `httpButtonHealth` returned `ONLINE` and FCM topic subscription succeeded.
2. **Force OFFLINE**: unplug ESP32 button device. Wait ~10 min for pubsub to fire. Verify pill appears with realistic duration label.
3. **Recovery**: plug ESP32 back in. Within 1 sec of next poll, the trigger should fire → FCM → pill disappears.
4. **Sign out**: pill clears immediately (Manager unsubscribes; display flips to `Unauthorized`).
5. **Sign back in**: cold-start fetch → display returns to correct state within seconds.
6. **Non-allowlisted user**: sign in with non-allowlisted account. Verify no pill ever; verify FCM topic NOT subscribed (Cloud Logs); verify `httpButtonHealth` returns 403.
7. **Door FCM regression check** (highest-risk PR #9 only): trigger a door state change and verify the door UI still updates via FCM. Confirms the dispatcher's `else` branch is intact.

**Pre-deploy gates:**
- `./scripts/validate.sh` — full pre-submit (includes `checkPreviewCoverage`, `checkSingletonCaching`, `checkNoBareTopLevelFunctions`, `checkNoImplSuffix`, `checkScreenViewModelCardinality`).
- `./scripts/validate-firebase.sh` — Firebase tests + lint.
- `./scripts/run-instrumented-tests.sh` — required when AppComponent / AppStartup / Activity lifecycle code changes (PR #9 triggers this).

**Post-deploy verification (production server):**
- Cloud Logs: `firestoreCheckButtonHealth` fires on every poll (cadence ~5 sec).
- Cloud Logs: `pubsubCheckButtonHealth` fires every 10 min.
- Firestore Console: `buttonHealthCurrent/{buildTimestamp}` doc exists with current state.
- No ERROR-level logs from new functions in the first 24 hours.

**Coverage targets:**
- Pure functions (`computeHealthFromLastPoll`, `detectTransition`, `compute`, `formatAgo`, topic builder, dispatcher): 100% line + branch.
- Repositories + Manager: 100% public surface; covers all sealed-class arms.
- Wire-contract tests cover all 5 fixture variants.

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
