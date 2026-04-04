# Android Testing & CI Stability Plan

## Goal

**Deploy with confidence based on CI.** If CI passes, the app is safe to ship. Tests exist to catch real bugs, not to inflate coverage numbers.

## Principles

1. **Tests must add value.** Every test should catch a bug that code review alone would miss — silent failures, race conditions, state machine edge cases.
2. **Prioritize production risk.** Test the code paths where bugs cause user-facing damage: network error handling, auth token expiry, state machine timeouts.
3. **Automate everything in CI.** If a check can run in CI, it should. Manual QA steps are gaps in the safety net.
4. **No silent failures.** The biggest class of bugs in this codebase is functions that fail silently (log and return). Tests should verify that failures produce visible error states.

## Current State

- **~90 unit tests** across ViewModels, Repositories, pure functions, and JSON parsing
- **CI checks:** unit tests (3 build variants), Spotless formatting, Android Lint, debug APK build, release AAB build
- **Completed:** Phase 1 (CI hardening), Phase 2 (network error tests), Phase 3.2 (token expiry via UseCase), Phase 4 (state machine completeness), Phase 5.2-5.3 (release safety)
- **Remaining:** Phase 3.1 (auth token refresh — needs Firebase wrapper refactor), Phase 5.1 (ProGuard smoke test), Phase 6 (Firebase server)

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
- Snooze with error response body → verify `snoozeRequestStatus` is ERROR

**Files:** New `PushRepositoryTest.kt`

### 2.3 ServerConfigRepository Caching Tests

Already done (8 tests in PR #11). Covers caching, all null-validation branches, and exceptions.

---

## Phase 3: Auth Token Lifecycle Tests

`AuthRepository.refreshFirebaseAuthState()` uses `suspendCancellableCoroutine` with `addOnSuccessListener` but no `addOnFailureListener`. If the Firebase token fetch fails, the coroutine hangs forever.

### 3.1 Auth Token Refresh Tests

**Tests to write:**
- Token refresh success → verify `AuthState.Authenticated` with fresh token
- Token refresh failure (Firebase returns error) → verify fallback to `Unauthenticated` (not hang)
- Token refresh with null `currentUser` → verify immediate `Unauthenticated`
- Sign out → verify state transitions to `Unauthenticated`

**Prerequisite:** Either mock `Firebase.auth` (difficult) or extract the Firebase calls behind an interface for testability. This may require a small refactor.

**Files:** New `AuthRepositoryTest.kt`, possibly new `FirebaseAuthWrapper` interface

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

**Action:** Add an instrumented test (or a Robolectric test) that initializes the Hilt dependency graph with the release build variant. This catches reflection-breaking obfuscation.

**Files:** New instrumented test in `src/androidTest/`

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
| 3.1 Auth token tests | Large | High | TODO — needs Firebase wrapper refactor |
| 5.1 ProGuard smoke test | Medium | Medium | TODO — before next release |
| 6.1 Migrate tslint | Medium | Low | TODO |

---

## Success Criteria

When this plan is complete:
- CI catches network error handling regressions (silent failures become test failures)
- CI catches auth token lifecycle bugs (hanging coroutines, expired tokens)
- CI catches release build breaks before merge
- CI catches lint issues that indicate real bugs
- Auth token is not logged in release builds
- Deploying from the `internal` branch with green CI is safe
