---
category: plan
status: active
---

# Test Notification Sandbox — implementation plan

**Status: IN PROGRESS** (this doc is the resume point if the session is interrupted).

A diagnostic-gated, **purely additive** Android feature to prototype app-owned
notification infrastructure (app-built notifications, dedicated channel,
`tag`-based inline replace, foreground handling) **in isolation** from the
production door-notification path. Once proven here, the reliable "Resolved:
door was open for X min" feature (and fixes R6/M4 in `NOTIFICATION_RELIABILITY.md`)
can be refactored onto this foundation.

## Scope (v1)

- **Client-only.** No server code. Test notifications are sent **manually** from
  the Firebase console / a script, targeting a personal topic the app displays.
- **Gated** behind the existing `featureFunctionList` flag (lives in the Function
  List screen — no new flag).
- **Zero touch** to `OldDataFCM` / `EventFCM` / the door path.

## Architecture

### Repository owns the "≤1 subscription, any call order" invariant

`TestNotificationRepository` persists three values and reconciles to them under a
single `Mutex`:

| Persisted (DataStore) | Meaning |
|---|---|
| `testNotificationCurrentTopic: String` | the topic shown/copied (generated on first use) |
| `testNotificationWantSubscribed: Boolean` | user intent |
| `testNotificationSubscribedTopic: String` | what FCM is actually subscribed to (`""` = none) — our authoritative record |

Single enforcement point `reconcile()` (always under `withLock`):
1. `target = wantSubscribed ? currentTopic : null`
2. if `subscribedTopic == target` → return
3. if `subscribedTopic != null` → `requireOwnTopic` + `bridge.unsubscribeFromTopic`, clear it (tear down old FIRST → never 2 live)
4. if `target != null` → `requireOwnTopic` + `bridge.subscribeToTopic`; set `subscribedTopic = target` **only on confirmed success** (M1 lesson); on failure leave null so next reconcile retries.

Guarantee: `subscribedTopic ∈ { currentTopic, none }` for any call order; `Mutex` serializes concurrent calls.

### Safety — only ever touch own topics

- Generated topics are `testNotification-<uuid>` (disjoint from `door_open-*` / `buttonHealth-*`).
- `reconcile()` only ever unsubscribes its own tracked `subscribedTopic`; never reads `fcmDoorTopic`, never enumerates.
- **Prefix guard** before every bridge call: `require(t.startsWith("testNotification-"))` → fails safe (throws, logged) rather than touching production.
- Separate DataStore keys from production.

### Four thin UseCases (zero logic — delegate to repo)

- `GetTestNotificationTopicUseCase` → `repo.getTopic()`
- `ChangeTestNotificationTopicUseCase` → `repo.changeTopic()` (regenerates; subscription **follows** to new topic)
- `SubscribeTestNotificationUseCase` → `repo.subscribe()`
- `UnsubscribeTestNotificationUseCase` → `repo.unsubscribe()`

Repo also exposes `state: StateFlow<TestNotificationSandboxState>` (topic + isSubscribed) for the UI.

### App-built display

- `FcmMessageHandler` gains a `testNotification-*` branch → `TestNotificationPresenter` (androidApp, holds appContext): lazily creates a dedicated `NotificationChannel`, builds `NotificationCompat` from data payload (`title`/`body`/`tag`), posts `notify(tag, FIXED_ID, …)` so same-`tag` replaces; `setOnlyAlertOnce(true)` + `setAutoCancel(true)`. Data message → `onMessageReceived` always fires → foreground + background identical.

### UI

Section in `FunctionListContent` (inherits `featureFunctionList`): topic string + tap-to-copy, subscribe / unsubscribe / change controls, live status from the repo state. Wired through `FunctionListViewModel` (the screen's VM, ADR-026).

## File checklist

### domain/
- [ ] `model/TestNotificationTopic.kt` — value class + `TestNotificationSandboxState`
- [ ] `repository/TestNotificationRepository.kt` — interface (getTopic/changeTopic/subscribe/unsubscribe + state)
- [ ] `repository/AppSettingsRepository.kt` — add 3 `Setting`s

### data-local/
- [ ] `DataStoreAppSettings.kt` — add 3 DataStore settings

### data/
- [ ] `repository/DefaultTestNotificationRepository.kt` — Mutex + reconcile + prefix guard

### usecase/
- [ ] `GetTestNotificationTopicUseCase.kt`
- [ ] `ChangeTestNotificationTopicUseCase.kt`
- [ ] `SubscribeTestNotificationUseCase.kt`
- [ ] `UnsubscribeTestNotificationUseCase.kt`

### viewmodel/
- [ ] `FunctionListViewModel.kt` — inject the 4 UseCases + expose state/actions

### androidApp/
- [ ] `fcm/TestNotificationPresenter.kt` — channel + NotificationManager post
- [ ] `fcm/FcmMessageHandler.kt` — add `testNotification-*` branch
- [ ] `fcm/FCMService.kt` — wire presenter into handler
- [ ] `ui/FunctionListContent.kt` — test-notification section
- [ ] `di/AppComponent.kt` — `@Singleton` repo + entry point + 4 UseCases + presenter
- [ ] `ComponentGraphTest` — assertSame for the singleton repo

### iosFramework/
- [ ] `NativeComponent.kt` — provide repo + 4 UseCases (VM ctor changed; iOS-DI rule). Presenter is Android-only.

### tests
- [ ] `DefaultTestNotificationRepositoryTest` — **every call-order permutation asserts ≤1 sub + correct target + only `testNotification-*` ever passed to the fake bridge + a "non-test value refused" test**
- [ ] `FcmMessageHandlerTest` — `testNotification-*` routing branch
- [ ] pure `payload → NotificationContent` mapper test
- [ ] `validate.sh` green before push

## Notes / decisions
- Repo is `@Singleton` (the invariant requires one instance owning the state + Mutex).
- changeTopic-follows-subscription = confirmed default.
- Send is manual (console). To inline-replace test: send twice with the same `tag`.
