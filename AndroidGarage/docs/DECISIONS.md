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

**Status:** Accepted. Adopted incrementally; all 5 production ViewModels use UseCases as of Phase 43. The "every VM operation goes through a UseCase, even pass-through ones" stance is reaffirmed as deliberate capability documentation, not redundancy — see ADR-021 and ADR-022 which depend on this layering.

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

**Status:** Partially superseded by ADR-022.

The core insight of ADR-013 — that `StateFlow` is a **state** primitive, not an **event** primitive, and that conflation silently drops transient signals (SENDING → IDLE) — remains correct. The blanket rule "StateFlow lives only in ViewModels" was too restrictive and, applied uniformly, produced the snooze-state propagation bug documented in `VIEWMODEL_SCOPING_ISSUE.md` (android/164-168). ADR-022 refines ADR-013 by distinguishing **event-y signals** (where ADR-013's ban on StateFlow still applies) from **state-y data with a current value** (where `StateFlow` belongs at the repository boundary, not only in ViewModels).

Read ADR-022 alongside ADR-013; the rules below apply to event-y signals only.

**Original context:** A critical bug was caused by using `StateFlow<PushStatus>` in a repository to signal transient events (SENDING → IDLE). StateFlow conflates intermediate values — if SENDING and IDLE are set in quick succession, collectors may only see IDLE, silently dropping the SENDING signal. This caused the button state machine to get stuck indefinitely. The root problem: StateFlow is a *state* primitive, not an *event* primitive.

**Original decision:** Restrict where each Flow type may be used:

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

### ~~StateFlow lives only in ViewModels~~ (superseded — see ADR-022)

**Superseded by ADR-022 for state-y data.** State-y data (auth, snooze, current door event, server config, FCM registration) now lives as `StateFlow<T>` at the repository boundary — owned by `@Singleton` repos, passed through by reference to VMs. Every StateFlow still has an initial value (retained). The rules below apply only to **event-y signals** (transient data where conflation loses information).

### Repositories use Flow and suspend (for event-y / cold / list-y data):

- Streams (list-y / cold): `fun observeRecentEvents(): Flow<List<T>>` (backed by Room/DataStore)
- Actions: `suspend fun doThing(): Result` (returns when complete)
- Repositories must not use StateFlow for **transient / event-y** signals (retained rule)
- State-y data with a current value DOES belong as `StateFlow<T>` at the repo per ADR-022

### ~~Exceptions require individual approval~~ (superseded — see ADR-022)

**Superseded for state-y data.** ADR-022 is the default shape: state-y → `StateFlow<T>` at the repo, no approval needed. Event-y signals below the ViewModel layer still need justification.

**Resolved violations:**
- `RemoteButtonRepository.pushButtonStatus: StateFlow<PushStatus>` — caused the button stuck bug. Remains banned (event-y signal; suspend return or Channel instead). ADR-013's original concern stands.
- `AuthRepository.authState: StateFlow<AuthState>` — **resolved by ADR-022** — state-y by design, correctly exposed as StateFlow.
- `SnoozeRepository.snoozeState: StateFlow<SnoozeState>` — **resolved by ADR-022** — state-y by design, correctly exposed as StateFlow.

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
- **Scope injection**: `DefaultDoorViewModel` accepts `CoroutineScope` for staleness coroutines (same pattern as `FcmRegistrationManager`, ADR-015). Production passes `applicationScope`. Jobs tracked and cancelled in `onCleared()`.

**Test scope choice — `this` vs `backgroundScope`:**
- `runTest { scope = this }` — use when the injected coroutine has a natural end (e.g., retry loop stops on success). `FcmRegistrationManagerTest` does this. `runTest` blocks until all children of `this` complete, so infinite loops would hang.
- `runTest { scope = backgroundScope }` — use when the coroutine never ends (e.g., periodic ticker). `DoorViewModelTest` does this. `backgroundScope` is cancelled at test completion without blocking — the right choice for `while(true)` timer loops.
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

## ADR-017: Test Conventions

**Status:** Accepted

**Context:** Test patterns across `*ViewModelTest.kt`, `*ManagerTest.kt`, and `Fake*.kt` files had drifted into multiple competing styles. An audit found three different ViewModel test setup patterns, mixed scope handling for tests with infinite coroutines, mixed state-observation styles (`.value` vs `.first()`), and mixed fake mutation styles (10 fakes use public `var`, 4 use `setX()` methods). This ADR locks in the conventions to follow.

### Rule 1: ViewModel test setup

Tests for classes that extend `androidx.lifecycle.ViewModel` MUST set up the Main dispatcher.

```kotlin
@BeforeTest fun setup() { Dispatchers.setMain(testDispatcher) }
@AfterTest fun tearDown() { Dispatchers.resetMain() }
```

Tests for non-ViewModel classes (e.g., `FcmRegistrationManager`) don't need this — they have no `viewModelScope`.

**Why:** `viewModelScope` uses `Dispatchers.Main.immediate`. Without `setMain`, ViewModel construction fails with "Module with the Main dispatcher is missing." Adding it where it's not needed pretends a class uses lifecycle infrastructure it doesn't.

### Rule 2: Test control of ViewModel coroutines

Tests MUST be able to cancel ViewModel coroutines before `runTest` drains the scheduler. An infinite `delay()` loop in `viewModelScope` will hang `runTest` indefinitely (the test scheduler tries to drain unbounded virtual time). Three techniques satisfy this rule — pick by what the timer is for:

| Situation | Technique |
|-----------|-----------|
| App-scoped state (shared across screens, must outlive any single screen) | **Manager** (ADR-015): extract to a class that accepts `scope: CoroutineScope`. Production passes `applicationScope`; tests pass `this` or `backgroundScope` |
| Screen-scoped timer (countdown, debounce, animation tick) | **`ViewModelStore.clear()` in test**: keep timer in `viewModelScope`, clean up explicitly per test (`try { ... } finally { store.clear() }`) — mirrors production cleanup |
| Hybrid edge case where timer logic genuinely belongs in ViewModel but should outlive the screen | **Inject `scope: CoroutineScope`** into ViewModel constructor |

**Test scope choice — `this` vs `backgroundScope`:**
- `runTest { scope = this }` — use when the injected coroutine has a natural end (e.g., retry loop stops on success). `runTest` blocks until all children complete, so infinite loops would hang.
- `runTest { scope = backgroundScope }` — use when the coroutine never ends (e.g., periodic ticker). `backgroundScope` is cancelled at test completion without blocking.

**Why:** A blanket "no timers in ViewModels" rule is too broad. The core requirement is test cancellation, achievable three ways depending on the lifecycle of the state.

### Rule 3: Manager scope in production

App-scoped managers (FCM registration, staleness ticker) use `applicationScope` (singleton, lives for process lifetime).

**Why:** These managers must keep working across screen rotations, navigation, and backgrounding. Cost of an idle `delay()` loop is negligible.

**Tradeoff:** If a Manager ever does expensive polling/network work in its loop, switch to `SharingStarted.WhileSubscribed` so the loop pauses when nothing observes.

### Rule 4: State observation in tests

| Test target | Assertion |
|-------------|-----------|
| `StateFlow` (always direct exposure per Rule 6) | `.value` |
| Plain `Flow` (no current value semantics) | `.first()` for single value, `launch { .toList(buf) }` for sequence |
| `Flow` where intermediate emissions are part of the contract | sequence collection |

**Why:** With Rule 6, every ViewModel `StateFlow` is direct exposure of an owned `MutableStateFlow` field — `.value` is identical to what subscribers see. There's no separate subscription path that can fail silently.

### Rule 5: Fake state mutation

Apply by field type:

| Field type | Pattern |
|------------|---------|
| State others observe (Flow needed) | `private val _x = MutableStateFlow(...)` + `setX()` method |
| State others read but don't write (counters, last-call) | `var x: T = ...` with `private set`, OR call-list (preferred) |
| Result configuration mutated by tests | `setX()` method (no public `var`) |
| Truly immutable | `val` |

**Counter style — call-list preferred:**

```kotlin
// Good — call-list (richer, all val)
private val _pushCalls = mutableListOf<PushArgs>()
val pushCalls: List<PushArgs> get() = _pushCalls
val pushCount: Int get() = _pushCalls.size

// Acceptable — counter
var pushCount: Int = 0
    private set
```

Call-list lets tests assert on call arguments, not just count. Both fields stay `val`.

**Anti-pattern (forbidden):** public `var pushResult: NetworkResult<Unit>` — tests can write whenever, no single call site to grep, test ordering becomes load-bearing.

**Why:** The real risk of `var` is **public** mutability. `private set` removes that risk while keeping syntax minimal. `MutableStateFlow` is needed when observation is part of the contract; using it for plain counters adds ceremony without safety gain.

### Rule 6: ViewModel `StateFlow` construction

**Scope (updated by ADR-022):** this rule applies when the ViewModel materializes a cold `Flow` or transforms upstream data into a VM-local `StateFlow`. When the upstream is a repository-owned `StateFlow<T>` for state-y data, ADR-022 supersedes this rule: expose the repository's `StateFlow` by reference (no `_xState` mirror, no `init { collect }`). See ADR-022's "ViewModel shape" section.

The rule below is retained for: (a) VM-local presentation state backed by a `MutableStateFlow` (e.g., `_snoozeAction`, `_buttonState`), and (b) VM `StateFlow` derived from a cold `Flow` at the repository boundary (e.g., recent-event lists).

```kotlin
// Required pattern — for VM-local presentation state OR cold-Flow materialization
private val _snoozeAction = MutableStateFlow<SnoozeAction>(Idle)
override val snoozeAction: StateFlow<SnoozeAction> = _snoozeAction

// For cold-Flow materialization in the VM:
private val _recentEvents = MutableStateFlow<List<DoorEvent>>(emptyList())
override val recentEvents: StateFlow<List<DoorEvent>> = _recentEvents
init {
    viewModelScope.launch(dispatchers.io) {
        observeRecentEvents().collect { _recentEvents.value = it }
    }
}

// Forbidden pattern — stateIn in a ViewModel
override val snoozeState: StateFlow<SnoozeState> = observeSnooze()
    .stateIn(viewModelScope, SharingStarted.Eagerly, Loading)

// Also forbidden (per ADR-022) — mirroring a repo-owned StateFlow
private val _snoozeState = MutableStateFlow<SnoozeState>(Loading)
override val snoozeState: StateFlow<SnoozeState> = _snoozeState
init { viewModelScope.launch { observeSnooze().collect { _snoozeState.value = it } } }
// Instead: override val snoozeState: StateFlow<SnoozeState> = observeSnooze()
```

**Why:** `stateIn(Eagerly)` had subtle timing issues that caused real production bugs (auth state UI not updating, see PR #295). The mirror pattern caused the snooze-state propagation bug in android/164-168 (see `VIEWMODEL_SCOPING_ISSUE.md`). The explicit `MutableStateFlow` + `init.collect` pattern remains correct for VM-local presentation state and cold-Flow materialization; ADR-022 specifies passthrough for state-y data.

**Enforcement:** `ViewModelStateFlowCheckTask` bans `.stateIn(viewModelScope, ...)` in ViewModel files. ADR-022 extends this to ban `MutableStateFlow<T>` in a VM when `T` is a repo-owned domain state type (allowlist).

### Rule 7: Test data construction

| Type complexity | Pattern |
|-----------------|---------|
| Type has 3+ fields, used in multiple tests | Factory function with sensible defaults |
| Used once or trivially small | Inline literal |

```kotlin
// Factory — DoorEvent has 4+ fields, used everywhere
private fun makeDoorEvent(
    position: DoorPosition = DoorPosition.CLOSED,
    lastCheckInTimeSeconds: Long = 1000L,
    lastChangeTimeSeconds: Long = 900L,
    message: String = "",
): DoorEvent = DoorEvent(...)

// Inline — small/local
val token = GoogleIdToken("test-token")
```

### Bonus: Naming

- `Fake*` for test doubles (e.g., `FakeAuthRepository`)
- `InMemory*` for real implementations backed by collections that could ship in production (e.g., `InMemoryLocalDoorDataSource`)

### Migration tasks (mandatory)

These were tracked as separate follow-up PRs. The rules above apply immediately to new code; existing code was migrated to the rules in PRs #297–#305.

1. Add `setMain`/`resetMain` to `DefaultAppLoggerViewModelTest` (Rule 1) — DONE (#297)
2. Refactor `DoorViewModel` staleness ticker into `CheckInStalenessManager` (Rule 2 + ADR-015) — DONE (#299)
3. Audit all ViewModels for `stateIn` usage; convert to explicit pattern (Rule 6) — DONE (#298)
4. Add lint check banning `stateIn(viewModelScope, ...)` in ViewModel files (Rule 6 enforcement) — DONE (#300)
5. Convert fakes with public `var` for results to `setX()` methods (Rule 5) — DONE (#301, #302, #303, #304, #305, #308)
6. Adopt call-list pattern for new fakes; migrate counter-style fakes opportunistically (Rule 5) — DONE (#307, #309, #313, #314, #315, #316, #317, #318, #319, #320)

### Enforcement

- `ViewModelStateFlowCheckTask` (#300) bans `.stateIn(viewModelScope, ...)` in ViewModel files (Rule 6)
- `FakePublicVarCheckTask` (#310, #313) bans public `var` and public mutable collections on `Fake*` classes (Rule 5)

### Consequences

- One pattern per test concern — no "which style do I use" decisions for new code
- The auth state UI bug (PR #295) is now a structural impossibility — Rule 6 enforced
- Test code reads consistently across modules, easier to onboard
- All migration tasks (mandatory + opportunistic) completed 2026-04-16
- Every fake whose methods take meaningful args now exposes a `*Calls: List<…>` for argument assertions; counter-only fakes (no-arg methods) keep simple counter accessors

## ADR-018: Reactive Auth State — Use Platform Listeners, Not Imperative Polling

**Status:** Accepted

**Context:** Auth state UI stopped updating on sign-in/sign-out in `android/159`. The root cause had two parts:

1. **Latent bug (Oct 2024):** `FirebaseAuthRepository.signInWithGoogle()` called `refreshFirebaseAuthState()` which did `getIdToken(forceRefresh=true)` — a network round-trip. Right after `signInWithCredential()` completed, this network call could fail or return null, causing the repo to commit `Unauthenticated` even though sign-in succeeded.

2. **Trigger (PR #283, Apr 2026):** Replaced the direct `StateFlow` reference (`authRepository.authState`) with `observeAuthState(): Flow` + `stateIn(Eagerly)`. Before this change, even if the repo briefly committed the wrong state, the direct reference meant the UI saw subsequent corrections instantly. After the change, the `stateIn` layer faithfully propagated the wrong state with no self-correction mechanism.

**Decision:** Auth state propagation must be reactive — driven by the platform's auth state listener, not imperative polling.

### Rules

1. **Use the platform's listener mechanism.** Firebase has `AuthStateListener`; future providers have their own. Wrap it in `callbackFlow` and expose as `observeAuthUser(): Flow<AuthUserInfo?>` on the bridge.

2. **Commands are fire-and-forget.** `signInWithGoogle()` and `signOut()` call the bridge and return. The listener handles state propagation. No return values from commands.

3. **No `getIdToken(forceRefresh=true)` in the sign-in/sign-out path.** Use `getIdToken(forceRefresh=false)` (cached token) in the listener collector. Force-refresh only for `EnsureFreshIdTokenUseCase` when the cached token is expired.

4. **Don't wrap StateFlow in another StateFlow unnecessarily.** If a repository already exposes a `StateFlow`, pass the reference through — don't add `stateIn` or `MutableStateFlow + collect` unless converting from a cold `Flow`. Every intermediate subscription is a layer where bugs can hide.

5. **Instrumented UI tests for critical state propagation.** Unit tests with fakes can't catch races between the platform SDK and the reactive chain. Add Compose instrumented tests (`createComposeRule`) that verify StateFlow changes trigger UI recomposition for critical user-facing state (auth, door events).

### Consequences

- Sign-in/sign-out UI updates are driven by Firebase's `AuthStateListener` — no network call in the critical path, no race condition.
- `refreshFirebaseAuthState()` deleted. `getAuthState()` removed from the interface.
- The auth state bug is structurally impossible: the listener fires after every auth change, and the repo just maps the emission to `AuthState`.
- Compose UI tests (`AuthStateUIPropagationTest`) verify the full rendering chain on device.

## ADR-019: Repository Side-Effects on `externalScope`; State From Authoritative Server Responses

**Status:** Accepted

**Context:** The snooze card UI stayed stuck on "Door notifications enabled" after a user saved a snooze (android/164 through android/166). Root cause had two parts:

1. **VM-scope cancellation stranded the singleton.** `NetworkSnoozeRepository.snoozeStateFlow` is a `@Singleton`, but every write to it originated from a `viewModelScope.launch { ... }` in `DefaultRemoteButtonViewModel` (the init fetch, the polling `LaunchedEffect`, and the post-submit refetch). Ktor's `snoozeNotifications` / `fetchSnoozeEndTimeSeconds` rethrow `CancellationException` on cancellation. If the VM scope cancelled after the HTTP call returned but before the `when(result) { is Success -> snoozeStateFlow.value = ... }` branch ran, the write was skipped and the singleton's flow stayed on its previous value forever. Every future VM and subscriber saw the stale state.

2. **Post-submit state relied on a follow-up GET.** The VM POSTed a snooze, then explicitly called `fetchSnoozeStatusUseCase()` to GET the new state. This added a second network call, a second cancellation window, and a second race against server read-after-write consistency. The server already returns the full `SnoozeRequest` (with `snoozeEndTimeSeconds`) in the POST response body — the client was discarding authoritative data and reaching for it again.

**Decision:** Two complementary rules.

### Rule 1 — Repository side-effects that must survive UI lifecycle run on `externalScope`

Mirror the `FirebaseAuthRepository` pattern (ADR-018). Repositories that own shared mutable state must accept an `externalScope: CoroutineScope` (wired to `provideApplicationScope()`) and run every side-effecting call on it:

```kotlin
class NetworkSnoozeRepository(
    // ...
    private val externalScope: CoroutineScope,
) : SnoozeRepository {
    init { externalScope.launch { doFetchSnoozeStatus() } }

    override suspend fun fetchSnoozeStatus() {
        externalScope.launch { doFetchSnoozeStatus() }.join()
    }

    override suspend fun snoozeNotifications(...): Boolean =
        externalScope.async { doSnoozeNotifications(...) }.await()
}
```

- The caller still suspends (via `.join()` or `.await()`). If the caller's scope cancels, the `join`/`await` throws `CancellationException` in the caller — but the launched child is scoped to `externalScope`, **not** the caller, so it continues to completion.
- State writes happen on a scope that cannot be cancelled by UI lifecycle. The singleton is correct for every subsequent subscriber.
- VM `init` does **not** trigger the first fetch. The repository's own `init` does it, on `externalScope`.

### Rule 2 — Update state from the authoritative server response, not a follow-up GET

When the server's `POST` response already contains the domain-relevant data, parse it at the data-source layer and update state directly from it. Don't follow up with a `GET`.

```kotlin
// NetworkButtonDataSource
suspend fun snoozeNotifications(...): NetworkResult<Long> // snoozeEndTimeSeconds

// NetworkSnoozeRepository.doSnoozeNotifications — Success branch
is NetworkResult.Success -> {
    snoozeStateFlow.value = snoozeStateFromEndTime(result.data)
    true
}
```

- One network call, one interpretation function (`snoozeStateFromEndTime`), one write.
- No race between the `POST` write and the follow-up `GET`'s read.
- No client-side optimistic update. The end time comes from the server's response body.

### Non-rules (explicit)

- **No optimistic local writes to domain state.** The action-overlay optimistic text (`SnoozeAction.Succeeded.Set(optimisticEnd)`) is UI feedback, not state — it's ephemeral and auto-resets. The persistent `SnoozeState` only changes in response to real server data.
- **Don't use `.join()` from a non-caller scope as a bridge.** The pattern only works because the launched child is a *child of `externalScope`*, not a child of the caller. If you wrote `externalScope.coroutineContext.launch { ... }.join()` from a viewModelScope coroutine, the child would still be scoped to the viewModelScope's Job — defeating the purpose.

### Cross-platform dispatcher policy (KMP)

`externalScope` is a `CoroutineScope` built from `SupervisorJob()` + a dispatcher. Dispatcher choice:

- **Android/JVM:** `Dispatchers.Default` (CPU) or `Dispatchers.IO` (network/disk). `provideApplicationScope()` in `AppComponent.kt` uses `Dispatchers.IO`.
- **Native (iOS):** `Dispatchers.IO` falls back to `Default` on Kotlin/Native. `Dispatchers.Main` requires explicit setup. iOS DI should wire `provideApplicationScope()` over `Dispatchers.Default`.
- **Repositories do not hard-code dispatchers inside their bodies.** The dispatcher comes from the injected `externalScope`. This keeps `data/commonMain` code portable.
- **Tests** inject `UnconfinedTestDispatcher` or `StandardTestDispatcher` via the `externalScope` constructor parameter.

See ADR-022 for the shape of `StateFlow` ownership inside repositories that run on `externalScope`.

### Consequences

- The snooze Loading / stale-state class of bugs is structurally impossible: state only transitions via `externalScope`-owned writes, so no caller-scope cancellation can strand the singleton.
- Post-submit refetch removed; the repo writes `SnoozeState` directly from the POST response.
- VM no longer triggers the initial fetch in `init` — the repository does, on its own scope.
- Repository gains slightly more complexity (the `async`/`await` dance) but in exchange owns all its side effects in one file.
- Tests can reproduce the original bug by cancelling a `vmScope` mid-fetch and asserting the singleton reached the correct terminal state anyway (see `NetworkSnoozeRepositoryTest`).

### When to apply

Rule 1 applies to any repository that owns a singleton `MutableStateFlow` whose writes happen from suspend functions called across VM boundaries. Rule 2 applies to any `POST` endpoint that returns the updated entity — prefer the response body over a follow-up `GET`.

## ADR-020: Release-Build Hardening — Explicit ProGuard Keep Rules + Raw-Body Diagnostic Logs

**Status:** Accepted

**Context:** A production bug (snooze card not updating after save on android/167) passed every unit test, the JVM integration test wiring the real repository + ViewModel, AND the instrumented Compose test on an emulator — but still reproduced on the Play Store release build. Investigation found `proguard-rules.pro` was **entirely empty comments** — the project was relying solely on `kotlinx.serialization`'s bundled consumer rules with no belt-and-suspenders keeps for `data.ktor.**` response classes. A diagnostic probe (flipping `debug` to `isMinifyEnabled=true`) crashed the test runner with `NoClassDefFoundError: kotlin.LazyKt`, confirming R8 was aggressive enough to strip stdlib classes when keep rules were missing.

The failure mode is **silent**: a missing `@Serializable` keep leaves all `Long? = null` fields parsing as `null`, defaulting to `0L`. The app behaves as if the server returned "no snooze" — no exception, no log, no test failure. Emulator debug builds and JVM tests can't catch it because R8 runs only on release/minified builds.

**Decision:** Two complementary rules.

### Rule 1 — `proguard-rules.pro` keeps `kotlinx.serialization` infrastructure + all `data.ktor.**` classes

```proguard
# kotlinx.serialization infrastructure (belt-and-suspenders beyond bundled consumer rules)
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keep class <1>$$serializer { *; }
-if @kotlinx.serialization.Serializable class **
-keep class <1>$Companion { *; }

# Network response types — full class + members so R8 can't rename
-keep class com.chriscartland.garage.data.ktor.** { *; }
-keepclassmembers class com.chriscartland.garage.data.ktor.** { *; }

# Annotations must survive
-keepattributes *Annotation*, InnerClasses
```

### Rule 2 — Log raw body + parsed result at network-data-source boundaries producing state-critical data

When a data-source call decodes JSON into a domain-state value (snooze end time, auth token, FCM registration, etc.), log both the verbatim body and the parsed result:

```kotlin
val rawBody: String = response.body()
val body = json.decodeFromString(Response.serializer(), rawBody)
val endTime = body.snoozeEndTimeSeconds ?: 0L
Logger.i { "Snooze POST parsed endTime=$endTime rawBody=$rawBody" }
```

This costs one extra body read per response. In exchange, opaque on-device bugs become a single `adb logcat` line showing exactly what the server sent and what the client parsed — the only reliable ground-truth channel for production-only failures.

### Why logging instead of more tests

Tests can verify the happy path and known errors, but they cannot replicate the full production environment: real Firebase Functions runtime, Play-Store-signed APK with R8, real user account state. When a generated serializer is silently stripped, the nullable-default fallback masks it. The diagnostic log is the only tool that surfaces the failure mode from a deployed binary.

### Consequences

- Release-build parse failures surface as visible log lines, not silent no-ops.
- Adding new `@Serializable` types under `data.ktor.*` is automatically covered by the package keep.
- One extra body read on state-critical paths (trivial cost; body is already buffered).
- The instrumented Compose test runs the debug variant and **cannot** catch R8-specific regressions. R8 regressions surface only on real release builds on real devices, so `proguard-rules.pro` must stay conservative.

### When to apply

Any new `@Serializable` response type under `data.ktor.*` is covered by Rule 1's package keep — no action needed. When adding a new endpoint producing state-critical data (repository writes, auth, FCM tokens), add raw-body logging at the data-source boundary per Rule 2, even if happy-path tests all pass.

### Relationship to ADR-021 Rule 9

Rule 2 (raw-body logging at network-data-source boundaries) is a specific instance of ADR-021 Rule 9 (observability-first). Rule 9 is the general principle — "every state-critical write is observable in production logs." ADR-020 Rule 2 is its concrete form for JSON-decoding boundaries. ADR-022 extends the same idea to every `_state.value = ...` write at the repository layer.

## ADR-021: State Ownership and ViewModel Scoping Principles

**Status:** Accepted and enforced.

**Context:** The android/164-168 snooze-state propagation bug (see `VIEWMODEL_SCOPING_ISSUE.md`) exposed an unstated assumption in the architecture: that "there is one `RemoteButtonViewModel`." In fact there were three — Activity-scope (dead code), Home nav entry, Profile nav entry — each with its own `_snoozeState: MutableStateFlow<SnoozeState>` mirroring the singleton repository. Compose read from one; the others emitted independently. Under production conditions the read-side VM could silently miss an emission while the other two saw it.

The root issue is that *the same domain state existed as multiple copies*. Every VM held its own `MutableStateFlow` fed by an observer coroutine that mirrored the singleton. Three copies, three observers, three chances for something to go wrong. PR #354 worked around the symptom by writing directly to one of those copies from the suspend call's return value. The real fix is to stop copying.

This ADR writes down the principles that make "multiple ViewModels is fine" structurally safe.

### The core distinction — domain state vs. presentation state

- **Domain state** is the single source of truth about the world. Snooze end time, current door event, auth state, FCM registration. There is ONE. It lives in a `@Singleton` Repository. Every consumer observes the SAME object.
- **Presentation state** is local to a screen. Action overlays ("Saved!"), form input, dialog visibility, loading spinners. Each screen can legitimately have its own. It lives in a ViewModel.

Mixing them — a VM holding a `MutableStateFlow<SnoozeState>` that mirrors the repository — is the anti-pattern. The mirror has no information the repository doesn't, but it's a separate object that can diverge.

### Rules

**Rule 1 — Domain state lives in `@Singleton` repositories.**
If a piece of data must be consistent app-wide (snooze, auth, door events, FCM, server config), its authoritative owner is a `@Singleton` Repository that exposes a `StateFlow<T>`. Writes happen only inside the repository. Everyone else observes.

**Rule 2 — ViewModels expose repository `StateFlow`s by reference, not by mirror.**
```kotlin
// Yes — pass-through, same object
val snoozeState: StateFlow<SnoozeState> = observeSnoozeStateUseCase()

// No — creates a local copy that must be kept in sync
private val _snoozeState = MutableStateFlow<SnoozeState>(Loading)
val snoozeState: StateFlow<SnoozeState> = _snoozeState
init { viewModelScope.launch { observeSnoozeStateUseCase().collect { _snoozeState.value = it } } }
```

The "no" form is the anti-pattern that caused the bug. It's acceptable ONLY when the VM is transforming the data (combining with another flow, debouncing, etc.) or when the repository exposes a cold `Flow` that needs to be materialized. Neither applies for simple exposure.

**Rule 3 — ViewModel-local state is only for per-screen presentation.**
`_snoozeAction` (the "Saved!" overlay that auto-resets after 10s), `buttonState` (the confirm-flow machine), pending dialog visibility, text field input — these are legitimately per-screen. They stay in the VM as `MutableStateFlow` and don't need to survive the VM's lifecycle.

**Rule 4 — Multiple ViewModel instances are allowed; they must converge via the repository, not via synchronization.**
Home's `RemoteButtonViewModel` and Profile's `RemoteButtonViewModel` can both exist. They expose the same `snoozeState` (Rule 2 — the literal same object from the repo) and their own independent `snoozeAction` (Rule 3 — it's fine for Profile to show "Saved!" while Home shows nothing). No cross-VM communication is needed or allowed.

**Rule 5 — ViewModel instantiation scope is explicit at every call site.**
The principle is portable: every VM has exactly one owner declared at the call site, not inferred from the compositional default. Platform-specific mechanisms:

- **Android:** use `activityViewModel(owner) { ... }` (see `androidApp/.../di/ActivityViewModels.kt`) with an explicit owner, or rely on the default Nav3 per-entry scope and document that the VM is per-screen-instance-of-presentation-state.
- **iOS (future):** use `@StateObject` for per-screen ownership. Share across views via a parent view's `@StateObject` passed as `@ObservedObject` — avoid `@EnvironmentObject` for VMs holding domain pointers, since environment lookup is implicit.

No implicit `LocalViewModelStoreOwner.current` on Android; no implicit `@EnvironmentObject` on iOS.

**Rule 6 — Repositories are `@Singleton` in DI; ViewModels are not.**
`@Singleton` on a ViewModel is a code smell: it implies either (a) the VM is holding domain state (→ move it to a Repository) or (b) you're trying to share presentation state across screens (→ rethink — either it's not really presentation state, or the screens should share a single owner).

**Rule 7 — Background/domain work on `externalScope`; UI reactions on `viewModelScope`.**
Repository writes, state mutations, and background refresh happen on `externalScope` (= application scope). The VM's auto-reset timer, the "Saved!" fade, navigation events that a screen triggers — those are on `viewModelScope`. Mixing is how we got the stranded-state bug in ADR-019.

**Rule 8 — Dead ViewModel references are deleted, not tolerated.**
If `val x = viewModel { ... }` is never read, remove it. A ghost VM running observer coroutines for no subscriber is a resource leak and a debugging trap.

**Rule 9 — Observability-first.**
When preventing a class of bug is complex or expensive, prioritize the ability to identify and understand it from production logs. State-critical writes, lifecycle transitions, and error paths emit grep-able log lines.

Concretely:
- Every `_state.value = ...` write at the repository layer emits a `Logger.i/d` line identifying the flow, new value, and write source (see ADR-022 for the shape).
- Auth-state transitions log the direction (`Unauthenticated → Authenticated(email=...)` or vice versa).
- Error paths log the reason, not just "failed."
- Raw HTTP response bodies are logged at the data-source boundary for state-critical decodes (ADR-020 is the specific case).
- kermit (`co.touchlab.kermit:kermit`) is the logging library; KMP-safe.

Rationale: the android/164-168 snooze bug was invisible in logs until we added raw-body logging in PR #352. Every future cross-user leak, write-ordering race, or lifecycle-edge bug should be diagnosable from `adb logcat` (or iOS equivalent) without a debug build. Observability at state boundaries is cheap and removes the worst failure mode — "user reports a problem we cannot reproduce."

This does not replace prevention (Rules 1-8 still apply). When prevention is ambiguous or expensive (race conditions, rare races, device-specific timing), Rule 9 makes the bug observable so we can diagnose-then-fix rather than guess-then-guess.

### How the snooze path looks under these rules

```
NetworkSnoozeRepository (@Singleton)
  └─ snoozeStateFlow: MutableStateFlow<SnoozeState>           ← Rule 1
        │ writes happen on externalScope (Rule 7)
        │ exposed as: observeSnoozeState(): StateFlow<SnoozeState>
        │             (not widened to Flow — callers get .value)
        ▼
  ObserveSnoozeStateUseCase.invoke(): StateFlow<SnoozeState>  ← passthrough, Rule 2
        │ same object, no transform
        ▼
  DefaultRemoteButtonViewModel (N instances allowed, Rule 4)
        │ val snoozeState: StateFlow<SnoozeState> = observeSnoozeStateUseCase()
        │   ← same object every VM exposes (Rule 2)
        │ private val _snoozeAction = MutableStateFlow(Idle)
        │   ← per-VM presentation state (Rule 3)
        ▼
  Compose collectAsState — reads the SAME StateFlow regardless of which VM
```

After this change, three VMs don't multiply the truth. They multiply only their local overlays, which is what we want.

### Trade-offs and open choices

- **`StateFlow` in the domain interface.** Rule 2 asks repositories to expose `StateFlow<T>`, not `Flow<T>`. This is a stronger contract: it guarantees a current value, enables `.value` synchronous reads, and prevents downstream `stateIn` layers. The cost is one more thing a Repository implementation has to commit to. Worth it for domain state where "there is always a current value" is true by construction.
- **Per-screen VM that wraps shared state.** A VM that *only* forwards shared state and has no local presentation state is a sign the VM may not be needed at all — the Composable could consume the UseCase directly. Keep the VM if it owns local state; delete it if it's a passthrough shell.
- **Instance-state VMs (e.g., auth's SignInClient).** `AuthViewModel` holds a `GoogleSignInClient` that can't safely be duplicated per nav entry. It uses `activityViewModel(...)` explicitly. That's the correct pattern for "state is per-instance but that instance must be shared" cases — distinct from "state is domain-wide and lives in a repo."

### Consequences

- The workaround in PR #354 (direct write to `_snoozeState` from the return value) becomes unnecessary. Applying Rule 2 removes `_snoozeState` entirely; the VM's `snoozeState` points at the repo's flow.
- Tests that build a single VM directly are still valuable, but they can't catch scoping bugs alone. Add one integration test per feature that wires the full DI graph (see `SharedRepositoryUseCasesTest` as the model).
- New features that introduce shared state must add the Repository first, with its `StateFlow` observable, and only then expose it via UseCase + VM property. "Put it in a VM for now" is not a valid stepping stone.

### Enforcement

- Code review: reject `private val _xState = MutableStateFlow(...)` in a VM when `x` is a domain concept owned by a repository.
- Lint/architecture check: consider adding a Detekt rule that flags VM properties whose type is `StateFlow<T>` backed by a `MutableStateFlow<T>` where `T` matches a known domain state type. (Not automated yet — open task.)
- Every repository exposing state should export `StateFlow`, not `Flow`. If it exports `Flow`, document why (cold/transformed).

### Related

- `VIEWMODEL_SCOPING_ISSUE.md` — the background analysis that motivated this ADR.
- ADR-018 — reactive auth listener; an instance of Rule 1 applied to auth state.
- ADR-019 — repository side-effects on `externalScope`; an instance of Rule 7.
- `ActivityViewModels.kt:31` — `activityViewModel(...)` helper for explicit-owner scoping.

## ADR-022: `StateFlow` at the Repository Boundary for State-y Data

**Status:** **Accepted and enforced (2026-04-19, restored on android/174).**
Partially supersedes ADR-013.

**Enforcement:**
- `checkViewModelStateFlow` bans `MutableStateFlow<T>` in `*ViewModel.kt`
  when `T` matches `bannedStateTypesInViewModels` (`SnoozeState`,
  `AuthState`, `FcmRegistrationStatus`). Extend the list as more state-y
  types migrate.
- `checkSingletonGuard` requires `@Singleton` on every
  `provide<State-owning-Repository>` method in `AppComponent`.
- `ComponentGraphTest.*IsSingleton` runs `assertSame` on every `@Singleton`
  entry point — without this, `@Singleton` is a lie. See
  `docs/DI_SINGLETON_REQUIREMENTS.md`.

### Timeline: withdrawal and restoration

- **android/170** — shipped this ADR's original rollout (PR #358–#365).
  Users reported the snooze card title did not update after saving; the
  overlay (VM-local `SnoozeAction`) did. Matched the android/167 symptom.
- **android/171** — rollback to android/169's commit via `--confirm-hash`.
- **android/172** — VM-local `_snoozeState` mirror + direct write hotfix
  (PR #354 pattern). ADR-022 Rule 2 marked **Withdrawn** on the
  assumption that the pass-through pattern itself was unreliable.
- **android/173** — Phase 2f: fixed the kotlin-inject `@Singleton`
  scoping bug. The generated `InjectAppComponent.kt` had been 15 lines of
  empty subclass; no `@Singleton` provider was cached. Multiple
  `NetworkSnoozeRepository` instances coexisted — POST wrote to one
  flow, VM observed another. Pass-through had been correct all along;
  the DI was lying about singletons.
- **android/174** — removed the android/172 hotfix, restored the
  pass-through. On device: snooze still works. The DI fix was the
  complete root cause. ADR-022 Rule 2 **restored**.

Lesson: the debug instrumented tests passed because they construct a
single VM with a single manually-wired repo — the production multi-
instance DI graph was never exercised. `ComponentGraphTest.*IsSingleton`
is now the load-bearing regression guard; without it, a future
`AppComponent` drift would silently resurrect the same bug.

### Glossary

- **State-y data** (ADR-022) == **domain state** (ADR-021). Data that has a current value at any point in time; observable; owned by a `@Singleton` repository.
- **Event-y signals** (ADR-013, ADR-022) == transient signals where every intermediate value matters. Distinct from presentation state (ADR-021).
- **Presentation state** (ADR-021) — per-screen UI state (spinners, overlays, form input) that lives in ViewModels.

### Context

ADR-013 established that `StateFlow` is a state primitive (not an event primitive) and, based on the PushStatus SENDING→IDLE conflation bug, banned `StateFlow` below the ViewModel layer. Applied uniformly, that rule forced every repository exposing long-lived state to widen its interface to `Flow<T>` — even when the underlying storage was a `MutableStateFlow`. Downstream, ViewModels then held `private val _xState: MutableStateFlow<T>` fields that mirrored the repository via `init { observeX().collect { _xState.value = it } }`.

That mirror pattern produced the android/164-168 snooze-state propagation bug (see `VIEWMODEL_SCOPING_ISSUE.md`). Three `DefaultRemoteButtonViewModel` instances coexisted at runtime (Activity scope, Home nav entry, Profile nav entry) because of `rememberViewModelStoreNavEntryDecorator<Screen>()` and DI providers that weren't `@Singleton`. Each VM held its own `_snoozeState` mirror. Under production conditions the Profile-entry VM's observer coroutine failed to forward an emission, while the other two mirrors updated correctly — and Compose was bound to the one that silently missed it. PR #354 shipped a workaround (direct write to `_snoozeState` from the suspend return value). ADR-021 captured the principles. This ADR refines ADR-013 with the shape change that makes the bug structurally impossible.

ADR-013 was right about event-y signals. It was wrong to generalize to state-y data.

**Decision:** Distinguish data by nature and pick the observable type accordingly.

### The three categories

1. **State-y data with a current value.** Auth state, snooze state, current door event, server config, FCM registration status. There is always an answer to "what is the current value?" These belong in a `@Singleton` repository, owned as `MutableStateFlow<T>` internally, **exposed as `StateFlow<T>`** at the repository interface. UseCases that observe this data are passthroughs: `operator fun invoke(): StateFlow<T> = repository.xState`. ViewModels that present this data to Compose expose the same reference: `val xState: StateFlow<T> = observeXUseCase()`. **ViewModels do not hold a `MutableStateFlow<T>` mirror** of state-y data owned by a repository.

2. **Cold / list-y data.** Recent door events (Room query), log counts (Room query). There is no naturally-current value; new collectors trigger new upstream work. Exposed as `Flow<List<T>>` (or `Flow<T>`) at the repository interface. A screen's ViewModel may convert to `StateFlow` via `stateIn(viewModelScope, WhileSubscribed(5_000), initial)` at the UseCase boundary if it needs `.value` — this is the one legitimate place to wrap, because the cold flow genuinely has no current value.

3. **Event-y / transient signals.** Button state machine transitions, action-overlay reset timers, one-shot command outcomes, snackbar triggers. ADR-013's ban on `StateFlow` still applies — conflation drops signals. Use `suspend fun` return values for command completion (ADR-011, ADR-013), `Channel` or `MutableSharedFlow(replay=0)` for cross-component events, `MutableStateFlow` only inside a ViewModel when the signal is UI-local and its conflation is acceptable (e.g., the button state machine where the UI cares only about the latest state).

### StateFlow lifecycle pattern: always-on collector in repo `init`

State-y repositories own their `StateFlow` via an explicit always-on collector pattern, not via the `stateIn` operator. This is the shape `NetworkSnoozeRepository` already uses after ADR-019:

```kotlin
class NetworkSnoozeRepository(
    private val externalScope: CoroutineScope,
    // ...
) : SnoozeRepository {
    private val _snoozeState = MutableStateFlow<SnoozeState>(SnoozeState.Loading)
    override val snoozeState: StateFlow<SnoozeState> = _snoozeState

    init {
        // Always-on collector: owns the translation from upstream (Room, listener,
        // HTTP fetch) to the exposed StateFlow. Lifetime is process-lifetime via
        // externalScope — no subscriber-count thrash, no WhileSubscribed traps.
        externalScope.launch {
            upstreamCurrentEvent.collect { _snoozeState.value = mapToState(it) }
        }
    }

    override suspend fun snoozeNotifications(...): AppResult<SnoozeState, ActionError> = ...
}
```

**Do not use `stateIn(externalScope, WhileSubscribed(5_000), initial)` for repo-owned state.** `WhileSubscribed` cancels the upstream collector when subscriber count drops to zero for the timeout window. Under production conditions (navigation, backgrounding, the multi-VM pattern from `VIEWMODEL_SCOPING_ISSUE.md`), subscriber count thrashes, and an emission that lands during the dead window is silently lost on upstream restart. This is the exact class of bug ADR-022 is trying to eliminate — it would just move from the ViewModel layer to the repository layer.

- `stateIn(externalScope, Eagerly, initial)` is safer than `WhileSubscribed` but less explicit about ownership than the collector-in-`init` pattern.
- The always-on collector makes the translation logic a named function (`mapToState`), makes the write points easy to audit, and is trivial to lint (see Enforcement).

### Interface shapes

```kotlin
// State-y data — repository owns the StateFlow
interface SnoozeRepository {
    val snoozeState: StateFlow<SnoozeState>
    suspend fun snoozeNotifications(...): AppResult<SnoozeState, ActionError>
    suspend fun fetchSnoozeStatus()
}

interface AuthRepository {
    val authState: StateFlow<AuthState>
    suspend fun signInWithGoogle(...): AuthState
    suspend fun signOut()
}

// Mixed — state-y current + cold list
interface DoorRepository {
    val currentDoorEvent: StateFlow<DoorEvent?>     // state-y
    fun observeRecentEvents(): Flow<List<DoorEvent>> // list-y, cold
    suspend fun fetchCurrentDoorEvent(): AppResult<DoorEvent, ActionError>
}

// Event-y — ADR-013's rules unchanged
interface RemoteButtonRepository {
    suspend fun pushButton(...): AppResult<Unit, ActionError>
    // NOT exposed: val pushStatus: StateFlow<PushStatus>  (the ADR-013 bug)
}
```

### ViewModel shape under this ADR

```kotlin
class DefaultRemoteButtonViewModel(
    observeSnooze: ObserveSnoozeStateUseCase,
    // ...
) : ViewModel() {
    // Domain state — passthrough reference to the repo's StateFlow.
    // No _snoozeState mirror, no init { collect } observer.
    val snoozeState: StateFlow<SnoozeState> = observeSnooze()

    // Presentation state — stays in the VM. Event-y per ADR-013.
    private val _snoozeAction = MutableStateFlow<SnoozeAction>(Idle)
    val snoozeAction: StateFlow<SnoozeAction> = _snoozeAction

    // ButtonStateMachine output — event-y machine, VM-local.
    val buttonState: StateFlow<RemoteButtonState> = stateMachine.state
}
```

### Worked example: snooze has both state-y and event-y fields

A single user action (tapping "Save 1 hour") drives both kinds of data on the same feature, and they belong in different places:

- `SnoozeState` is **state-y**. There's a current value — the user is either snoozing (until X) or not. Every screen that shows snooze reads the SAME value. Lives in `SnoozeRepository.snoozeState: StateFlow<SnoozeState>`. Passthrough through UseCase. Passthrough through every VM.
- `SnoozeAction` (`Idle / Sending / Succeeded.Cleared / Succeeded.Set / Failed.*`) is **event-y presentation state**. A "Saved!" overlay fades after 10 seconds. Each screen has its own (Profile's "Saved!" doesn't apply to Home). Lives in `DefaultRemoteButtonViewModel._snoozeAction: MutableStateFlow<SnoozeAction>`. Per-VM.

The separation is the reason ADR-022 permits multiple VMs (Rule 4 in ADR-021) without state divergence: they share the state-y data by reference through the repository singleton, and they legitimately hold independent event-y presentation state for their own screen.

### Write-ordering guarantees (last-writer-wins)

Multiple writers can target the same repository `StateFlow` concurrently: an always-on collector driven by upstream, a `suspend` command's success branch, a post-action refetch. All run on `externalScope` (per ADR-019). `externalScope`'s dispatcher is a thread pool; two coroutines can resume on different threads and call `_state.value = ...` in undefined order. `MutableStateFlow.value` writes are atomic (no torn state) but not strictly ordered.

**The rule:** accept last-writer-wins semantics for repository `StateFlow` writes. Do not add a `Mutex` or `limitedParallelism(1)` dispatcher globally. If a specific repository proves to need strict write ordering (observed race in production), add a `Mutex` **inside that repository** around writes to its `_state.value = ...`.

Justification: in practice, concurrent writers target the same server-side truth. Even if a stale read overwrites a fresh write for one poll cycle, the next poll converges. The real-world failure mode is a ~60-second UI flash, not a permanent wrong value. Adding mutex ceremony at every write site across every repo is architectural tax for a theoretical concern — and would have to be added in every new repo by convention. State-write logging (see Observability below) makes any real instance of the race identifiable in production via `adb logcat`, which is more actionable than preventing it at the cost of complexity.

### Sign-out and user-scoped state

Singleton repositories outlive user sessions. State written during user A's session is still in memory when user B signs in on the same device. The rule:

**Repositories that own user-scoped state must observe `AuthRepository.authState` on `externalScope` and reset their `_state.value` to a neutral initial value on transition to `AuthState.Unauthenticated`.** User-scoped state includes per-user caches (snooze, user-specific FCM tokens), anything the user can only change while signed in. Global state (server config, device-wide door events) is not user-scoped.

**Implementation timing:** this rule is documented now but its per-repo implementation is Phase 2 work. Our current exposure is narrow (home-IoT app, typically single-user per device). Adding clearing logic to every repo in Phase 1 expands scope; adding the log line that would make cross-user leakage detectable is Phase 1 (see Observability). If the log ever shows evidence of leakage, Phase 2 prioritizes the retrofit.

### Observability — state-write logging

Per ADR-021 Rule 9 (observability-first), every `_state.value = ...` write at the repository layer emits a log line with flow name, new value, and write source. Shape:

```kotlin
private val _snoozeState = MutableStateFlow<SnoozeState>(Loading)
override val snoozeState: StateFlow<SnoozeState> = _snoozeState

init {
    externalScope.launch {
        upstreamCurrentEvent.collect { event ->
            val newState = mapToState(event)
            _snoozeState.value = newState
            Logger.i { "snoozeStateFlow <- $newState (source=upstream)" }
        }
    }
}

override suspend fun snoozeNotifications(...): AppResult<SnoozeState, ActionError> {
    // ... HTTP POST ...
    val newState = SnoozeState.Snoozing(serverResponse.endTime)
    _snoozeState.value = newState
    Logger.i { "snoozeStateFlow <- $newState (source=POST)" }
    return AppResult.Success(newState)
}
```

This generalizes ADR-020's raw-body logging pattern (a specific instance) to all state-critical writes. The cost is a few log lines per event; the benefit is `adb logcat | grep snoozeStateFlow` shows the entire state trajectory for diagnosis. Race conditions, stale caches, and cross-user leakage become visible from the log even when we can't prevent them.

kermit (`co.touchlab.kermit:kermit`) is KMP-safe; this rule applies on Android today and iOS when it lands.

### What this supersedes from ADR-013

- ✗ "StateFlow lives only in ViewModels." **Superseded** for state-y data.
- ✗ "No UseCase, Repository, or data layer may expose StateFlow without explicit approval." **Superseded** for state-y data. The default shape for state-y data IS `StateFlow`.
- ✓ "Every StateFlow has an initial value." **Retained.**
- ✓ "If every intermediate value matters (SENDING → IDLE), do not use StateFlow — use suspend return, Channel, or callback." **Retained and reinforced.**
- ✓ "ADR-011 — `suspend fun` return = completion signal. Don't add a parallel StateFlow for the same information." **Retained.**
- The "Current violations to review" list (AuthRepository.authState, SnoozeRepository.snoozeState as StateFlow) is resolved: they become StateFlow by design.

### Testing story

Fake repositories for state-y data follow a uniform shape:

```kotlin
class FakeSnoozeRepository : SnoozeRepository {
    private val _snoozeState = MutableStateFlow<SnoozeState>(NotSnoozing)
    override val snoozeState: StateFlow<SnoozeState> = _snoozeState
    fun setSnoozeState(s: SnoozeState) { _snoozeState.value = s }
    override suspend fun snoozeNotifications(...): AppResult<SnoozeState, ActionError> = ...
}
```

Tests mutate `_snoozeState.value` directly; observers see every distinct value via StateFlow's per-subscriber replay. No `Dispatchers.setMain`, no coroutine scheduling tricks, no VM-scope test plumbing for pure observation paths.

**Reference-identity tests** lock in Rule 2 from ADR-021:

```kotlin
@Test fun vmSnoozeStateIsSameInstanceAsRepoStateFlow() {
    val fake = FakeSnoozeRepository()
    val vm = buildVm(fake)
    assertSame(fake.snoozeState, vm.snoozeState,
        "VM must expose repo's StateFlow by reference (ADR-022)")
}
```

Without `assertSame`, the no-mirror rule is unenforced — a test that compares `.value` passes even when a mirror has been re-introduced.

Feature-level integration tests wiring the real repository + multiple subscribers (per `SharedRepositoryUseCasesTest`) catch scoping regressions.

### iOS bridging: Skie

The project commits to **Skie** (`co.touchlab.skie`) as the iOS bridge for `StateFlow<T>` → `@Published` / `AsyncSequence` mapping when the iOS target lands. This is the same approach battery-butler uses. Constraints this places on shared types:

- `T` must be a value-type-friendly shape Skie can project: `data class`, sealed hierarchies, enum. Avoid generic interfaces in `T` that Skie can't bridge.
- When adding a new state-y type under `domain/model`, validate Skie compatibility before using it as a `StateFlow<T>` domain property.

Skie adoption is tracked as Phase 38 (see `MIGRATION.md`).

### Consequences

- PR #354's direct-write workaround (`_snoozeState.value = result.data` inside `snoozeOpenDoorsNotifications`) becomes unnecessary — the VM no longer has a `_snoozeState`. The line gets removed as part of the migration (see `MIGRATION_PLAN.md`).
- Repositories commit to a tighter contract: "I always have a current value for this state." Fine for state-y data by definition.
- `WhileSubscribed` is banned for repo-owned StateFlow. The always-on collector in `init` is the required pattern.
- Last-writer-wins write ordering is accepted by default; per-repo `Mutex` is opt-in for observed races.
- User-scoped state clearing on sign-out is a documented rule; per-repo implementation is Phase 2.
- Every repository state-write emits a log line. Grep-able observability replaces exhaustive prevention for rare race/lifecycle corner cases.

### Enforcement

- **Convention test:** `SingletonGuardTask` (buildSrc) extends to require any class that implements a `*Repository` interface and owns a `MutableStateFlow` field be `@Singleton` in `AppComponent`.
- **Lint extension:** `ViewModelStateFlowCheckTask` extends to ban `MutableStateFlow<T>` properties in a ViewModel when `T` appears in a curated allowlist of domain state types (starting with `SnoozeState`, `AuthState`, `DoorEvent?`, `ServerConfig?`, `FcmRegistrationStatus`). Structural match, not just regex on `stateIn`. Handles the subtle `.map { it }` / `.distinctUntilChanged()` mirror variants by failing on any `StateFlow<T>` VM property whose type matches the allowlist and whose RHS is not a direct UseCase invocation.
- **Reference-identity test:** each state-y feature adds an `assertSame(fakeRepo.xState, vm.xState)` test. Without this test, the lint rule protects the syntax but the test protects the semantics.
- **State-write log convention:** every `_state.value = ...` in a `data/**/repository/**` file has a `Logger.*` call on the same statement or next line. Enforced by code review initially; upgrade to lint after the migration settles.

### Related

- `VIEWMODEL_SCOPING_ISSUE.md` — the bug post-mortem that motivated this.
- `MIGRATION_PLAN.md` — rollout sequence.
- ADR-011 — suspend return values carry completion signals.
- ADR-013 — event-y signals stay out of StateFlow (retained; see inline supersede marks).
- ADR-017 Rule 6 — scoped (see amendments) so the cold-Flow materialization pattern no longer conflicts with passthrough for state-y data.
- ADR-018 — reactive auth listener. This ADR makes the "AuthRepository.authState as StateFlow" choice explicit instead of a pending review.
- ADR-019 — externalScope for repository side-effects. State writes in repos happen on externalScope.
- ADR-020 — raw-body diagnostic logging. The state-write log convention generalizes this.
- ADR-021 — state ownership principles. This ADR is the concrete shape; Rule 9 (observability) anchors the logging convention.
