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

## Phase 11: Platform Abstractions — COMPLETE

**Goal:** Decouple platform SDKs behind injectable interfaces for testability and iOS readiness.

### 11.1 AuthBridge — COMPLETE (#152)
- `AuthBridge` interface in `data/src/commonMain/` abstracts Firebase Auth
- `FirebaseAuthBridge` implementation in androidApp
- `FirebaseAuthRepository` no longer imports Firebase directly
- 6 new unit tests via `FakeAuthBridge`
- Fixed: double-resume bug in original token refresh callback

### 11.2 MessagingBridge — COMPLETE (#153)
- `MessagingBridge` interface in `data/src/commonMain/` abstracts FCM
- `FirebaseMessagingBridge` implementation in androidApp
- Fixed: double-resume bug in original getToken callback

### 11.3 Existing Abstractions
- HTTP: `NetworkDoorDataSource`, `NetworkConfigDataSource`, `NetworkButtonDataSource` (Ktor behind interfaces)
- Local storage: `LocalDoorDataSource` (Room behind interface)
- `expect/actual` declarations deferred until iOS target is added

## Phase 12: Type-Safe Navigation — IN PROGRESS

**Goal:** Modern type-safe navigation.

### 12.1 Type-Safe Routes — COMPLETE
- Migrated from string routes to `@Serializable` route objects (`Route.Home`, `Route.History`, `Route.Profile`)
- Uses Navigation Compose 2.9 `composable<Route>` pattern
- `Tab` enum links routes to UI metadata (label, icon)
- `NavDestination.hasRoute()` for type-safe tab selection

### 12.2 Nav3 Migration — DEFERRED
- Nav3 was still alpha/experimental as of May 2025
- Current Navigation Compose 2.9 with type-safe routes is sufficient
- Type-safe routes are a stepping stone — Nav3 uses typed keys similarly
- Revisit when Nav3 reaches beta/stable

## Phase 13: iOS Target (Future)

**Goal:** Add iOS framework target to shared modules.

- Add `iosX64()`, `iosArm64()`, `iosSimulatorArm64()` targets
- Implement `actual` declarations for iOS
- Create SwiftUI app consuming shared ViewModels via KMP framework

## Phase 14: Typed Error System — COMPLETE

**Goal:** Replace silent failures with typed error hierarchy.

### 14.1 Foundation — COMPLETE
- `AppResult<D, E : AppError>` sealed interface in `domain/`
- `AppError` base with `message` and `cause`
- Hierarchies: `DataError.Network`, `DataError.Database`, `AuthError`
- Extensions: `map`, `mapError`, `getOrNull`, `flatMap`, `onSuccess`, `onError`
- 11 tests in `domain/src/commonTest/`

### 14.2 DoorRepository Migration — COMPLETE
- `fetchCurrentDoorEvent()` and `fetchRecentDoorEvents()` return `AppResult<D, FetchError>`
- `FetchError` sealed interface: `NotReady`, `NetworkFailed`
- ViewModel handles errors with exhaustive `when`

### 14.3 UseCase Migration — COMPLETE
- `PushRemoteButtonUseCase` and `SnoozeNotificationsUseCase` return `AppResult<Unit, ActionError>`
- `ActionError` sealed interface: `NotAuthenticated`, `MissingData`
- ADR-010 documents the typed API pattern (observation + one-time requests)

## Phase 15: KMP Logging (Kermit)

**Goal:** Multiplatform logging to replace `android.util.Log`.

- Add Kermit dependency to shared modules
- Replace `Log.d/e/w` with `Logger.d/e/w` (API is nearly identical)
- No custom abstraction — Kermit is multiplatform-first
- Enables logging in commonMain code

## Phase 16: Integration Tests with Fakes — COMPLETE

**Goal:** Test multi-class interactions with fake data sources.

### 16.1 Data Module Integration Tests — COMPLETE
- Fake data sources in `data/src/commonTest/`: `InMemoryLocalDoorDataSource`, `FakeNetworkDoorDataSource`, `FakeNetworkConfigDataSource`
- 12 integration tests for `NetworkDoorRepository`: fetch→cache→flow pipeline, null server config, null responses
- 6 tests for `CachedServerConfigRepository`: caching, retry after null, cache invalidation
- All tests use `kotlin.test` (KMP-compatible, no Mockito)

### 16.2 Move androidApp Fakes — DEFERRED
- Existing `FakeDoorRepository`, `FakePushRepository`, `FakeAuthRepository` in `androidApp/testcommon/` still useful for ViewModel tests
- Will move to shared module when adding iOS tests

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
| 10. Shared ViewModels | Medium | Phase 9 | **COMPLETE** (all 5 ViewModels in shared modules) |
| 11. Platform Abstractions | Small | Phase 9 | **COMPLETE** (bridges extracted) |
| 12. Type-Safe Navigation | Medium | None | **COMPLETE** (Nav2→Nav3 in #192) |
| 13. iOS Target | Large | Phase 38 | TODO (renumbered to Phase 38) |
| 14. Typed Errors | Medium | Phase 8 | **COMPLETE** |
| 15. Kermit Logging | Small | None | **COMPLETE** |
| 16. Integration Tests | Medium | Phase 9 | **COMPLETE** (data module) |
| 17. Architecture Rules | Small | Phase 8 | **COMPLETE** (import boundaries) |
| 18. Presentation-Model | Small | Phase 10 | **COMPLETE** |

### Architecture Hardening (completed alongside migration)

| Phase | Description | PR |
|-------|-------------|-----|
| A | JVM import boundary enforcement | #156 |
| B | Detekt error handling rules + ADR-011 | #157 |
| C | NetworkResult<T> typed data sources | #158 |
| D | Inject APP_CONFIG into ViewModel | #159 |
| E | FCMService structured concurrency | #161 |
| F | AuthRepository inject scope | #162 |
| G | Error state propagation to UI | #163 |
| H | Migrate DoorViewModelTest to fakes | #164 |
| I | Test timeout constants | #160 |

### Android Purity — Move business logic to shared KMP modules

| Phase | Description | PR |
|-------|-------------|-----|
| 19 | Move snooze + FCM models to domain | #169 |
| 20 | Remove unused Activity param from FCM chain | #170 |
| 21 | Move Ktor data sources to data module | #171 |
| 22 | Move FirebaseAuthRepository to data module | #174 |
| 23 | Extract AppConfig types to domain | #173 (combined with 24) |
| 24 | Move HTTP client config to data module | #173 |
| 25 | Extract AppLoggerDataSource to data module | #172 |
| 26 | Move Google Sign-In to Compose layer | #181 |
| 27 | Move RemoteButton + Door ViewModels to usecase | #175, #177 |
| 28 | Move AppSettings ViewModel to usecase | #178 |
| 29 | Move AppLogger ViewModel to usecase | #179 |
| 30 | Move AuthViewModel to usecase | #182 |
| 31 | Move DispatcherProvider + FcmRepository to shared | #183 |
| 32 | Move demo data to presentation-model | #184 |
| — | FQN enforcement (NoFullyQualifiedNames check) | #180 |
| 33 | Create data-local KMP module with Room database | #187 |
| — | Import boundary for data-local + exemption cleanup | #188 |
| — | Clean stale detekt baseline | #189 |
| — | FcmRepository tests + shared fakes | #190 |
| — | ViewModel tests (13 tests) + remove 4 exemptions | #191 |
| — | Navigation 2 → Navigation 3 migration | #192 |
| — | Nav2 import enforcement (NoNav2ImportsTask) | #192 |

**Result:** All ViewModels, UseCases, Repositories, data sources, and local database live in shared KMP modules. Navigation uses Nav3 (NavDisplay + entryProvider). androidApp only contains: Compose UI, Firebase bridge implementations, DI wiring, and Android framework code.

### What remains in androidApp

| Category | Files | Can share? | Path forward |
|----------|-------|------------|--------------|
| Compose UI | `ui/*.kt`, `ui/theme/*.kt` | **Yes** | Move to shared Compose modules (Phase 34) |
| Firebase bridges | `FirebaseAuthBridge`, `FirebaseMessagingBridge`, `FCMService` | No | Android-only Firebase SDK; iOS gets its own impls |
| Google Sign-In | `GoogleSignInState.kt` | No | Android GMS API; iOS uses Apple Sign-In |
| DI wiring | `AppComponent.kt`, `ActivityViewModels.kt`, `ComponentProvider.kt`, `Singleton.kt` | Partial | `Singleton` can move; `AppComponent` stays per-platform |
| Settings impl | ~~`AppSettings.kt`, `SettingManager.kt`~~ | **Done** | Replaced with reactive DataStore (#199) |
| Platform | `MainActivity.kt`, `GarageApplication.kt`, permissions, version | No | Android framework entry points |
| Time formatting | `TimeFormats.kt` | **Yes** | Replace `java.time` with `kotlinx-datetime` (Phase 36) |
| Config values | `LocalConfig.kt` | Partial | Types shared; `BuildConfig` values stay per-platform |
| HTTP engine | `KtorHttpClientProvider.kt` | expect/actual | Engine selection via KMP expect/actual (Phase 37) |

## Next: Full KMP Alignment (battery-butler target)

Target module structure matching [battery-butler](https://github.com/cartland/battery-butler):

```
domain/              commonMain — models, repository interfaces, error types
data/                commonMain — repository implementations, bridges
data-local/          commonMain — Room database + DataStore (KMP)
data-network/        commonMain — Ktor HTTP data sources (already here)
usecase/             commonMain — use cases (already here)
viewmodel/           commonMain — ViewModels (currently usecase/, rename later)
presentation-model/  commonMain — screen state data classes (already here)
presentation-core/   commonMain — shared Compose UI components
compose-app/         Android app + iOS framework + Desktop
fixtures/            commonMain — test data and demo fixtures
```

### Phase 33: Move Room to shared `data-local` module — COMPLETE (#187)

Room 2.7.2 (stable) with KMP support. Created `data-local/` module with:
- `AppDatabase` with `@ConstructedBy` for KMP compatibility
- All entities (`DoorEventEntity`, `AppEvent`), DAOs, `DatabaseLocalDoorDataSource`
- `BundledSQLiteDriver` for cross-platform SQLite
- Android-specific `DatabaseFactory` in androidMain (iOS impl in Phase 37)
- `expect/actual currentTimeMillis()` replacing `System.currentTimeMillis()`
- Import boundary enforcement (allows `androidx.room.*`, `androidx.sqlite.*`)

### Phase 34: Extract shared Compose modules

**Goal:** Compose Multiplatform lets UI components be shared across Android, iOS, Desktop.

**Changes:**
- Create `presentation-core/` KMP module with JetBrains Compose plugin
- Move reusable components: `DoorStatusCard`, `ErrorCard`, `ExpandableColumnCard`, `RemoteButtonContent`, `SnoozeNotificationCard`, `UserInfoCard`, `GarageDoorCanvas`, `AnimatableGarageDoor`
- Move theme: `Color.kt`, `DoorStatusColorScheme.kt`, `Type.kt`, `Theme.kt`
- Create `compose-app/` as the actual application entry point
- androidApp becomes a thin shell: `MainActivity` + `GarageApplication` + platform DI + Firebase
- Replace `painterResource(R.drawable.*)` with Compose Multiplatform resources

**Blocker:** Compose Multiplatform maturity for production iOS. Evaluate JetBrains Compose version.

### Phase 35: Replace SharedPreferences with reactive DataStore — COMPLETE (#199)

Migrated settings from synchronous SharedPreferences to reactive DataStore:
- `Setting<T>` interface now Flow-based: `val flow: Flow<T>`, `suspend fun set()`, `suspend fun restoreDefault()`
- `DataStoreAppSettings` in `data-local/commonMain` backed by `DataStore<Preferences>`
- `DataStoreSettingsFactory` in androidMain creates singleton DataStore instance
- `AppSettingsViewModel` collects flows via `stateIn(Eagerly)` — UI reactively updates
- Deleted `AppSettings.kt`, `SettingManager.kt` (180 lines of SharedPreferences boilerplate)

### Architecture Enforcement — COMPLETE (#196)

Three build-time checks in validate.sh:
- **ArchitectureCheckTask** — module dependency graph validation
- **SingletonGuardTask** — `@Singleton` required on Database/Settings/HttpClient providers
- **LayerImportCheckTask** — ViewModels can't import DataSource/Repository impls; UseCases can't import data layer

### Phase 36: Replace `java.time` with `kotlinx-datetime`

**Goal:** `java.time` is JVM-only. `kotlinx-datetime` works on all KMP targets.

**Changes:**
- Add `kotlinx-datetime` dependency to domain/data modules
- Replace `java.time.Instant` with `kotlinx.datetime.Instant` in shared code
- Replace `java.time.Duration` with `kotlin.time.Duration`
- Move `toFriendlyDuration()` to shared module (pure math)
- `toFriendlyDate()`/`toFriendlyTime()` need expect/actual for locale formatting
- Move `DurationSince` composable to shared Compose module (after Phase 34)

### Phase 37: KMP expect/actual for platform-specific code

**Goal:** Replace remaining Android-only implementations with expect/actual.

**Changes:**
- HTTP engine: expect `HttpClient` factory, actual = OkHttp (Android) / Darwin (iOS)
- Database instance: expect `AppDatabase` factory, actual = Room with Context (Android) / Room with path (iOS)
- Config values: expect `AppConfig` provider, actual reads BuildConfig (Android) / Info.plist (iOS)
- Version info: expect `AppVersion`, actual reads PackageManager (Android) / Bundle (iOS)

### Phase 38: iOS target

**Goal:** Add iOS app consuming shared KMP modules.

**Changes:**
- Add iOS targets to all shared modules (domain, data, data-local, usecase, viewmodel)
- Create `ios-swift-di/` module for Swift-side DI bridge
- Implement iOS platform bridges: `AppleAuthBridge`, `AppleMessagingBridge`
- Either: shared Compose UI (Phase 34) or native SwiftUI consuming shared ViewModels
- Xcode project setup, signing, CI

**Rule:** Finish each phase before starting the next. Update this document with commit hashes when items complete.
