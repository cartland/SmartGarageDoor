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
Compose UI (screens, components)
    ↓ collectAsState()
ViewModel (StateFlow, business coordination)
    ↓ suspend calls
Repository (data abstraction, caching)
    ↓              ↓
Retrofit         Room
(network)      (local DB)
    ↓
Firebase Server
```

## Package Structure

All packages under `androidApp/src/main/java/com/chriscartland/garage/`:

| Package | Purpose | Key Classes |
|---------|---------|-------------|
| `applogger/` | Event logging to Room DB, CSV export | `AppLoggerRepository`, `AppLoggerViewModel` |
| `auth/` | Google Sign-In, Firebase Auth | `AuthRepository`, `AuthViewModel`, `AuthState` (sealed) |
| `config/` | App configuration, server config caching | `APP_CONFIG`, `ServerConfigRepository` |
| `coroutines/` | Testable dispatcher injection | `DispatcherProvider`, `DefaultDispatcherProvider` |
| `db/` | Room database, DAOs, schema | `AppDatabase` (v11), `DoorEventDao`, `AppLoggerDao` |
| `door/` | Core domain: door events, status | `DoorRepository`, `DoorViewModel`, `DoorEvent`, `DoorPosition`, `LoadingResult<T>` |
| `fcm/` | FCM registration, push handling | `FCMService`, `DoorFcmRepository`, `DoorFcmState` (sealed) |
| `internet/` | Retrofit service, response DTOs | `GarageNetworkService`, value classes for type-safe params |
| `permissions/` | Notification permission (API 33+) | Accompanist-based permission request |
| `remotebutton/` | Remote button with state machine | `RemoteButtonViewModel`, `PushRepository`, `RequestStatus` |
| `settings/` | SharedPreferences wrapper | `AppSettings`, type-safe `Setting` classes |
| `snoozenotifications/` | Snooze duration options | `SnoozeDurationUIOption`, server conversion |
| `ui/` | Compose screens and components | Screens, DoorStatusCard, AnimatableGarageDoor, theme |
| `version/` | App version info | `AppVersion` |

Root files: `GarageApplication.kt` (@HiltAndroidApp), `MainActivity.kt` (Compose entry point).

## Data Flows

### Door Status: Cold Start

1. `MainActivity.onCreate()` → `DoorViewModel` init
2. ViewModel starts collecting `doorRepository.currentDoorEvent` Flow
3. ViewModel calls `fetchCurrentDoorEvent()` (if `FetchOnViewModelInit.Yes`)
4. `DoorRepository` → `GarageNetworkService.getCurrentEventData(buildTimestamp)`
5. Response parsed → `localDoorDataSource.insertDoorEvent()`
6. Room insert triggers `DoorEventDao.currentDoorEvent()` Flow
7. Flow emits → ViewModel wraps in `LoadingResult.Complete` → UI renders

### Door Status: FCM Push

1. Server sends FCM to topic `door_open-<buildTimestamp>`
2. `FCMService.onMessageReceived()` extracts `DoorEvent` from data payload
3. `doorRepository.insertDoorEvent()` → Room insert → Flow → ViewModel → UI

### Remote Button Press

1. UI → `RemoteButtonViewModel.pushRemoteButton(authRepository)`
2. Checks auth, refreshes token if expired
3. Creates `buttonAckToken` (android-version-timestamp-millis)
4. `PushRepository.push(idToken, buttonAckToken)` → POST `/addRemoteButtonCommand`
5. State machine: NONE → SENDING → SENT (server ack) → RECEIVED (door moves)
6. 10-second timeouts at each state → SENDING_TIMEOUT / SENT_TIMEOUT → NONE

### Authentication

1. User taps sign-in → Google One-Tap UI via `BeginSignInRequest`
2. User selects account → `PendingIntent` → `onActivityResult`
3. Google ID token extracted → `Firebase.auth.signInWithCredential()`
4. `refreshFirebaseAuthState()` → `getIdToken(true)` → `AuthState.Authenticated`
5. Token included as `X-AuthTokenGoogle` header in server requests

## Dependency Injection (Hilt)

| Module | Provides | Scope |
|--------|----------|-------|
| `RetrofitModule` | `GarageNetworkService` | Singleton |
| `AppDatabaseModule` | `AppDatabase` | Singleton |
| `GarageRepositoryModule` | `DoorRepository` | Singleton |
| `PushRepositoryModule` | `PushRepository` | Singleton |
| `AuthRepositoryModule` | `AuthRepository` | Singleton |
| `ServerConfigRepositoryModule` | `ServerConfigRepository` | Singleton |
| `AppLoggerRepositoryModule` | `AppLoggerRepository` | Singleton |
| `DoorFcmRepositoryModule` | `DoorFcmRepository` | Singleton |
| `DispatcherModule` | `DispatcherProvider` | Singleton |

ViewModels use `@HiltViewModel` and are scoped to the navigation graph.

## State Management

- **`LoadingResult<T>`** (sealed class): `Loading(data?)`, `Complete(data?)`, `Error(exception)`. Used by DoorViewModel to represent fetch state.
- **`RequestStatus`** (enum): NONE, SENDING, SENDING_TIMEOUT, SENT, SENT_TIMEOUT, RECEIVED. Drives RemoteButton UI feedback.
- **`AuthState`** (sealed): Unknown, Unauthenticated, Authenticated(user). Drives sign-in UI.
- **`DoorFcmState`** (sealed): Unknown, NotRegistered, Registered(topic). Tracks FCM subscription.

## Database

Room database v11 with exported schemas in `schemas/`.

**Entities:**
- `DoorEvent`: doorPosition, message, timestamps. PK = `"$lastChangeTimeSeconds:$doorPosition"`.
- `AppEvent`: eventKey, timestamp, appVersion. Auto-generated PK.

**DAOs:**
- `DoorEventDao`: `currentDoorEvent()` Flow, `recentDoorEvents()` Flow, `insert()`, `replaceAll()` (atomic transaction)
- `AppLoggerDao`: `insert()`, `getAll()` Flow, `countKey()` Flow

## Configuration

**`APP_CONFIG`** (compile-time): server URL, feature flags (`remoteButtonPushEnabled`, `snoozeNotificationsOption`), `fetchOnViewModelInit`, `recentEventCount`.

**`BuildConfig`** (from `local.properties`): `SERVER_CONFIG_KEY`, `GOOGLE_WEB_CLIENT_ID`.

**`ServerConfig`** (fetched at runtime, cached): `buildTimestamp`, `remoteButtonBuildTimestamp`, `remoteButtonPushKey`. Cached with Mutex to prevent duplicate network calls.
