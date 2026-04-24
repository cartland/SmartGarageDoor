# Architecture

## System Overview

Smart Garage Door is an IoT system with three components:

```
ESP32 Firmware          Firebase Server          Android App
(sensor + relay)   →   (business logic)    ←   (display + control)
                            ↕
                       Firestore DB
```

**Key principle:** The server handles all critical business logic. Clients (ESP32 and Android) are intentionally simple. This enables feature updates without client deployments.

- **ESP32** reports raw sensor data (open/closed/transitioning). It does not interpret door state.
- **Server** interprets sensor data into door events, manages notifications, detects errors.
- **Android** displays server-computed door status and sends button press commands.

## Android Layer Model

```
Compose UI (androidApp/ui)
    ↓ collectAsState()
ViewModel (usecase module, shared)
    ↓ UseCase invocation
UseCase (usecase module, shared)
    ↓ suspend / Flow
Repository interface (domain module)
    ↓ concrete impl (data module)
Data source interface (data module)
    ↓ Ktor HTTP             ↓ Room / DataStore
NetworkDataSource        LocalDataSource
(data/ktor)             (data-local)
    ↓                       ↓
Firebase Server          On-device storage
```

All business logic lives in KMP-compatible modules. `androidApp/` is the Compose UI + Firebase bridge implementations + DI wiring only.

## Module Structure

Gradle modules (declared in `settings.gradle.kts`):

| Module | Source set | Platform | Contents |
|--------|-----------|----------|----------|
| `domain/` | `commonMain` | KMP | Model types + repository interfaces; no Android or framework deps |
| `data/` | `commonMain` | KMP | Data source interfaces, Ktor HTTP implementations, platform bridges, repository implementations |
| `data-local/` | `commonMain` | KMP | Room database + DAOs + entity mapping, DataStore settings |
| `usecase/` | `commonMain` | KMP | UseCases, ViewModels (via KMP `androidx.lifecycle.viewmodel`), app-scoped Managers, `ButtonStateMachine` |
| `presentation-model/` | `commonMain` | KMP | Screen-state data classes (`HomeScreenState`, `DoorHistoryScreenState`, etc.) + demo data |
| `androidApp/` | `main/java` | Android | Compose UI, Firebase bridge implementations, DI wiring, Activity/Application/Service |
| `test-common/` | `commonMain` | KMP | Shared fakes (14+ fakes across repositories, data sources, bridges) |
| `android-screenshot-tests/` | `screenshotTest` | Android | Preview-based screenshot tests |
| `macrobenchmark/` | `main` | Android | Baseline profile generator, startup benchmark |

### Domain module (`domain/`)

| Package | Contents |
|---------|----------|
| `domain/model/` | `DoorEvent`, `DoorPosition`, `LoadingResult<T>`, `AppResult<D, E>`, `AuthState` (sealed), `User`, `FirebaseIdToken`, `GoogleIdToken`, `DoorFcmState` (sealed), `DoorFcmTopic`, `FcmRegistrationStatus`, `PushStatus`, `SnoozeState` (sealed), `SnoozeAction` (sealed), `SnoozeDurationUIOption`, `SnoozeDurationServerOption`, `ServerConfig`, `AppConfig`, `AppLoggerKeys` |
| `domain/repository/` | `AppLoggerRepository`, `AppSettingsRepository`, `AuthRepository`, `DoorFcmRepository`, `DoorRepository`, `RemoteButtonRepository`, `ServerConfigRepository`, `SnoozeRepository` (interfaces only) |
| `domain/coroutines/` | `DispatcherProvider` |

`PushRepository` was split into `RemoteButtonRepository` + `SnoozeRepository` (#203). The old unified interface is gone.

### Data module (`data/`)

| Package / File | Contents |
|---|---|
| `data/` root | `LocalDoorDataSource`, `NetworkDoorDataSource`, `NetworkButtonDataSource`, `NetworkConfigDataSource` (data source interfaces), `AuthBridge`, `MessagingBridge` (platform SDK abstractions), `NetworkResult<T>`, `FcmPayloadParser` |
| `data/ktor/` | Ktor HTTP implementations + `KtorHttpClientProvider` (engine selected via KMP `expect/actual`) |
| `data/repository/` | `NetworkDoorRepository`, `NetworkRemoteButtonRepository`, `NetworkSnoozeRepository`, `CachedServerConfigRepository`, `FirebaseAuthRepository`, `FirebaseDoorFcmRepository` |
| `data/coroutines/` | `DefaultDispatcherProvider` |

### Data-local module (`data-local/`)

Room database (`@Database(version = 11)` with `@ConstructedBy` for KMP) + `BundledSQLiteDriver`:
- Entities: `DoorEventEntity`, `AppEvent`
- DAOs: `DoorEventDao`, `AppLoggerDao`
- Data sources: `DatabaseLocalDoorDataSource` (wraps `DoorEventDao`)
- Logger: `RoomAppLoggerRepository`
- Settings: `DataStoreAppSettings` (reactive `Setting<T>` Flow-based; replaced SharedPreferences in #199)

### UseCase module (`usecase/`)

ViewModels (all five):
`DefaultDoorViewModel`, `DefaultRemoteButtonViewModel`, `DefaultAuthViewModel`, `DefaultAppLoggerViewModel`, `DefaultAppSettingsViewModel`

UseCases (actions + observations):
`PushRemoteButtonUseCase`, `SnoozeNotificationsUseCase`, `FetchCurrentDoorEventUseCase`, `FetchRecentDoorEventsUseCase`, `FetchFcmStatusUseCase`, `RegisterFcmUseCase`, `DeregisterFcmUseCase`, `EnsureFreshIdTokenUseCase`, `SignInWithGoogleUseCase`, `SignOutUseCase`, `LogAppEventUseCase`, `FetchSnoozeStatusUseCase`, `AppSettingsUseCase`, `ObserveDoorEventsUseCase`, `ObserveAuthStateUseCase`, `ObserveSnoozeStateUseCase`, `ObserveAppLogCountUseCase`

App-scoped managers & helpers:
`ButtonStateMachine`, `CheckInStalenessManager`, `FcmRegistrationManager`, `AppClock`

Phase 43 (#240-#252) enforces ViewModel → UseCase only — a lint rule blocks `domain.repository.*` imports from any `*ViewModel.kt`.

### Presentation-model module (`presentation-model/`)

`HomeScreenState`, `DoorHistoryScreenState`, `ProfileScreenState`, demo data.

### Android app module (`androidApp/`)

Packages under `androidApp/src/main/java/com/chriscartland/garage/`:

| Package | Purpose | Key files |
|---------|---------|-----------|
| (root) | Application + entry points | `GarageApplication.kt`, `MainActivity.kt`, `AppStartup.kt` |
| `applogger/` | CSV export (UI-side) | `ExportAppLogCsv.kt` |
| `auth/` | Firebase bridge + Google One-Tap | `FirebaseAuthBridge.kt`, `GoogleSignInState.kt` |
| `config/` | Reads `BuildConfig` into `AppConfig` | `AppConfigFactory.kt` |
| `di/` | kotlin-inject wiring | `AppComponent.kt`, `ActivityViewModels.kt`, `ComponentProvider.kt`, `Singleton.kt` |
| `fcm/` | Firebase Messaging bridge + Android service | `FCMService.kt`, `FcmMessageHandler.kt`, `FirebaseMessagingBridge.kt` |
| `permissions/` | Notification permission (API 33+) | Accompanist request flow |
| `ui/` | Compose screens, cards, theme, navigation | `Main.kt` (Nav3 NavDisplay + entryProvider), `HomeContent.kt`, `DoorHistoryContent.kt`, `ProfileContent.kt`, `RemoteButtonContent.kt`, `SnoozeNotificationCard.kt`, `DoorStatusCard.kt`, `AnimatableGarageDoor.kt`, `GarageDoorCanvas.kt`, `theme/` |
| `version/` | Android `AppVersion` via `PackageManager` | — |

`GarageApplication.kt` uses kotlin-inject (`AppComponent`). Hilt was fully removed (#133 era, see `docs/DI-MIGRATION.md`). No `@HiltAndroidApp` in the tree.

## Data Flows

### Door Status: Cold Start

1. `MainActivity.onCreate()` → `DefaultDoorViewModel` init (via kotlin-inject)
2. ViewModel's `init` starts collecting `observeDoorEvents.current()` from `ObserveDoorEventsUseCase`
3. If constructed with `fetchOnInit = true`, ViewModel calls `fetchCurrentDoorEvent()`
4. `FetchCurrentDoorEventUseCase` → `doorRepository.fetchCurrentDoorEvent(buildTimestamp)`
5. `NetworkDoorRepository` → `NetworkDoorDataSource` (Ktor) → server; response → `LocalDoorDataSource.insert()`
6. Room insert triggers `DoorEventDao.currentDoorEvent()` Flow → `ObserveDoorEventsUseCase` passthrough
7. ViewModel **also** sets `_currentDoorEvent.value = LoadingResult.Complete(result.data)` explicitly in the Success branch (ADR-023) — `MutableStateFlow` dedups by equality, so relying on the Flow observer alone latches Loading when the fetched value equals the cached value

### Door Status: FCM Push

1. Server sends FCM to topic `door_open-<buildTimestamp>`
2. `FCMService.onMessageReceived()` extracts `DoorEvent` from data payload
3. `doorRepository.insertDoorEvent()` → Room insert → Flow → ViewModel → UI

### Remote Button Press

1. UI → `RemoteButtonViewModel.onButtonTap()` → `ButtonStateMachine.onTap()`
2. State machine: Ready → Preparing (500ms) → AwaitingConfirmation (5s timeout) → tap → SendingToServer
3. On confirm: `onSubmit` callback → `PushRemoteButtonUseCase` checks auth, refreshes token if expired
4. `PushRepository.push(idToken, buttonAckToken)` → POST `/addRemoteButtonCommand`
5. State machine observes `pushButtonStatus`: SendingToServer → SendingToDoor (server ack) → Succeeded (door moves)
6. Failure paths: ServerFailed / DoorFailed → Ready after display delay
7. All transitions atomic via single Channel consumer; testable with virtual time
8. UI: GarageDoorButton (M3) + NetworkProgressDiagram (phone → server → door)

### Authentication

1. User taps sign-in → Google One-Tap UI via `BeginSignInRequest`
2. User selects account → `PendingIntent` → `onActivityResult`
3. Google ID token extracted → `Firebase.auth.signInWithCredential()`
4. `refreshFirebaseAuthState()` → `getIdToken(true)` → `AuthState.Authenticated`
5. Token included as `X-AuthTokenGoogle` header in server requests

## Dependency Injection (kotlin-inject)

All dependencies wired in `AppComponent` (see `docs/DI-MIGRATION.md` for the Hilt→kotlin-inject migration history and `docs/DI_SINGLETON_REQUIREMENTS.md` for `@Singleton` correctness rules).

Entry points exposed by `AppComponent` (abstract vals — required for `@Singleton` caching, see CLAUDE.md):

| Category | Bindings |
|----------|----------|
| ViewModels | `authViewModel`, `appLoggerViewModel`, `appSettingsViewModel`, `doorViewModel`, `remoteButtonViewModel` |
| Repositories (`@Singleton`) | `authRepository`, `doorRepository`, `serverConfigRepository`, `snoozeRepository`, `remoteButtonRepository`, `doorFcmRepository`, `appLoggerRepository`, `appSettings` |
| Data sources | `networkDoorDataSource`, `networkConfigDataSource`, `networkButtonDataSource`, `localDoorDataSource` |
| Bridges | `authBridge`, `messagingBridge` |
| Infrastructure | `httpClient`, `appDatabase`, `dispatcherProvider`, `applicationScope`, `appClock`, `appConfig`, `appStartup` |
| App-scoped managers | `fcmRegistrationManager`, `checkInStalenessManager` |

ViewModels are created via the `activityViewModel()` helper in `di/ActivityViewModels.kt` for Activity-scoped sharing.

Safety rails enforced by `validate.sh`:
- `checkSingletonGuard` — any `@Singleton` provider for `Database` / `Settings` / `HttpClient` / a state-owning repository must be declared via `abstract val` (ADR-022).
- `checkSingletonCaching` — parses the KSP-generated `InjectAppComponent.kt` and fails if any `@Singleton` binding is instantiated without `_scoped.get(...)` caching.

## State Management

- **`LoadingResult<T>`** (sealed class): `Loading(data?)`, `Complete(data?)`, `Error(exception)`. Used by DoorViewModel to represent fetch state.
- **`RemoteButtonState`** (sealed): Ready, Arming, Armed, NotConfirmed, Sending, Sent, Received, SendingTimeout, SentTimeout. Unified state for the remote garage button — combines tap-to-confirm interaction and network/door request lifecycle. Owned by `ButtonStateMachine` in `usecase/`.
- **`SnoozeState`** (sealed): Loading, NotSnoozing, Snoozing(until). Always-visible current snooze status from server.
- **`SnoozeAction`** (sealed): Idle, Sending, Succeeded.{Cleared, Set}, Failed.{NotAuthenticated, MissingData, NetworkError}. Overlay on top of SnoozeState; auto-resets to Idle after 10s.
- **`AuthState`** (sealed): Unknown, Unauthenticated, Authenticated(user). Drives sign-in UI.
- **`DoorFcmState`** (sealed): Unknown, NotRegistered, Registered(topic). Tracks FCM subscription.

## Database

Room database (`@Database(version = 11)`) in `data-local/` with exported schemas in `data-local/schemas/`. Uses `BundledSQLiteDriver` for KMP compatibility; Android provides the factory via `expect/actual`.

**Entities:**
- `DoorEventEntity`: doorPosition, message, timestamps. PK = `"$lastChangeTimeSeconds:$doorPosition"`. Round-trip mapping to domain `DoorEvent`.
- `AppEvent`: eventKey, timestamp, appVersion. Auto-generated PK.

**DAOs:**
- `DoorEventDao`: `currentDoorEvent()` Flow, `recentDoorEvents()` Flow, `insert()`, `replaceAll()` (atomic transaction)
- `AppLoggerDao`: `insert()`, `getAll()` Flow, `countKey()` Flow

Schema changes are guarded by `validate.sh`'s schema-drift check and `RoomSchemaTest` contract tests. See the "Room Database Safety" section of CLAUDE.md for the change recipe.

## Configuration

**`APP_CONFIG`** (compile-time): server URL, feature flags (`remoteButtonPushEnabled`, `snoozeNotificationsOption`), `fetchOnViewModelInit`, `recentEventCount`.

**`BuildConfig`** (from `local.properties`): `SERVER_CONFIG_KEY`, `GOOGLE_WEB_CLIENT_ID`.

**`ServerConfig`** (fetched at runtime, cached): `buildTimestamp`, `remoteButtonBuildTimestamp`, `remoteButtonPushKey`. Cached with Mutex to prevent duplicate network calls.
