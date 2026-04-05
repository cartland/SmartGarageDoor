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

## Phase 5: KMP Preparation

**Status:** Not started. Begin after Phase 4.

**Goal:** Share code across platforms.

### 5.1 Configure Multiplatform
- Add `org.jetbrains.kotlin.multiplatform` plugin
- Define targets: `androidTarget()`, `iosArm64()`, `iosSimulatorArm64()`
- Create `commonMain`, `androidMain`, `iosMain` source sets

### 5.2 Move Shared Code to Common
- Domain layer (interfaces, models) → `commonMain`
- UseCase layer → `commonMain`
- Repository implementations → `commonMain` (with `expect`/`actual` for platform specifics)
- ViewModels → `commonMain`

### 5.3 Platform-Specific Implementations
- `expect class DatabaseFactory` / `actual class DatabaseFactory` for Room
- `expect class HttpClientFactory` / `actual class HttpClientFactory` for Ktor engine
- `expect class AuthBridge` / `actual class AuthBridge` for Firebase Auth (Android SDK / iOS SDK)
- Platform entry points create components with platform-specific dependencies

## Phase 6: Screenshot Tests

**Status:** Not started. Can begin independently of other phases.

**Goal:** Automated app screenshot generation for Play Store and documentation.

### 6.1 Set Up Compose Screenshot Testing
- Add Compose screenshot test dependencies (same approach as battery-butler)
- Create screenshot test module or source set

### 6.2 Write Deterministic Previews
- All previews use fixed `Instant.parse(...)` timestamps, not `Clock.System.now()`
- Thread time parameters through composable chain
- Create preview data fixtures for consistent test data

### 6.3 Generate Gallery
- Write screenshot tests for key screens: Home, Door History, Remote Button, Profile
- Write screenshot tests for key components: DoorStatusCard, AnimatableGarageDoor
- Generate reference PNGs, commit to repository
- Screenshots do NOT block CI — regenerated on demand

## Phase Summary

| Phase | Effort | Prerequisite | Status |
|-------|--------|-------------|--------|
| 1. Testing | Medium | None | **COMPLETE** |
| 2. Clean Architecture | Large | Phase 1 | **COMPLETE** |
| 3. DI Migration | Medium | Phase 2 | **COMPLETE** |
| 4. Network Migration | Medium | Phase 3 | **COMPLETE** |
| 5. KMP | Large | Phase 4 | Not started |
| 6. Screenshot Tests | Medium | None | Not started |

**Rule:** Finish each phase before starting the next. Update this document with commit hashes when items complete.
