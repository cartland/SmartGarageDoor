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
