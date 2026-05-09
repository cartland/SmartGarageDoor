---
category: reference
status: active
last_verified: 2026-04-24
---
# Android Testing & CI Stability Plan

## Goal

**Deploy with confidence based on CI.** If CI passes, the app is safe to ship. Tests exist to catch real bugs, not to inflate coverage numbers.

## Principles

1. **Tests must add value.** Every test should catch a bug that code review alone would miss — silent failures, race conditions, state machine edge cases.
2. **Prioritize production risk.** Test the code paths where bugs cause user-facing damage: network error handling, auth token expiry, state machine timeouts.
3. **Automate everything in CI.** If a check can run in CI, it should. Manual QA steps are gaps in the safety net.
4. **No silent failures.** The biggest class of bugs in this codebase is functions that fail silently (log and return). Tests should verify that failures produce visible error states.

## Current State

- **305+ unit tests** across 52 test files (7 androidApp + 9 domain + 28 usecase + 7 data + 1 data-local), all KMP-compatible (kotlin.test). Includes `NetworkSnoozeRepositoryTest` (9 tests, guards the snooze externalScope pattern per ADR-019) and `RealNetworkSnoozeRepositoryPropagationTest` (2 tests, wires the real `NetworkSnoozeRepository` + real `DefaultProfileViewModel` around fakes — catches regressions in the full production chain without needing an emulator).
- **26 instrumented tests** across 6 test files (Room sanity, DI graph, navigation smoke, state restoration, configuration change, snooze-state Compose propagation). The snooze test is intentional belt-and-suspenders for the VM→flow→`collectAsState` path but **cannot catch R8-specific regressions** (runs against the debug variant) — see ADR-020.
- **14 shared fakes** in `test-common` module — one copy each, no duplicates across modules
- **Zero Mockito** — all tests use fake implementations
- **CI architecture:** pre-submit (`ci.yml` → `ci-checks.yml`) + post-merge (`ci-post-merge.yml` → `ci-checks.yml` + instrumented tests)
- **CI gate jobs:** `Android CI Complete` (PRs, branch-protection-required), `Android Post-Merge Complete` (main) — single check-run names for branch protection and release script
- **CI checks:** unit tests (3 build variants), Spotless formatting (all modules), Detekt, Android Lint, screenshot test compilation, debug APK build, release AAB build
- **CI path filtering:** Android CI skips for Firebase-only/docs-only changes, and vice versa
- **CI failure tracking:** post-merge failures auto-create GitHub issues (`ci-failure/post-merge`), auto-close on fix, flakiness detection
- **Local validation:** `./scripts/validate.sh` mirrors CI + Room schema drift check + auto-discovered module tests + screenshot compilation + import boundary check + architecture check + singleton guard + singleton caching check + no raw Dispatchers (ADR-005) + no bare top-level fns (ADR-009) + no `*Impl` suffix (ADR-008) + no Mockito imports (ADR-003) + Mutex `withLock` + layer import check + Nav2 import check + rememberSaveable guard. Opt-in: `VALIDATE_R8=1 ./scripts/validate.sh` runs R8 instrumented tests (requires connected device).
- **Safety guardrails:** git hooks warn on Room entity changes, block push to main, enforce squash merge, block direct screenshot Gradle tasks, block absolute paths to gradlew, block push to branches with auto-merge enabled
- **DI system:** kotlin-inject (Hilt fully removed as of Phase 3), Ktor HTTP (Retrofit fully removed as of Phase 4)
- **Navigation:** Navigation 3 (Nav2 fully removed, enforcement check blocks re-introduction)
- **Architecture enforcement:** ArchitectureCheckTask (module deps), SingletonGuardTask (DB/Settings/HTTP scoping), SingletonCachingCheckTask (kotlin-inject generated `_scoped.get(...)` matches `@Singleton` count and names — catches the android/170 shape where caching is silently skipped; see `DI_SINGLETON_REQUIREMENTS.md` for the verification checklist), NoRawDispatchersTask (ADR-005 — VMs/UseCases must inject `DispatcherProvider`), NoBareTopLevelFunctionsTask (ADR-009 — group in `object {}`; extensions and `@Composable` exempt), NoImplSuffixTask (ADR-008 — use descriptive prefix), NoMockitoImportsTask (ADR-003 — fakes over mocks), MutexWithLockTask (correctness — bare `lock()`/`unlock()` pairs strand the lock on throw), LayerImportCheckTask (ViewModel→UseCase strict, UseCase→domain boundaries), RememberSaveableGuardTask (blocks unsafe rememberSaveable without saver), ViewModelStateFlowCheckTask (ADR-022 — repo-owned StateFlow must pass through, no VM `stateIn`/mirror). ViewModels are blocked from importing both data layer implementations AND `domain.repository.*` interfaces — must go through a UseCase
- **ViewModel fetch contract:** every fetch method that drives a `LoadingResult<T>` MUST set `LoadingResult.Complete(result.data)` explicitly in the `AppResult.Success` branch (ADR-023). `MutableStateFlow` dedups by equality, so relying on the Flow observer alone latches Loading whenever a fetch returns the same value as cached. Motivating bug: 2.4.4 Home-tab regression.
- **Test coverage:** No exemptions remaining (RoomAppLoggerRepository moved to data-local KMP module)
- **Test pattern:** see [Test layering: where to fake](#test-layering-where-to-fake) below for the convention.
- **Completed:** Phase 1 (CI hardening), Phase 2 (network error tests), Phase 3 (auth token fix + UseCase tests + AuthBridge extraction), Phase 4 (state machine completeness), Phase 5.2-5.3 (release safety), Phase 6.1 (ESLint migration), Phase 7 (instrumented tests)
- **Remaining:** Phase 6.2 (server contract tests) — Firebase server scope; tracked in `docs/FIREBASE_HANDLER_PATTERN.md` (handler-extraction pattern shipped via `server/18`).

---

## Test layering: where to fake

The codebase uses two test patterns deliberately, picked by what's under test:

**Default — real consumer + fake repo.** Most VM and UseCase tests fake at the repository interface. Examples: `HomeViewModelTest`, `ProfileViewModelTest`, every `*ViewModelTest` in `usecase/commonTest/`. Setup is one line per fake (`FakeAuthRepository.setAuthState(Authenticated(...))`); the test isolates the consumer's logic from network/coroutine plumbing. This is what the `test-common/` `Fake*Repository` classes exist for.

**Escape hatch — real repo + fake data source.** When the bug under test lives in the repository's own wiring (loading-state lifecycle, `externalScope.async{}.await()` semantics, StateFlow propagation through `MutableStateFlow.value =` writes, stale-while-revalidate caching), the fake-repo pattern hides exactly the code that's broken. For these cases, instantiate the REAL repository (e.g. `NetworkSnoozeRepository`) and substitute fakes only at the data-source layer (`FakeNetworkButtonDataSource`, `FakeNetworkConfigDataSource`). Canonical example: `RealNetworkSnoozeRepositoryPropagationTest` — closes the gap that `RemoteButtonRepository` fakes left around the android/167 snooze-state propagation bug. The test's docstring contrasts itself with the fake-repo pattern explicitly.

**When to use which:**
- Testing VM behavior (which UseCase did the VM call? what's the resulting StateFlow?) → fake repo.
- Testing repository wiring (does this StateFlow actually emit when I write to it under `externalScope.async{}.await()`?) → real repo + fake data source.
- Repos with non-trivial wiring deserve at least one real-repo test even if their consumers are fake-tested. Current candidates worth covering this way: `NetworkButtonHealthRepository`'s SWR (stale-while-revalidate) pattern from 2.12.1 (ADR-022).

**Not used: real repo + real data source.** That's an integration test bucket; the codebase relies on instrumented tests + the internal-track release smoke test for the device-end of integration.

---

## Phase 1: CI Pipeline Hardening

Make CI a reliable gate. These are workflow/config changes, no app code.

### 1.1 Enable Android Lint in CI

Android Lint catches real bugs: unused resources, missing translations, API level incompatibilities, security issues (cleartext traffic, exported components).

**Action:** Add `./gradlew :androidApp:lint` to the CI test job. Start with `lintOptions { abortOnError true }` for error-severity issues only. Fix existing errors, then tighten over time.

**Files:** `.github/workflows/ci.yml`, `AndroidGarage/androidApp/build.gradle.kts`

### 1.2 Verify Release Build in CI on PRs

Currently, release AAB only builds on main/internal/release pushes. A PR could break the release build and we wouldn't know until after merge.

**Action:** Build release AAB on PRs too (without signing — just compilation). Add `./gradlew :androidApp:bundleRelease` or at minimum `assembleRelease` to the PR CI job.

**Files:** `.github/workflows/ci.yml`

### 1.3 Fail CI on Compiler Warnings

Kotlin compiler warnings (unused variables, deprecations, unchecked casts) often indicate real bugs. Currently they're ignored.

**Action:** Add `-Werror` to Kotlin compiler options in build.gradle.kts. Fix existing warnings first, then enable.

**Files:** `AndroidGarage/androidApp/build.gradle.kts`

### 1.4 Require CI Checks Before Deploy

Branch protection is set up but verify the internal/release branches also require CI.

**Action:** Audit branch protection rules. Ensure the `internal` branch (which auto-deploys to Play Store) requires CI to pass.

---

## Phase 2: Network Error Handling Tests

The highest-risk untested area. The app has ~15 null-check branches in network code that all fail silently — the user sees stale data with no indication something is wrong.

### 2.1 DoorRepository Network Error Tests

`DoorRepositoryImpl.fetchCurrentDoorEvent()` has 9 distinct failure paths (null config, non-200 response, null body, null currentEventData, null currentEvent, null doorEvent, exception). All return silently.

**Tests to write:**
- HTTP 500 response → verify `_currentDoorEvent` reflects error state (not stuck in Loading)
- Null response body → same
- Network exception (timeout) → same
- Null `buildTimestamp` (server config not loaded) → verify graceful handling
- Valid response → verify event is stored in local database

**Files:** New `DoorRepositoryTest.kt`, mock `GarageNetworkService` and `LocalDoorDataSource`

### 2.2 PushRepository Error Tests

`PushRepositoryImpl.push()` never checks the HTTP response code. A 401 or 500 looks identical to success from the user's perspective.

**Tests to write:**
- Push with HTTP 500 response → verify `pushButtonStatus` reflects error (not IDLE)
- Push with null server config → verify status is not stuck in SENDING
- Push with `remoteButtonPushEnabled = false` → verify no network call made
- Snooze with error response body → verify `snoozeAction` becomes `Failed.NetworkError`

**Files:** New `PushRepositoryTest.kt`

### 2.3 ServerConfigRepository Caching Tests

Already done (8 tests in PR #11). Covers caching, all null-validation branches, and exceptions.

---

## Phase 3: Auth Token Lifecycle Tests

### 3.1 Auth Token Refresh Bug Fix — `9c469b8` (#53)

`refreshFirebaseAuthState()` had `addOnSuccessListener` without `addOnFailureListener`. Fixed by adding failure listener that resumes with null (triggering existing Unauthenticated fallback).

**Remaining:** Full AuthRepository unit tests still need Firebase wrapper extraction. The fix was a one-line safety improvement, not a full test suite.

### 3.2 Token Expiry in RemoteButtonViewModel — COMPLETE (PR #48)

Token refresh logic extracted into `EnsureFreshIdTokenUseCase` with 6 tests in `EnsureFreshIdTokenUseCaseTest`:
- Cached token returned when not expired
- Token refreshed when expired (exact boundary tested)
- Fallback to cached token when refresh returns Unauthenticated
- Fallback to cached token when refresh returns Unknown
- No refresh when token has large margin

---

## Phase 4: State Machine Completeness

The RemoteButton timeout state machine has 6 states and 10-second delays between transitions. Basic transitions are tested; edge cases are not.

### 4.1 Timeout Edge Cases — COMPLETE

All state machine edge cases covered in `RemoteButtonViewModelTest.kt`:
- SENDING→SENDING_TIMEOUT, SENT→SENT_TIMEOUT, RECEIVED→NONE
- Timeout cancellation on door movement
- Reset during timeout → timeout cancelled, state is NONE
- Rapid state changes (SENDING→SENT→RECEIVED) → only final timeout active
- Multiple door position changes during SENT → state stays RECEIVED (not oscillating)

---

## Phase 5: Release Safety

Checks that verify the release artifact is safe to ship.

### 5.1 ProGuard/R8 Smoke Test

Release builds use `isMinifyEnabled = true` with an empty `proguard-rules.pro`. If R8 strips or renames a class used by Moshi, Room, or Hilt via reflection, the app crashes at runtime — but unit tests wouldn't catch it.

**Action:** Covered by Phase 7 instrumented tests (Hilt DI graph test catches R8 stripping).

**Files:** See Phase 7

### 5.2 Disable Verbose Logging in Release

`GarageNetworkService.kt` creates an `HttpLoggingInterceptor` with `Level.BODY` unconditionally. This logs auth tokens to Logcat in release builds.

**Action:** Gate the logging interceptor on `BuildConfig.DEBUG`. Add a test that verifies the OkHttpClient in release mode does not have body-level logging.

**Files:** `GarageNetworkService.kt`, new test

### 5.3 BuildConfig Validation

`BuildConfig.SERVER_CONFIG_KEY` and `BuildConfig.GOOGLE_WEB_CLIENT_ID` come from `local.properties`. If they're missing, the build succeeds with empty strings — the app launches but nothing works.

**Action:** Add a build-time check that fails compilation if these values are empty/placeholder. Or add a runtime startup check that crashes early with a clear message.

**Files:** `androidApp/build.gradle.kts`

---

## Phase 6: Firebase Server Tests

The server handles all business logic (door state interpretation, notifications, error detection). Server bugs affect all clients.

### 6.1 Migrate tslint to ESLint

tslint has been deprecated since 2019. ESLint with `@typescript-eslint` catches more issues and is actively maintained.

**Action:** Replace tslint with ESLint. Keep existing rules, add `@typescript-eslint/strict` preset.

**Files:** `FirebaseServer/tslint.json` → `FirebaseServer/.eslintrc.js`, `FirebaseServer/package.json`

### 6.2 Add Server Error Response Tests

The server functions return error responses that the Android app must handle. If the error format changes, the app breaks.

**Action:** Add contract tests that verify error response shapes match what the Android app expects.

**Files:** New tests in `FirebaseServer/test/`

---

## Phase 7: Instrumented Tests

Instrumented tests run on a real device/emulator and catch runtime-only failures that unit tests miss (R8 stripping, DI graph resolution, Room migrations, navigation crashes). These run **post-merge only** (not on PRs) in a separate workflow using Gradle Managed Devices.

### 7.1 Setup Gradle Managed Device

**Action:** Configure a Gradle Managed Device in `build.gradle.kts`:
```kotlin
testOptions {
    managedDevices {
        localDevices {
            create("pixel6Api34") {
                device = "Pixel 6"
                apiLevel = 34
                systemImageSource = "aosp-atd"
            }
        }
    }
}
```

Create `.github/workflows/instrumented-tests.yml`:
- Trigger: `push` to `main` only (not PRs)
- Runs: `./gradlew pixel6Api34DebugAndroidTest`
- Uses KVM acceleration on GitHub Actions Linux runners
- Non-blocking: failures create GitHub annotations but don't block merges

### 7.2 Room Database Sanity Test

Verify the database can be created and basic DAO operations work. Catches R8 stripping Room type converters or entity definitions.

**Tests:**
- Create in-memory database
- Insert and read a `DoorEvent`
- Insert and read an `AppEvent`
- Verify all DAOs are accessible from `AppDatabase`
- AppLogger per-key cap behavior (`insertAndPruneKey`, `pruneAllKeys`, `deleteAllAppEvents`)
- `RoomAppLoggerRepository.log()` end-to-end with cap

**Files:** `src/androidTest/.../db/DatabaseSanityTest.kt`

**Gap:** these tests use `inMemoryDatabaseBuilder`, which always opens at the latest schema version. **They do not exercise migration code paths.** A regression in an `@AutoMigration` or `Migration` class would pass every test in this suite and only surface on a real device with pre-existing user data — i.e., on the Play Store internal track at the earliest. Until we wire up Room's `MigrationTestHelper` (which can replay an exported schema and run the migration against real SQLite), schema changes should be smoke-tested by installing the upgrade build over the previous one on a test device before promoting from the internal track to production. PR #660 (AppLogger v11→v12) shipped under this gap; the migration was reviewed by inspecting the KSP-generated `AppDatabase_AutoMigration_11_12_Impl.kt`.

### 7.3 kotlin-inject Component Test

Verify the full kotlin-inject DI graph resolves without errors. Catches missing `@Provides` methods or constructor wiring that only crash at runtime.

**Tests:**
- Launch `MainActivity` with test runner
- Verify Activity creates without crash
- Verify `AppComponent` creates and all ViewModels are accessible

**Files:** `src/androidTest/.../ComponentGraphTest.kt`

### 7.4 Navigation Smoke Tests

Verify all screens are reachable via navigation. Catches crashes from missing composables, broken routes, or unresolvable ViewModel dependencies.

**Tests:**
- Navigate to Home screen
- Navigate to Door History screen
- Navigate to Profile screen
- Navigate to each settings/info screen
- Verify no crash on any navigation transition

**Files:** `src/androidTest/.../navigation/NavigationSmokeTest.kt`

---

## Priority Order

| Phase | Effort | Risk Reduced | Status |
|-------|--------|-------------|--------|
| 1.1 Android Lint in CI | Small | Medium | Done |
| 1.2 Release build on PRs | Small | High | Done |
| 1.3 Compiler warnings | Medium | Medium | Skipped (none exist) |
| 1.4 Branch protection audit | Small | Medium | Done |
| 2.1 DoorRepository error tests | Medium | High | Done |
| 2.2 PushRepository error tests | Medium | High | Done |
| 2.3 ServerConfigRepository tests | Medium | High | Done |
| 4.1 Timeout edge cases | Small | Medium | Done |
| 5.2 Disable release logging | Small | High | Done |
| 5.3 BuildConfig validation | Small | Medium | Done |
| 3.1 Auth token tests | Large | High | Done (#152) — AuthBridge extraction + 6 tests |
| 5.1 ProGuard smoke test | Medium | Medium | Covered by Phase 7 |
| 7.1 Gradle Managed Device setup | Medium | High | Done (#105) |
| 7.2 Room database sanity | Small | High | Done (#105) |
| 7.3 kotlin-inject component test | Small | High | Done (#105) |
| 7.4 Navigation smoke tests | Medium | Medium | Done (#106) |
| 6.1 Migrate tslint | Medium | Low | Done (#150) |

---

## Success Criteria

When this plan is complete:
- CI catches network error handling regressions (silent failures become test failures)
- CI catches auth token lifecycle bugs (hanging coroutines, expired tokens)
- CI catches release build breaks before merge
- CI catches lint issues that indicate real bugs
- Auth token is not logged in release builds
- Deploying from the `internal` branch with green CI is safe
