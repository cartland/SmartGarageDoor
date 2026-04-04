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
- Repository interfaces in domain (signatures pending alignment with androidApp)

### 2.2 Extract UseCase Layer
- Create `usecase/` module depending on domain
- Extract business logic from ViewModels into single-purpose UseCases:
  - `FetchDoorStatusUseCase`
  - `PushRemoteButtonUseCase`
  - `RegisterFcmUseCase`
  - `RefreshAuthTokenUseCase`
- Each UseCase has `operator fun invoke()` for clean call syntax
- ViewModels depend on UseCases, not Repositories

### 2.3 Separate Data Modules
- `data/` — Repository implementations
- `data-local/` — Room database, DataStore, DAOs
- `data-network/` — Network service, HTTP client
- Each implements domain interfaces

## Phase 3: DI Migration (Hilt to kotlin-inject)

**Status:** Not started. Begin after Phase 2.

**Goal:** KMP-compatible dependency injection.

### 3.1 Add kotlin-inject
- Add `me.tatarka.inject` KSP plugin and runtime dependency
- Create `AppComponent` with `@Component` annotation
- Provide all singletons via `@Provides` methods

### 3.2 Migrate ViewModels
- Replace `@HiltViewModel` with `@Inject` constructor
- Build ViewModel instances from component, not Hilt
- Update Navigation to pass ViewModel factories

### 3.3 Remove Hilt
- Delete all `@Module`, `@InstallIn`, `@Binds` annotations
- Remove Hilt Gradle plugin and dependencies
- Remove `@HiltAndroidApp` from `GarageApplication`

## Phase 4: Network Migration (Retrofit to Ktor HTTP)

**Status:** Not started. Begin after Phase 3.

**Goal:** KMP-compatible HTTP client.

### 4.1 Add Ktor Client
- Add `io.ktor:ktor-client-core`, `ktor-client-okhttp` (Android), `ktor-client-content-negotiation`
- Add `io.ktor:ktor-serialization-kotlinx-json`
- Configure base URL, logging, auth headers

### 4.2 Replace Retrofit Endpoints
- Rewrite `GarageNetworkService` methods as Ktor `HttpClient` calls
- Replace Moshi response DTOs with `@Serializable` data classes (kotlinx.serialization)
- Migrate one endpoint at a time, keeping both active during transition

### 4.3 Remove Retrofit
- Delete Retrofit interface, annotations, Moshi adapters
- Remove OkHttp logging interceptor (Ktor has its own)
- Remove Retrofit/Moshi/OkHttp dependencies

## Phase 5: KMP Preparation

**Status:** Not started. Begin after Phases 3 + 4.

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
| 2. Clean Architecture | Large | Phase 1 | Not started |
| 3. DI Migration | Medium | Phase 2 | Not started |
| 4. Network Migration | Medium | Phase 3 | Not started |
| 5. KMP | Large | Phases 3 + 4 | Not started |
| 6. Screenshot Tests | Medium | None | Not started |

**Rule:** Finish each phase before starting the next. Update this document with commit hashes when items complete.
