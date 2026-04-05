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
- Navigation3 (too experimental for this project's needs)
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

## ADR-008: Parsing Objects Over Generic Extension Functions

**Status:** Accepted

**Context:** The codebase has extension functions on generic types like `Map<K, V>.asDoorEvent()` for parsing FCM payloads and network responses. These pollute autocomplete, make failure modes unclear, and create implicit coupling between unrelated types.

**Decision:** Prefer parser objects with explicit function signatures over extension functions on generic receiver types. Group related parsing functions in an `object {}` rather than bare top-level functions.

**Example — current (avoid):**
```kotlin
private fun <K, V> Map<K, V>.asDoorEvent(): DoorEvent? { ... }
```

**Example — preferred:**
```kotlin
object FcmPayloadParser {
    fun parseDoorEvent(data: Map<String, String>): DoorEvent? { ... }
}
```

**Consequences:**
- Parsing logic is discoverable via the parser object, not hidden on generic types
- Function signatures are explicit about expected input types (`Map<String, String>` not `Map<K, V>`)
- Failure modes are clear at the call site
- Migrate existing code only when safe — especially FCM-related code where silent breakage is the biggest risk
