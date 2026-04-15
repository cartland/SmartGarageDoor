# Architectural Decision Records

## ADR-001: Server-Centric Design

**Status:** Accepted

**Context:** The system has three clients (ESP32, Android app, potential future iOS/web). Business logic changes (door state interpretation, notification rules, error detection) should not require client updates.

**Decision:** All critical business logic lives on the Firebase server. ESP32 reports raw sensor data. Android displays server-computed results. Neither client interprets sensor data. No offline business logic on clients — not even local door state interpretation.

**Consequences:**
- Feature updates deploy to one place (server), not three
- Clients are thin and less likely to have bugs
- ESP32 firmware updates (OTA) are risky and rare
- Adds network dependency: if server is down, no door status interpretation
- Increases server cost (every sensor reading hits Cloud Functions)
- App shows stale data without network; this is an accepted tradeoff

## ADR-002: Current Tech Stack (Android)

**Status:** Accepted, partially migrated toward KMP (see ADR-004)

**Context:** The Android app was built with standard 2024 Android libraries. DI and networking have been migrated to KMP-compatible alternatives.

**Decision:** Current stack:
- **DI:** kotlin-inject (KMP-compatible, replaced Hilt in Phase 3)
- **Network:** Ktor HTTP + kotlinx.serialization (KMP-compatible, replaced Retrofit+Moshi in Phase 4)
- **Database:** Room (Android Jetpack)
- **UI:** Jetpack Compose + Material 3
- **Async:** Kotlin Coroutines + Flow
- **Testing:** JUnit 4 + Mockito (fakes preferred for new tests)

**Consequences:**
- DI and networking are now KMP-compatible
- Room is still Android-only (will need expect/actual in Phase 5)
- Clean architecture layers (domain, data, usecase) are pure Kotlin — ready for `commonMain`

## ADR-003: Testing Philosophy

**Status:** Accepted

**Context:** Solo project with limited time for testing. Need to maximize value per test.

**Decision:**
1. **Tests must add value.** Every test catches a bug that code review alone would miss. No tests for trivial getters, data class defaults, or compiler-enforced exhaustive `when` mappings.
2. **CI is the deployment gate.** If CI passes, the app is safe to ship.
3. **Fakes over mocks** (target state). Mockito works but fakes are more readable, catch more integration issues, and work across KMP. Migrate toward fake implementations as the battery-butler project demonstrates.
4. **Prioritize production risk.** Test silent failures, state machine edge cases, network error handling, auth token lifecycle.
5. **Automate in CI.** If a check can run in CI, it should. Manual QA is a gap in the safety net.

**Reference:** battery-butler's testing patterns (fakes, convention tests, custom Gradle checks).

## ADR-004: Target Tech Stack

**Status:** In progress (DI and networking migrated, KMP setup remaining)

**Context:** Want to eventually share code across platforms (KMP). The battery-butler project demonstrates the target architecture. Migration will happen through several independent refactoring projects.

**Decision:** Target stack:
- **DI:** kotlin-inject (compile-time, KMP-compatible) — ✅ Done (Phase 3)
- **Network:** Ktor HTTP client + kotlinx.serialization (not gRPC — server uses REST) — ✅ Done (Phase 4)
- **Database:** Room with KMP support (alpha, same API)
- **UI:** Compose Multiplatform
- **Testing:** Fakes over Mockito, Kotlin Test, StandardTestDispatcher
- **Static analysis:** Detekt with zero tolerance

**KMP targets:** Android + iOS (no desktop). Firebase Auth on both platforms via platform-specific implementations behind a shared interface (expect/actual).

**Not adopted from battery-butler:**
- gRPC/Wire (server uses REST endpoints forever, no proto definitions)
- Navigation3 (still alpha as of May 2025 — using type-safe routes via Navigation Compose 2.9 instead)
- Desktop target (not needed for this project)

**Consequences:**
- Each migration phase is independent and can be a separate PR
- DI migration (Hilt → kotlin-inject) is the most invasive change
- Network migration (Retrofit → Ktor) requires new HTTP client setup
- KMP preparation can happen incrementally after library migrations
- REST endpoints are the permanent server protocol — no future protocol migration needed

## ADR-005: DispatcherProvider Pattern

**Status:** Implemented

**Context:** ViewModel coroutines originally hardcoded `Dispatchers.IO`, making them untestable. Tests couldn't control coroutine execution timing.

**Decision:** Inject `DispatcherProvider` interface with `main`, `io`, `default` dispatchers into all ViewModels. Tests use `TestDispatcherProvider` backed by `StandardTestDispatcher`.

**Consequences:**
- All ViewModel coroutines are deterministically testable
- `runCurrent()` processes pending work without advancing past delays
- Timeout state machine tests work correctly (delays are virtual)
- Small addition to constructor signatures

## ADR-006: Clean Architecture with UseCase Layer

**Status:** Proposed

**Context:** Current architecture has ViewModels calling Repositories directly. This works but makes ViewModels harder to test (must mock repositories) and harder to share business logic across ViewModels.

**Decision:** Adopt battery-butler's layered module structure:
```
domain/          → interfaces, models (depends on nothing)
usecase/         → business logic (depends on domain)
data/            → repository implementations (depends on domain)
data-local/      → Room, DataStore (depends on domain)
data-network/    → HTTP clients (depends on domain)
viewmodel/       → state management (depends on usecase, domain)
presentation/    → Compose UI (depends on viewmodel)
```

ViewModels depend on UseCases, not Repositories directly. Each UseCase has a single `operator fun invoke()` method. **Every ViewModel operation goes through a UseCase, even simple pass-through ones** — consistency over pragmatism.

**Consequences:**
- Each layer testable in isolation with fakes
- UseCases are reusable across ViewModels
- More files and modules (accepted overhead for consistency)
- Convention tests can enforce structure (e.g., every UseCase has a test)
- Migration is incremental: extract one UseCase at a time
- Simple UseCases may feel like boilerplate but maintain uniform architecture

## ADR-007: Screenshot Tests for Gallery Generation

**Status:** Proposed

**Context:** Need app screenshots for Play Store listings and development documentation. Manual screenshots are tedious and inconsistent.

**Decision:** Use Compose screenshot tests (same approach as battery-butler) to generate gallery images. This is its own migration phase. Screenshots are for asset generation, NOT CI blocking checks. Screenshots will not fail CI on pixel mismatch — they are regenerated on demand.

**Requirements:**
- All preview composables use deterministic data (fixed timestamps, no `Clock.System.now()`)
- Preview parameters threaded through composable chain
- Generated screenshots committed to repository as reference gallery

**Consequences:**
- Consistent, reproducible app screenshots
- Gallery updates are explicit (regenerate + commit)
- No flaky CI from font rendering differences across environments
- Requires disciplined preview authoring (deterministic data)

## ADR-008: Implementation Naming — No "Impl" Suffix

**Status:** Accepted

**Context:** The codebase used `*Impl` suffixes for interface implementations (`DoorRepositoryImpl`, `AuthRepositoryImpl`). As the architecture grows with fakes, platform variants, and multiple real implementations, `Impl` conveys no information about _which_ implementation or _how_ it works.

**Decision:** Name implementations with a descriptive prefix that explains the strategy. The description comes first. If no better name exists, `Default` is an acceptable prefix.

**Naming patterns:**
| Pattern | When to use | Example |
|---------|------------|---------|
| Strategy prefix | Implementation has a clear strategy | `CachedServerConfigRepository`, `NetworkDoorRepository` |
| Platform prefix | Platform-specific implementation | `FirebaseAuthRepository`, `RoomAppLoggerRepository` |
| `Default` prefix | No distinguishing strategy | `DefaultDispatcherProvider` |
| Fake prefix | Test doubles — describe the fake type | `InMemoryDoorRepository`, `StubAuthRepository` |

**Avoid:**
- `*Impl` — says nothing about the implementation
- `Fake*` without further description — `InMemory*` or `Stub*` is more descriptive

**Migration:** Rename existing `*Impl` classes incrementally as they are touched. No bulk rename PR — renames happen alongside functional changes.

**Consequences:**
- Names communicate implementation strategy at a glance
- Easier to distinguish multiple implementations of the same interface
- Slightly longer class names (accepted tradeoff)

## ADR-009: Object-Scoped Functions Over Top-Level Functions

> _Broadened from original ADR-008 (parsing objects over extension functions)._

**Status:** Accepted

**Context:** Bare top-level functions pollute the global namespace and make code harder to discover. Extension functions on generic types (e.g., `Map<K, V>.asDoorEvent()`) create implicit coupling. Both problems get worse in KMP where the public API surface is shared across platforms.

**Decision:** Group related functions in a named `object {}` rather than using bare top-level functions or extension functions on generic receiver types.

**Rules:**
1. **No bare top-level utility functions** — group in an `object {}` with a descriptive name
2. **No extension functions on generic types** — use explicit parameter types instead
3. **Private top-level functions are fine** — the rule targets public/internal API surface
4. **Composable functions are exempt** — Compose conventions expect top-level `@Composable` functions

**Example — avoid:**
```kotlin
// Bare top-level function — hard to discover, pollutes namespace
fun createButtonAckToken(now: Date): String { ... }

// Extension on generic type — implicit coupling
private fun <K, V> Map<K, V>.asDoorEvent(): DoorEvent? { ... }
```

## ADR-010: Typed API Patterns — Observation and One-Time Requests

**Status:** Accepted

**Context:** The app has two fundamental interaction patterns: ongoing state observation (door position, auth state) and discrete actions (push button, snooze, fetch data). Both patterns currently use nullable returns or Boolean success flags, which fail silently and don't enforce handling of edge cases.

**Decision:** Adopt two typed API patterns throughout UseCase and ViewModel layers:

1. **Observation APIs** — `Flow<Result<D, E>>` or `StateFlow<UiState>` where `UiState` is a sealed class with `Loading`, `Success`, `Error` variants. Use these for multi-stage transitions (e.g., button press tracking: SENDING → SENT → RECEIVED). The UI can show each phase.

2. **One-time Request APIs** — `suspend fun action(): AppResult<D, E>` where both `D` (data) and `E` (error) are sealed or enum types. Use exhaustive `when` statements to handle every case at compile time. New edge cases produce compiler errors, not silent failures.

**Rules:**
- `D` and `E` should be sealed classes or enums — never open types
- `invoke()` on UseCases returns `AppResult<D, E>`, not nullable types
- ViewModels translate `AppResult` into UI-observable state (Flow or StateFlow)
- Add new sealed variants when new edge cases are discovered — the compiler forces all call sites to handle them

**Example:**
```kotlin
// UseCase returns typed result
suspend operator fun invoke(token: String): AppResult<DoorEvent, FetchError>

// Error is a sealed type — exhaustive when
sealed interface FetchError : AppError {
    data object NotReady : FetchError
    data object NetworkFailed : FetchError
    data class ServerError(val code: Int) : FetchError
}

// ViewModel handles exhaustively
when (val result = fetchUseCase(token)) {
    is AppResult.Success -> _state.value = UiState.Loaded(result.data)
    is AppResult.Error -> when (result.error) {
        FetchError.NotReady -> _state.value = UiState.ConfigMissing
        FetchError.NetworkFailed -> _state.value = UiState.Offline
        is FetchError.ServerError -> _state.value = UiState.Error(result.error.code)
    }
}
```

**Consequences:**
- Eliminates silent failures — every error path is visible
- New edge cases caught at compile time via exhaustive `when`
- Slightly more verbose than nullable returns, but the safety is worth it
- Requires migrating existing nullable/Boolean APIs incrementally

## ADR-011: No-Throw Error Handling

**Status:** Accepted

**Context:** The codebase has multiple places where library exceptions are caught and silently swallowed (`catch (e: Exception) { Logger.e { ... } }`). Callers can't distinguish success from failure. The Detekt `SwallowedException` rule was allowing `Exception` in its ignore list, defeating its purpose.

**Decision:** Adopt a no-throw error handling policy:

1. **Never throw** from application code (except `CancellationException` for coroutine cancellation)
2. **Catch at boundaries** — library exceptions (Ktor, Firebase, Room) are caught at the data source/bridge layer and converted to sealed error types
3. **Return sealed results** — use `AppResult<D, E>` or `NetworkResult<T>` instead of nullable returns or Boolean success flags
4. **No `else` in `when` on sealed types** — always list every variant explicitly so the compiler forces handling new variants
5. **Detekt enforcement** — `SwallowedException` and `TooGenericExceptionCaught` rules are active. Existing violations are baselined and must be resolved incrementally

**Rules:**
- Data sources return `NetworkResult<T>` (Success, HttpError, ConnectionFailed)
- Repositories return `AppResult<D, FetchError>` or `AppResult<D, ActionError>`
- UseCases return `AppResult<D, E>` to ViewModels
- ViewModels handle errors with exhaustive `when` and update UI state accordingly
- Only `CancellationException` may propagate — all others must be caught and typed

**Consequences:**
- Every error path is visible and compiler-checked
- Adding a new error variant forces all callers to handle it
- Baselined Detekt violations track technical debt — each resolved violation is a win
- New code cannot swallow exceptions or catch generic Exception without baseline entry

**Example — preferred:**
```kotlin
object ButtonAckTokens {
    fun create(now: Date): String { ... }
}

object FcmPayloadParser {
    fun parseDoorEvent(data: Map<String, String>): DoorEvent? { ... }
}
```

**Consequences:**
- Functions are discoverable via the containing object
- Cleaner namespace, especially in KMP public API surface
- Function signatures are explicit about expected input types
- Slightly more nesting (accepted tradeoff for discoverability)
- Migrate existing code incrementally — prioritize public/shared module APIs

## ADR-012: Garage Door Button UX Redesign

**Status:** Accepted

**Context:** The remote garage button had several UX problems:
1. Military terminology ("Arming"/"Armed") for a garage door button
2. "Sending"/"Sent" describes network packets, not what the user cares about (the door)
3. A numbered progress bar (0-5) with gaps and no clear meaning
4. The button was a custom circular gradient — distinctive but non-standard
5. No visual distinction between success and failure states (only text differed)
6. The user cannot know whether the door will open or close — the button toggles

**Decision:** Redesign the button and progress indicator:

### Button
- Standard Material3 rectangular button (not custom circle/gradient)
- Idle text: "Garage Door Button" (clear that it's a button)
- Confirmation text: "Door will move." (line 1) + "Confirm?" (line 2, separate Text composable)
- Idle: default Material3 `FilledTonalButton`
- Confirmation: amber/caution color (not red — caution, not danger)
- Post-confirm: button disabled with simple status text (Sending.../Waiting.../Done!/Failed/Cancelled)
- Parent layout gives both states the same width for visual stability

### Network Diagram (replaces progress bar)
- Three-node diagram: Phone → Server → Door (icon drawables, not emoji)
- Connected by animated lines showing request flow
- Gray dashed line: not started
- Animated dotted line moving forward: in progress
- Solid green line: succeeded
- Solid red line: failed
- Generic composable — takes node/edge states, not `RemoteButtonState` directly

### State Renames
| Old | New | User-facing text |
|-----|-----|-----------------|
| Ready | Ready | "Garage Door Button" |
| Arming | Preparing | "Garage Door Button" (disabled) |
| Armed | AwaitingConfirmation | "Door will move." / "Confirm?" |
| NotConfirmed | Cancelled | "Cancelled" |
| Sending | SendingToServer | "Sending..." |
| Sent | SendingToDoor | "Waiting..." |
| Received | Succeeded | "Done!" |
| SendingTimeout | ServerFailed | "Failed" |
| SentTimeout | DoorFailed | "Failed" |

**Consequences:**
- User sees where their command is in the Phone→Server→Door chain
- Failures show exactly where the chain broke (red on the failed edge)
- No military/network jargon — language describes physical outcomes
- Standard M3 button is more accessible and consistent with platform conventions
- Network diagram component is reusable (generic node/edge state model)
- Touches domain (sealed interface), usecase (state machine + VM), UI (composables), and tests

## ADR-013: Flow and StateFlow Boundaries

**Status:** Accepted (supersedes StateFlow guidance in ADR-010)

**Context:** A critical bug was caused by using `StateFlow<PushStatus>` in a repository to signal transient events (SENDING → IDLE). StateFlow conflates intermediate values — if SENDING and IDLE are set in quick succession, collectors may only see IDLE, silently dropping the SENDING signal. This caused the button state machine to get stuck indefinitely. The root problem: StateFlow is a *state* primitive, not an *event* primitive.

**Decision:** Restrict where each Flow type may be used:

### UseCases return one of two types:

1. **One-shot operations:** `suspend fun invoke(): AppResult<D, E>`
   - Call it, await the result, done
   - The suspend function return *is* the completion signal
   - Example: push button, snooze notifications, fetch data

2. **Observations:** `fun invoke(): Flow<T>`
   - Non-suspend — creating the Flow does nothing
   - Collection requires a coroutine scope (caller's responsibility)
   - Lifecycle is controlled by the collector's scope
   - Example: observe door events, observe auth state

### StateFlow lives only in ViewModels:

- ViewModels expose `StateFlow<T>` for Compose to collect
- Every StateFlow has an initial value (the UI must always show something)
- The ViewModel is the only layer that converts Flow/AppResult into StateFlow
- No UseCase, Repository, or data layer may expose StateFlow without explicit approval

### Repositories use Flow and suspend:

- Streams: `val events: Flow<T>` (cold Flow, backed by Room/DataStore)
- Actions: `suspend fun doThing(): Result` (returns when complete)
- Repositories must not use StateFlow for transient signals
- If a repository needs to expose changing state, use Flow (not StateFlow)

### Exceptions require individual approval:

Any use of StateFlow below the ViewModel layer must be reviewed and approved on a case-by-case basis. The justification must explain why Flow is insufficient and how StateFlow conflation won't cause missed updates.

**Current violations to review:**
- `RemoteButtonRepository.pushButtonStatus: StateFlow<PushStatus>` — caused the button stuck bug. Must be replaced with suspend return or direct callback.
- `AuthRepository.authState: StateFlow<AuthState>` — pending review.
- `SnoozeRepository.snoozeState: StateFlow<SnoozeState>` — pending review.

**Rules:**
- `suspend fun` return = completion signal. Don't add a parallel StateFlow for the same information.
- `Flow<T>` = observation stream. Collector manages lifecycle.
- `StateFlow<T>` = UI state in ViewModel only. Always has initial value.
- If every intermediate value matters (SENDING → IDLE), do not use StateFlow — use suspend return, Channel, or callback.

**Consequences:**
- Eliminates the class of bugs where StateFlow conflation silently drops signals
- Clear ownership: ViewModel owns UI state, everything below produces raw data
- Forces one-shot operations to use suspend returns, which are simpler and more reliable
- Existing StateFlow usages in repositories must be reviewed and migrated
- New code cannot add StateFlow below ViewModel without approval

## ADR-014: FCM Architecture — Service, UseCase, and ViewModel Boundaries

**Status:** Accepted

**Context:** FCM registration logic currently lives in `DoorViewModel`. This is wrong for two reasons:
1. ViewModels are tied to UI lifecycle — if the screen is closed, the ViewModel may not exist to handle registration retry
2. `DoorViewModel` is a door-status ViewModel with FCM registration bolted on — mixed responsibilities

The `FCMService` correctly inserts door events into the repository (data layer), but registration, retry, and status management need clearer ownership.

**Decision:** FCM components are distributed across layers as follows:

### Data Layer (infrastructure)
- `FCMService` — Android entry point. Receives messages, delegates to `FcmMessageHandler`. Does not contain business logic.
- `FcmMessageHandler` — Parses payload, inserts into `DoorRepository`, logs. Testable with fakes.
- `FirebaseDoorFcmRepository` — Wraps Firebase subscribe/unsubscribe SDK calls behind `DoorFcmRepository` interface. Persists topic in DataStore.
- `MessagingBridge` — Abstracts Firebase Messaging SDK for testability.

### Domain Layer (business logic)
- `RegisterFcmUseCase` — Single attempt: fetch build timestamp, subscribe to topic. Returns `AppResult<Unit, ActionError>`. Does not retry — caller decides retry policy.
- `FetchFcmStatusUseCase` — Reads current registration status from repository.
- `DoorFcmRepository` interface — Domain-level contract for FCM operations.
- Domain models: `DoorFcmTopic`, `DoorFcmState`, `FcmRegistrationStatus`. No Firebase imports.

### Presentation Layer (ViewModel)
- ViewModel **observes** FCM registration status via `Flow<FcmRegistrationStatus>` from the repository
- ViewModel does **not own** registration logic or retry policy
- Registration is triggered by app startup (`AppStartupActions`), not by ViewModel

### App Startup
- `AppStartupActions` calls `RegisterFcmUseCase` with retry
- Retry policy: fixed delay, retry forever (FCM is critical for the app)
- Retry is app-scoped (via `ApplicationScope`), not screen-scoped
- If registration succeeds, status updates via repository Flow → ViewModel → UI

### Rules
- `FCMService` must not contain business logic — delegate to handler/UseCase
- Registration logic must not live in a ViewModel — it's app-scoped, not screen-scoped
- FCM payload parsing happens in the data layer (`FcmPayloadParser`) — domain models have no Firebase imports
- Registration retry is the caller's responsibility, not the UseCase's

### Current violations to resolve
- `DoorViewModel.registerFcm()` — must move to `AppStartupActions`
- `DoorViewModel.fcmRegistrationStatus` — must become an observation of repository state
- `DoorViewModel.fetchFcmRegistrationStatus()` — must move to startup or be removed
- `FcmRegistration.kt` composable — remove, replace with ViewModel observation

**Consequences:**
- FCM registration works even when no screen is visible (app-scoped retry)
- DoorViewModel becomes purely about door status — no FCM responsibility
- Testable at every layer: handler, UseCase, retry policy, ViewModel observation
- FCM message handling is already correct (FCMService → repository → Room → Flow → UI)

## ADR-015: App-Scoped Managers for Lifecycle Operations

**Status:** Accepted

**Context:** Some operations (like FCM registration with retry) must outlive any single screen. UseCases are single-attempt by design (ADR-013). ViewModels are screen-scoped. We need a pattern for app-scoped operations that retry, poll, or run continuously.

**Decision:** Use a **manager class** that owns the lifecycle of an app-scoped operation. The manager calls UseCases but adds lifecycle behavior (retry, cancellation, deduplication).

**Pattern:**
- Manager is created by DI with `ApplicationScope` (singleton coroutine scope)
- Manager has a `start()` method called from `AppStartupActions`
- Manager calls a UseCase (single-attempt, returns `AppResult`)
- Manager owns the retry loop (fixed delay, forever or bounded)
- Manager guards against concurrent starts (only one retry loop active)
- Manager exposes status as `Flow` for ViewModel observation

**Rules:**
- UseCases remain single-attempt — never retry internally
- Managers never touch UI state — they produce `Flow` that ViewModels observe
- Managers are singleton — one instance per app process
- `start()` is idempotent — calling twice doesn't create two retry loops

**Consequences:**
- Clear separation: UseCase = logic, Manager = lifecycle, ViewModel = UI state
- App-scoped operations survive screen rotation and navigation
- Testable: manager tested with fake UseCase and test dispatcher for time control
- New pattern to learn, but limited to truly app-scoped operations (FCM, token refresh, sync)

## ADR-016: Scope Injection for ViewModel Coroutines with Delay

**Status:** Accepted

**Context:** `DurationSince` was a Compose content-lambda composable wrapping entire UI trees to provide a live-updating `Duration`. This caused blank screenshots (content renders inside `LaunchedEffect` timing) and mixed display concerns (show "3 min ago") with business concerns (is the check-in stale?). When replacing it, the ViewModel's periodic staleness ticker (`while(true) { delay(30s) }`) hung `runTest` because `Dispatchers.setMain(testDispatcher)` routes `viewModelScope` coroutines to the test scheduler, and `runTest` tries to drain all pending virtual time.

**Decision:** Split into two layers and inject `CoroutineScope` for timer coroutines:

- **Display**: `rememberDurationSince()` returns `State<Duration>`, updated every 1s via `LaunchedEffect`. Lives in `androidApp` UI layer.
- **Business**: `DoorViewModel.isCheckInStale: StateFlow<Boolean>` computed from `AppClock` + periodic 30s ticker + reactive data-change collect. Staleness logging moved from composable to ViewModel.
- **Scope injection**: `DefaultDoorViewModel` accepts `CoroutineScope` for staleness coroutines (same pattern as `FcmRegistrationManager`, ADR-015). Production passes `applicationScope`; tests pass `backgroundScope` from `runTest`. Jobs tracked and cancelled in `onCleared()`.
- **Clock injection**: `AppClock` interface in domain module. Tests use `FakeClock` to control wall-clock time independently from coroutine virtual time.

**Rules:**
- ViewModel coroutines that use `delay` or infinite loops must run in injected scope, not `viewModelScope`
- Display-layer timers (1s "ago" text) use `rememberDurationSince` — no business logic
- Staleness boolean comes from ViewModel, never computed locally in composables
- Two time axes in tests: `FakeClock.advanceSeconds()` for wall clock, `advanceTimeBy()` for coroutine delays

**Consequences:**
- No blank screenshots from content-lambda wrapping
- Staleness is testable with virtual time + fake clock
- One source of truth for "is check-in stale" (ViewModel, not composable)
- Tests never hang — `backgroundScope` cancelled on test completion
- Slight complexity: two scopes in ViewModel (viewModelScope for screen-scoped, injected scope for timer-scoped)
