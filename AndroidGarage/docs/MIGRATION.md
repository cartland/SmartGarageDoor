# Migration Roadmap

Target: Align with [battery-butler](https://github.com/cartland/battery-butler) technology stack over several independent refactoring projects. Each phase is a separate PR or series of PRs. No phase depends on a later phase.

## Phase 1: Testing Infrastructure

**Goal:** Make CI a reliable deployment gate.

### 1.1 Add Detekt with Zero Tolerance
- Add `io.gitlab.arturbosch.detekt` plugin
- Configure `detekt.yml` with `maxIssues: 0`
- Generate baseline for existing issues (`detekt --create-baseline`)
- Run `./gradlew detekt` in CI
- Catches: swallowed exceptions, unsafe casts, unreachable code, magic numbers

### 1.2 Test Coverage Enforcement
- Write a custom Gradle task (in `buildSrc/`) that scans for `*ViewModel` and `*Repository` classes
- Fail build if matching `*Test.kt` file is missing
- Support `// @NoTestRequired: reason` inline exemptions
- Reference: battery-butler's `TestCoverageCheckTask.kt`

### 1.3 Migrate from Mocks to Fakes
- Create fake implementations for key interfaces: `FakeDoorRepository`, `FakePushRepository`, `FakeAuthRepository`
- Put fakes in a `test-common` source set or dedicated module
- Gradually replace Mockito `mock()` calls with fakes in existing tests
- Fakes are more readable and work across KMP

### 1.4 Enable Android Lint in CI
- Add `./gradlew :androidApp:lint` to CI
- Fix error-severity issues first, then tighten

## Phase 2: Clean Architecture

**Goal:** Separate business logic into testable layers.

### 2.1 Extract Domain Module
- Create `domain/` module with interfaces and models (no Android dependencies)
- Move `DoorRepository`, `PushRepository`, `AuthRepository` interfaces to domain
- Move `DoorEvent`, `DoorPosition`, `AuthState`, `LoadingResult` to domain
- Domain depends on nothing

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

| Phase | Effort | Prerequisite | Key Benefit |
|-------|--------|-------------|-------------|
| 1. Testing | Medium | None | CI becomes reliable deployment gate |
| 2. Clean Architecture | Large | None | Testable layers, reusable logic |
| 3. DI Migration | Medium | Phase 2 helps but not required | KMP-compatible DI |
| 4. Network Migration | Medium | None | KMP-compatible networking |
| 5. KMP | Large | Phases 3 + 4 | Android + iOS code sharing |
| 6. Screenshot Tests | Medium | None | Automated Play Store assets |

**Recommended order:** Start with Phase 1 (testing infrastructure). It builds the safety net needed before making structural changes in later phases.
