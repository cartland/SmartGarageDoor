# Migration Roadmap

Target: Align with [battery-butler](https://github.com/cartland/battery-butler) technology stack over several independent refactoring projects. Each phase is a separate PR or series of PRs. No phase depends on a later phase.

**Rule:** Focus on finishing each phase completely before starting the next. Update this document with the commit hash when a phase is complete.

## Phase 1: Testing Infrastructure — COMPLETE

**Goal:** Make CI a reliable deployment gate.

### 1.1 Add Detekt with Zero Tolerance — `d51f6f3` (#29)
- Added `io.gitlab.arturbosch.detekt` plugin with `maxIssues: 0`
- Configured `detekt.yml`: disabled rules incompatible with Compose (LongMethod, LongParameterList), disabled MaxLineLength (redundant with ktlint)
- Runs in CI and `./scripts/validate.sh`

### 1.2 Test Coverage Enforcement — `631ede4` (#34)
- Custom `buildSrc` Gradle task (`checkTestCoverage`) scans for `*ViewModel` and `*Repository` classes
- Fails build if no matching `*Test.kt` file exists
- Supports `// @NoTestRequired` inline exemptions and `test-coverage-exemptions.txt`
- 6 classes currently exempt (pending Firebase wrapper refactor)

### 1.3 Migrate from Mocks to Fakes — `e12d965` (#30)
- Created `FakeDoorRepository`, `FakePushRepository`, `FakeAuthRepository` in `testcommon/`
- `FakeLocalDoorDataSource` used in `DoorRepositoryTest`
- Existing Mockito tests remain; new tests prefer fakes

### 1.4 Enable Android Lint in CI — `8bc844d` (#19)
- Added `./gradlew :androidApp:lint` to CI test job
- Added to `./scripts/validate.sh`
- Passes clean with no existing issues

## Phase 2: Clean Architecture

**Status:** In progress.

**Goal:** Separate business logic into testable layers.

### 2.1 Extract Domain Module — COMPLETE
- Created `domain/` module (pure Kotlin, no Android dependencies) — `5453e27` (#43)
- Defined domain model types and repository interfaces — `a9fe332` (#44)
- Migrated all androidApp imports to use domain types — `258939f` (#45)
- Created `DoorEventEntity` for Room persistence boundary with round-trip mappers
- Added domain tests: `DoorPositionTest` (server string contract), `AuthModelTest`
- Added `DoorEventEntityTest` for entity ↔ domain mapping correctness
- Domain module types: `DoorEvent`, `DoorPosition`, `LoadingResult`, `AuthState`, `User`, `FirebaseIdToken`, `GoogleIdToken`, `DoorFcmState`, `DoorFcmTopic`, `FcmRegistrationStatus`, `RequestStatus`, `PushStatus`, `SnoozeRequestStatus`, `ServerConfig`
- All repository interfaces consolidated to domain module — `4a56f8e` (#59), `8f593bb` (#60), `9546278` (#62)

### 2.2 Extract UseCase Layer — COMPLETE
- All ViewModel business logic extracted into UseCases with `operator fun invoke()` syntax
- `EnsureFreshIdTokenUseCase` — token refresh logic (6 tests) — `79d3a6b` (#48)
- `PushRemoteButtonUseCase` — auth + push delegation (6 tests) — `529d176` (#68)
- `SnoozeNotificationsUseCase` — auth + snooze delegation (6 tests) — `bec753f` (#69)
- `FetchCurrentDoorEventUseCase` / `FetchRecentDoorEventsUseCase` — repository delegation (4 tests) — `31cf810` (#70)
- `FetchFcmStatusUseCase` / `RegisterFcmUseCase` / `DeregisterFcmUseCase` — FCM operations (7 tests) — `dc2d0ce` (#71)
- Shared `DoorFcmState.toRegistrationStatus()` mapping extracted (was duplicated 3x)

**Next step (Phase 3):** Remove direct Repository references from ViewModels. ViewModels should only depend on UseCases. UseCases take Repositories via constructor injection (kotlin-inject wires this). This follows the battery-butler pattern where the DI layer enforces ViewModel→UseCase→Repository separation.

### 2.3 Separate Data Modules — COMPLETE
- Created `data/` module with pure Kotlin data source interfaces — `19c8793` (#74), `af0c9c8` (#75)
  - `LocalDoorDataSource` — abstracts Room
  - `NetworkDoorDataSource` — abstracts Retrofit/Ktor for door events
  - `NetworkConfigDataSource` — abstracts server config API
  - `NetworkButtonDataSource` — abstracts push/snooze API
- Wired all Repositories to data interfaces:
  - `DoorRepository → NetworkDoorDataSource + LocalDoorDataSource` — `26d2f41` (#78)
  - `PushRepository → NetworkButtonDataSource` — `c290171` (#79)
  - `ServerConfigRepository → NetworkConfigDataSource` — `695d19e` (#80)
- Created Retrofit adapter implementations (`RetrofitNetworkDoorDataSource`, `RetrofitNetworkButtonDataSource`, `RetrofitNetworkConfigDataSource`)
- Consolidated `config.model.ServerConfig` into `domain.model.ServerConfig`
- No Repository imports Retrofit or Room directly — swapping to Ktor means only new data source implementations

**Remaining (optional, can defer to Phase 5/KMP):**
- Extract `data-local/` as separate Gradle module (Room entities, DAOs, DatabaseLocalDoorDataSource)
- Extract `data-network/` as separate Gradle module (Retrofit service, response DTOs, adapters)
- These are structural — the abstraction boundary is already enforced by interfaces

## Phase 3: DI Migration (Hilt to kotlin-inject) — COMPLETE

**Goal:** KMP-compatible dependency injection.

See `docs/DI-MIGRATION.md` for the full migration guide with before/after code examples.

### 3.1 Add kotlin-inject — `65f89c8` (#86)
- Added `me.tatarka.inject:kotlin-inject-runtime` 0.9.0 + KSP compiler
- Created `@Singleton` scope annotation and empty `AppComponent`
- Both Hilt and kotlin-inject coexisted during migration

### 3.2 Migrate ViewModels — `a7c2ade` (#87), `953beb6` (#89), `9312b01` (#90)
- AppSettingsViewModel (#87), AuthViewModel (#89), DoorViewModel + RemoteButtonViewModel (#90)
- Pattern: `rememberAppComponent()` + `viewModel { component.xxxViewModel }`
- Activity-scoped ViewModels via `activityViewModel()` helper for shared instances
- AppLoggerViewModel + FCMService also migrated (#91)

### 3.3 Remove Hilt — `c5d9d58` (#96)
- Deleted all 15 `@Module` classes, `@HiltAndroidApp`, `@AndroidEntryPoint`
- Removed Hilt Gradle plugin and all 3 dependencies
- Zero `dagger`/`hilt` references remain in source code
- Net -257 lines

### 3.4 Fix ViewModel instance sharing — `0aebfb1` (#93), `28151ce` (#95)
- **Bug:** `component.xxxViewModel` creates new instances. ViewModels with instance state
  (SignInClient, FCM status, log counts) must share the same instance across Activity and Compose.
- **Fix:** `activityViewModel()` helper creates ViewModels in Activity's ViewModelStore.
  Compose receives them as nullable params with component fallback for Previews.

## Phase 4: Network Migration (Retrofit to Ktor HTTP) — COMPLETE

**Goal:** KMP-compatible HTTP client.

### 4.1 Add Ktor Client + Create Ktor Data Sources — `158c176` (#100)
- Added Ktor 3.1.3 (client-core, client-okhttp, content-negotiation, logging)
- Added kotlinx-serialization-json 1.8.1 + Gradle plugin
- Created `KtorNetworkDoorDataSource`, `KtorNetworkConfigDataSource`, `KtorNetworkButtonDataSource`
- Each uses `@Serializable` DTOs with `ignoreUnknownKeys` — only fields actually used
- Ktor implementations bypass `GarageNetworkService` entirely — implement data source interfaces directly
- Swapped DI wiring in `AppComponent` from Retrofit to Ktor

### 4.2 Remove Retrofit — `759443e` (#101)
- Deleted `GarageNetworkService.kt` (Retrofit interface + inline value classes)
- Deleted 3 Retrofit adapter implementations, 6 Moshi `@JsonClass` DTOs
- Deleted `retrofit2.pro`, `moshi.pro` ProGuard rules
- Removed 7 dependencies: retrofit2, moshi, moshi-kotlin, moshi-codegen, converter-moshi, okhttp3-logging-interceptor
- Migrated `GarageNetworkServiceTest` and `RoomSchemaTest` from Moshi to kotlinx.serialization
- Net -841 lines
- Zero `retrofit2`/`moshi`/`okhttp3` imports remain in source code

## Phase 5: KMP Preparation — COMPLETE

**Goal:** Structure code for multiplatform, Android target only.

### 5.1 Convert domain/ and data/ to KMP — `82e1ed7` (#120)
- Both modules: `kotlin("jvm")` → `kotlin("multiplatform")` + `com.android.library`
- domain/: 13 source files + 2 test files → `src/commonMain/kotlin/` + `src/commonTest/kotlin/`
- data/: 4 interface files → `src/commonMain/kotlin/`
- Tests migrated from JUnit to `kotlin.test`
- Zero Android imports — all code in commonMain

### 5.2 Future: Move more code to commonMain
Not needed for Android-only. When adding a second platform target:
- UseCases (4 pure Kotlin files) → new `usecase/` KMP module
- Ktor data sources → `commonMain` (already KMP-compatible)
- `expect`/`actual` for Room (DatabaseFactory) and Firebase Auth
- ViewModels → `commonMain` with Compose Multiplatform

## Phase 6: Screenshot Tests — COMPLETE

**Goal:** Automated app screenshot generation for Play Store and documentation.

### 6.1 Set Up Compose Screenshot Testing — `56e742a` (#107)
- Separate `android-screenshot-tests` module with AGP Screenshot Plugin 0.0.1-alpha12
- `screenshotTest` source set with `@PreviewTest` + `@Preview` pattern
- OOM prevention: blocks single-invocation runs, sequential script required
- Screenshot compilation in CI and `validate.sh` (not generation)
- Claude hook blocks direct screenshot Gradle tasks

### 6.2 Screenshot Tests — `56e742a` (#107)
- 17 preview tests (light + dark = 34 images)
- Screens: Home, History, Profile
- Components: DoorStatusCard, RemoteButton, ErrorCard, UserInfoCard, LogSummaryCard, SnoozeNotificationCard
- Garage door states: Closed, Open, Opening, Closing, Midway, GarageIcon

### 6.3 Scripts and Tooling — `56e742a` (#107)
- `scripts/generate-android-screenshots.sh` — sequential generation (avoids OOM)
- `scripts/generate-android-screenshot-gallery.sh` — auto-generates SCREENSHOT_GALLERY.md
- `/update-android-screenshots` Claude skill
- Screenshots do NOT block CI — compilation only, generation on demand

## Phase 7: Instrumented Tests — COMPLETE

**Goal:** Runtime tests that catch failures unit tests miss.

### 7.1 Gradle Managed Device — `b509fc3` (#105)
- Pixel 6, API 34, aosp-atd
- Post-merge CI workflow with failure tracking via GitHub issues
- KVM acceleration on GitHub Actions

### 7.2 Room Database Sanity — `b509fc3` (#105)
- 6 tests: database creates, DAOs accessible, insert/read DoorEvent and AppEvent, replaceAll

### 7.3 kotlin-inject Component Graph — `b509fc3` (#105)
- 6 tests: AppComponent creates, all 5 ViewModels resolve

### 7.4 Navigation Smoke — `56e742a` (#106)
- 6 tests: Home displays, all tabs visible, navigate History/Profile/Home, sequential navigation

## Phase 8: UseCase Refactor + Module Extraction

**Goal:** Clean UseCase signatures, then extract to shared KMP module.

### 8.1 Fix UseCase Signatures — COMPLETE (#127)

Several UseCases pass repositories as `invoke()` arguments instead of constructor parameters. This couples callers to wiring details and blocks DI.

| UseCase | `invoke()` args → constructor params |
|---------|--------------------------------------|
| `EnsureFreshIdTokenUseCase` | `authRepository: AuthRepository` |
| `PushRemoteButtonUseCase` | `authRepository`, `pushRepository` |
| `SnoozeNotificationsUseCase` | `authRepository`, `pushRepository` |

**Pattern:** `invoke()` should only take request-specific arguments (token, duration), not dependencies. Dependencies go in the constructor so DI can wire them.

### 8.2 Extract UseCase Module — COMPLETE (#128)

- Created `usecase/` KMP module (commonMain, depends on `:domain` only)
- Move 5 pure Kotlin use cases from `androidApp/usecase/`
- `RegisterFcmUseCase` stays in `androidApp` (depends on `android.app.Activity`)
- Tests move to `usecase/src/commonTest/kotlin/` with `kotlin.test`

## Phase 9: Repository Implementations in Data Module — COMPLETE

**Goal:** Move pure Kotlin repository implementations into `data/` module.

- `ServerConfigRepository` → `data/src/commonMain/` (pure Kotlin)
- `DoorRepositoryImpl` → `data/src/commonMain/` (depends on data source interfaces)
- `PushRepositoryImpl` → `data/src/commonMain/` (depends on data source interfaces)
- Ktor data sources → `data/src/commonMain/` with expect/actual for HTTP engine
- Firebase repos (`AuthRepository`, `DoorFcmRepository`) stay in `androidApp`
- Room code (`DatabaseLocalDoorDataSource`, DAOs) stays in `androidApp`

## Phase 10: Shared ViewModel/Presentation Logic — IN PROGRESS

**Goal:** Share ViewModel business logic across platforms.

### 10.1 Extract RemoteButtonStateMachine — COMPLETE
- Extracted pure state machine from `DefaultRemoteButtonViewModel` into `RemoteButtonStateMachine` in `usecase/src/commonMain/`
- Takes `Flow<PushStatus>` + `Flow<DoorPosition>` inputs, produces `StateFlow<RequestStatus>`
- 19 tests in `usecase/src/commonTest/` using `kotlin.test` (no Mockito)
- ViewModel becomes thin wrapper delegating to state machine
- Moved `DispatcherProvider` interface to `domain/src/commonMain/`
- Replaced `java.time.Duration` with `kotlinx.coroutines.delay(millis)` for KMP compatibility

### 10.2 DoorStatusPresenter — DEFERRED
- DoorViewModel logic is mostly flow collection + FCM (Android-specific)
- Not enough pure logic to justify extraction yet

### 10.3 Shared ViewModel Module — DEFERRED
- ViewModels still depend on `androidx.lifecycle.ViewModel` and `viewModelScope`
- Full extraction to KMP module deferred until iOS target is added

## Phase 11: Platform Abstractions (expect/actual)

**Goal:** Define platform boundary contracts for iOS.

- `expect class HttpClientFactory` — Android: OkHttp, iOS: Darwin engine
- `expect class AuthBridge` — Android: Firebase Auth, iOS: native auth
- `expect class PushNotificationBridge` — Android: FCM, iOS: APNs
- `expect class LocalStorageBridge` — Android: Room + DataStore, iOS: CoreData + UserDefaults
- iOS `actual` implementations come when adding iOS target

## Phase 12: Navigation 3 (Nav3)

**Goal:** Modern type-safe navigation.

- Migrate from current navigation to Compose Navigation 3
- Type-safe routes as `@Serializable` data classes in shared module
- Declarative `NavHost` + `composable<Route>` pattern
- Enables sharing route definitions with iOS

## Phase 13: iOS Target (Future)

**Goal:** Add iOS framework target to shared modules.

- Add `iosX64()`, `iosArm64()`, `iosSimulatorArm64()` targets
- Implement `actual` declarations for iOS
- Create SwiftUI app consuming shared ViewModels via KMP framework

## Phase 14: Typed Error System

**Goal:** Replace silent failures with typed error hierarchy.

- `Result<D, E : AppError>` sealed interface in `domain/`
- `AppError` base with `message` and `cause`
- Hierarchies: `DataError.Network`, `DataError.Database`, `AuthError`
- Extensions: `map`, `mapError`, `getOrNull`, `flatMap`, `onSuccess`, `onError`
- Migrate repos/use cases from nullable/Boolean to `Result<T, E>`

## Phase 15: KMP Logging (Kermit)

**Goal:** Multiplatform logging to replace `android.util.Log`.

- Add Kermit dependency to shared modules
- Replace `Log.d/e/w` with `Logger.d/e/w` (API is nearly identical)
- No custom abstraction — Kermit is multiplatform-first
- Enables logging in commonMain code

## Phase 16: Integration Tests with Fakes

**Goal:** Test multi-class interactions with fake data sources.

- Create `test-common/` KMP module with shared fakes in `commonMain`
- Fakes: `FakeNetworkDoorDataSource`, `FakeLocalDoorDataSource`, etc.
- Integration tests: real Repository + real UseCase + fake DataSource
- Validates flows: "fetch door → cache locally → return to UI"
- Move existing fakes from `androidApp/testcommon/`

## Phase 17: Architecture Enforcement Rules

**Goal:** Detekt rules and Gradle checks for module purity.

- Import boundary: shared modules must not import `android.*` in commonMain
- Hardcoded string detection in Compose (battery-butler's Detekt rule)
- Module dependency direction enforcement: domain ← usecase ← data ← viewmodel ← app

## Phase 18: Presentation-Model Module (Optional)

**Goal:** Separate UI state definitions from ViewModel logic.

- `presentation-model/` KMP module for screen state data classes
- Depends only on `:domain`
- Deferred until after Phase 10 when the boundary is clearer

## Phase Summary

| Phase | Effort | Prerequisite | Status |
|-------|--------|-------------|--------|
| 1. Testing | Medium | None | **COMPLETE** |
| 2. Clean Architecture | Large | Phase 1 | **COMPLETE** |
| 3. DI Migration | Medium | Phase 2 | **COMPLETE** |
| 4. Network Migration | Medium | Phase 3 | **COMPLETE** |
| 5. KMP | Large | Phase 4 | **COMPLETE** (Android-only) |
| 6. Screenshot Tests | Medium | None | **COMPLETE** |
| 7. Instrumented Tests | Medium | None | **COMPLETE** |
| 8. UseCase Refactor + Module | Medium | Phase 2 | **COMPLETE** |
| 9. Data Module Repos | Medium | Phase 8 | **COMPLETE** |
| 10. Shared ViewModels | Medium | Phase 9 | 10.1 COMPLETE (state machine extracted) |
| 11. Platform Abstractions | Small | Phase 9 | TODO |
| 12. Nav3 Migration | Medium | None | TODO |
| 13. iOS Target | Large | Phases 10-11 | TODO |
| 14. Typed Errors | Medium | Phase 8 | **COMPLETE** (foundation) |
| 15. Kermit Logging | Small | None | **COMPLETE** |
| 16. Integration Tests | Medium | Phase 9 | TODO |
| 17. Architecture Rules | Small | Phase 8 | **COMPLETE** (import boundaries) |
| 18. Presentation-Model | Small | Phase 10 | TODO (optional) |

**Rule:** Finish each phase before starting the next. Update this document with commit hashes when items complete.
