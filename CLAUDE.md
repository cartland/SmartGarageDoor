---
category: reference
status: active
last_verified: 2026-04-24
---
# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **Agents new to this repo:** see [`docs/AGENTS.md`](docs/AGENTS.md) for the documentation contract (categories, front-matter, source-of-truth map). This repo is designed for collaboration with multiple AI agents over time; docs are load-bearing.

## Project Overview

Smart Garage Door is an IoT system with three main components:
- **ESP32 Firmware** (GarageFirmware_ESP32/): FreeRTOS-based hardware control with sensor monitoring and relay control
- **Firebase Server** (FirebaseServer/): TypeScript serverless functions handling all business logic
- **Android App** (AndroidGarage/): Kotlin/Compose mobile app with MVVM architecture

**Key principle**: Server handles all critical business logic. Clients (hardware and mobile) are kept simple.

## Build Commands

### Android App (AndroidGarage/)
```bash
# Debug builds
./gradlew assembleDebug
./gradlew test
./gradlew connectedDebugAndroidTest

# Release builds (requires encrypted secrets)
export ENCRYPT_KEY="SecretPassphrase"
release/decrypt-secrets.sh
./gradlew assembleRelease
release/clean-secrets.sh

# Code quality
./gradlew lint
./gradlew ktlintCheck
```

### Firebase Server (FirebaseServer/)
```bash
# Development cycle (uses the Node version in FirebaseServer/.nvmrc = 22)
./scripts/firebase-npm.sh install
./scripts/firebase-npm.sh run build   # lint + tsc
./scripts/firebase-npm.sh run tests   # mocha (sets NODE_OPTIONS to disable native strip-types)
./scripts/firebase-npm.sh run lint

# Local development
firebase serve --only functions
firebase emulators:start

# Deployment
firebase deploy --only functions
```

`scripts/firebase-npm.sh` auto-sources nvm and switches to the Node version in `FirebaseServer/.nvmrc`, then forwards args to `npm --prefix FirebaseServer`. `scripts/validate-firebase.sh` does the same auto-switch before running the full validation. Manual `nvm use` is not needed.

**Mocha glob + Node strip-types pitfalls — both now enforced.** `validate-firebase.sh` asserts that the `tests` script in `FirebaseServer/package.json` (a) single-quotes the glob `'test/**/*.ts'` (PR #486 — unquoted glob silently skipped 84 tests for months because sh's `**` degrades to `*` without globstar) and (b) pins `NODE_OPTIONS='--no-experimental-strip-types'` (Node 22.18+/24 native strip-types breaks `import * as admin from 'firebase-admin'` under mocha+ts-node). If either assertion fails, the message explains the why; don't unquote the glob and don't drop the env var.

<!-- not-actively-maintained: ESP32 firmware build/deploy docs are out of primary documentation scope (focus is Android + Firebase server). See GarageFirmware_ESP32/README.md for active firmware guidance. -->

### ESP32 Firmware (GarageFirmware_ESP32/)
```bash
# Setup and configuration
idf.py menuconfig      # Configure WiFi credentials and server settings

# Build and deploy
idf.py build
idf.py flash
idf.py monitor         # View serial output

# Component testing with fakes
idf.py menuconfig      # Enable fake implementations for testing
```

## Architecture Patterns

### Server-Centric Design
- ESP32 reports raw sensor data; server interprets door state changes
- Android displays server-computed door status
- All business logic (event interpretation, notifications, error detection) lives on server
- Enables feature updates without client changes

### Android Clean Architecture
- **Domain module** (`domain/`): Pure Kotlin types and repository interfaces — no Android deps
- **Data module** (`data/`): Pure Kotlin data source interfaces (`LocalDoorDataSource`, `NetworkDoorDataSource`, etc.) — abstracts Room/Ktor
- **UseCase module** (`usecase/`): Shared business logic in `commonMain` — `ButtonStateMachine`, fetch/push/snooze use cases. Also hosts all 5 ViewModels post-Phase 27–30.
- **ViewModels** (in `usecase/`): `DoorViewModel`, `AuthViewModel`, `RemoteButtonViewModel`, `AppLoggerViewModel`, `AppSettingsViewModel` — delegate to UseCases
- **Repositories** (impls in `data/`, interfaces in `domain/`): `DoorRepository`, `AuthRepository`, `RemoteButtonRepository`, `SnoozeRepository`, `DoorFcmRepository`, `ServerConfigRepository`, `AppLoggerRepository`, `AppSettingsRepository` — `PushRepository` was split into `RemoteButtonRepository` + `SnoozeRepository` in #203
- **Typed errors**: `AppResult<D, E>` and `NetworkResult<T>` with sealed error types — exhaustive `when`, no `else`. See ADR-010, ADR-011
- **Platform bridges**: `AuthBridge`, `MessagingBridge` decouple Firebase SDK — enables unit testing and future iOS
- **DI**: kotlin-inject (`AppComponent`) — Hilt fully removed. See `AndroidGarage/docs/archive/DI-MIGRATION.md`
- **Local storage**: Room database with offline-first caching
- **Network**: Ktor HTTP client + kotlinx.serialization, `NetworkResult<T>` at data source boundary

<!-- not-actively-maintained: ESP32 component architecture is reference-only. See GarageFirmware_ESP32/README.md for current firmware structure. -->

### ESP32 Component Architecture
```
Components:
├── garage_hal/          # Hardware abstraction layer
├── door_sensors/        # Sensor management with debouncing
├── button_token/        # Secure button press protocol
├── wifi_connector/      # WiFi connectivity management
└── garage_http_client/  # HTTPS communication with cert validation
```

### Firebase Functions Organization
```
HTTP Functions:     Handle client requests (door status, button presses)
Pub/Sub Functions:  Scheduled tasks (error checking, notifications, cleanup)
Firestore Functions: Event-driven processing on data changes
```

## Testing Approaches

### Android
- Unit tests: JUnit + Mockito for ViewModels/Repositories
- UI tests: Compose testing framework
- Performance: Baseline Profiles + Macrobenchmarks

### Firebase Server
- Unit tests: Mocha + Chai with TypeScript
- Integration tests: Event processing and FCM functionality
- Type safety: Full TypeScript coverage

### ESP32 Firmware
- Configurable fake implementations via menuconfig
- Component-level testing without hardware dependencies
- Hardware-in-the-loop testing for validation

## Security Architecture

### Authentication Flow
1. Android: Google Sign-In → Firebase Auth token
2. Server: Token validation + user allowlist verification  
3. ESP32: Button token protocol prevents unauthorized button presses

### Secrets Management
- **Android**: GPG-encrypted local.properties with automated decrypt/encrypt scripts
- **Firebase**: Environment variables and server configuration endpoints
- **ESP32**: Menuconfig for development, NVS storage for production

## Development Workflow

### Local Validation
Run `./scripts/validate.sh` before pushing. It mirrors CI: spotless (all modules), lint, unit tests (3 variants), domain tests, debug build, screenshot test compilation, instrumented test compilation, and Room schema drift check. Writes a validation marker so the git-guardrails hook can warn on stale pushes.

The `compileDebugAndroidTestKotlin` step (added after #604) catches signature-breaking changes to public Composables that `androidTest/` sources call — these used to slip past pre-submit because validate.sh didn't compile that source set, then surface only in the post-merge instrumented-tests job. Running the test rules still requires a device; the compile step does not.

**Always run `validate.sh` end-to-end before the FIRST push of any PR**, especially when the PR touches: a `domain/` interface (any new method ripples to `test-common` fakes), DI wiring (`AppComponent.kt`), theme / colors (the `checkHardcodedColors` lint), VM ↔ UseCase boundaries (the `checkLayerImports` lint), or anything in `theme/`, `usecase/`, or `data-local/`. Substituting `:androidApp:assembleDebug` + `generate-android-screenshots.sh` is faster but **misses three categories of checks**: (a) custom architecture lints (`checkLayerImports`, `checkHardcodedColors`, `checkSingletonCaching`, `checkPreviewCoverage`, `checkNoBareTopLevelFunctions`, `checkNoLocalConfigurationDimensionReads`, `checkNoRawSafeDrawingPaddingValues`, `checkAuthStateProjection`) only run as part of validate.sh; (b) `:test-common` compile across variants — `assembleDebug` doesn't fully compile the test source sets that depend on `test-common`, so adding a method to a `domain` interface breaks fakes silently until you compile a test target; (c) `:usecase:testDebugUnitTest` (catches breaking constructor changes in fakes / test builders). Skipping validate to "ship fast" is net slower for any PR that crosses these boundaries — `android/234` (2.16.20) burned three CI cycles + a hotfix because each round of CI failures was a different latent issue, and validate.sh would have surfaced all of them in one local round.

**Reading validate.sh output: trust the printed `FAIL` / `PASS` markers, not the runtime's exit-code summary.** validate.sh runs checks sequentially and stops at the first failure, but the wrapper does NOT always propagate Gradle's non-zero exit code upstream — the BACKGROUND TASK notification can report `exit code 0` while the printed output actually says `[FAIL] hardcoded colors`. Always grep / scan the output for `FAIL` and `BUILD FAILED` before treating a validate run as green. The release script reads the validation marker file (which is written only on full success) so the marker is authoritative for release-time gating, but for "did my push pass" you must read the output.

**Be on the PR branch when validating, not main.** Common foot-gun: PR fails CI → switch to main to investigate → run validate locally → "passes" → confused why CI fails. validate.sh on main validates main, which doesn't include the PR's changes. Always `git checkout <pr-branch>` first.

### Instrumented Tests
Run `./scripts/run-instrumented-tests.sh` when changing Room entities/DAOs, DI wiring (AppComponent), navigation, or Activity lifecycle code. Requires a connected device or emulator. Not part of `validate.sh` (too slow for every run). A git hook warns on push when these files are changed.

### CI Architecture

**Android:**
- **Pre-submit** (`ci.yml` → `ci-checks.yml`): Runs on PRs. Gate job `Android CI Complete` is the required status check.
- **Post-merge** (`ci-post-merge.yml` → `ci-checks.yml` + instrumented tests): Runs on push to main. Auto-creates GitHub issue on failure (`ci-failure/post-merge`), auto-closes on fix with flakiness detection.

**Firebase** (mirrors Android):
- **Pre-submit** (`firebase-ci.yml` → `firebase-ci-checks.yml`): Runs on PRs. Gate job `Firebase CI Complete` is the required status check.
- **Post-merge** (`firebase-ci-post-merge.yml` → `firebase-ci-checks.yml`): Runs on push to main. Auto-creates GitHub issue on failure (`ci-failure/firebase-post-merge`), auto-closes on fix.
- The `firebase-deploy.yml` `verify-ci` step matches the check-run named `Firebase Checks / Unit Tests` exactly. That's the name produced by the reusable workflow when called via the `Firebase Checks` caller job (see `firebase-ci.yml`).

**Docs-only fast path (Android + Firebase):**
- When every changed file matches `**/*.md`, `docs/**`, `.claude/**`, `LICENSE`, `.gitignore`, or `AndroidGarage/distribution/whatsnew/**`, the `checks` reusable workflow is skipped and the gate jobs post success in ~20–30s.
- Mixed PRs (docs + code) take the full pipeline. Anything under `.github/**` or `scripts/**` is NOT docs — workflow and script edits still trigger full CI.
- Rule: skip CI only when the change cannot affect what CI verifies.
- The `Script unit tests` required check is a separate always-run workflow (no `paths:` filter), so it **does** run on docs-only PRs too — a fast ~6s gate, not part of the skipped `checks` workflow.

**Renaming a branch-protection-required gate job (ordering rule):**
- `Android CI Complete` (Android pre-submit) and `Firebase CI Complete` (Firebase pre-submit) are listed in branch protection's required status checks. Their names cannot be changed in a single PR without stalling every subsequent PR.
- Correct order: (1) PR adds a new gate job alongside the old one (both run); (2) wait for the new check-run to appear on a completed PR; (3) `gh api repos/:owner/:repo/branches/main/protection/required_status_checks/contexts --method PUT --input <(echo '["<new name>","<other required>"]')` — swaps the required contexts; (4) follow-up PR deletes the old gate job.
- **NEVER delete the old gate job before removing it from required contexts.** Reverse order leaves branch protection expecting a check-run no workflow produces, which blocks every PR. Recovery is re-adding the old context via the same `gh api` call.
- This was used to rename `CI Complete` → `Android CI Complete` in PRs #474 + #475.

**Required status checks (branch protection):** `Android CI Complete`, `Firebase CI Complete`, `Script unit tests` (added 2026-06-09), and `Doc front-matter` (added 2026-06-11 — `.github/workflows/doc-frontmatter.yml` runs `scripts/check-doc-frontmatter.sh` on every PR; closes the gap where the AGENTS.md front-matter contract was only enforced by `validate.sh` at release time). Read/modify the list with `gh api repos/cartland/SmartGarageDoor/branches/main/protection/required_status_checks/contexts` — GET to read, `--method PUT --input <file>` to replace (PUT replaces the **whole** list, so include every context you want kept; write the bare JSON array to a temp file to avoid `$(...)`/`<(...)` in the command).

**A path-filtered workflow must NEVER be a required check.** A required check that uses `on: { pull_request: { paths: [...] } }` simply does not run on a PR touching none of those paths — its context then sits in `Expected`/pending forever and the PR can never merge. Before promoting any check to a required context: remove its `paths:` filter so it runs (and concludes) on *every* PR, and add `timeout-minutes` so a stuck runner fails fast instead of blocking for the 6h default. `Script unit tests` (`.github/workflows/scripts-tests.yml`) is the canonical example — it runs `node --test` on the `.github/scripts/` Node scripts on every PR (~6s, no deps) precisely so it's safe as a required gate. Verified before promoting by opening a docs-only PR and confirming the check still ran (#864 → throwaway verify PR #865 → promote, 2026-06-09).

**Node scripts under `.github/scripts/` have unit tests.** Pure logic lives in `lib/*.mjs` (no I/O, deps injected) with `*.test.mjs` beside it; `scripts-tests.yml` runs them. Target tests by glob (`node --test '.github/scripts/**/*.test.mjs'`) — `node --test <dir>` would also import the non-test `.mjs` entry points (which pull in `googleapis`) and fail with `MODULE_NOT_FOUND`.

**Screenshot generation**: Use `./scripts/generate-android-screenshots.sh` (never run screenshot Gradle tasks directly — hooks block this).

**Conflict on `SCREENSHOT_GALLERY.md` or `PREVIEW_COVERAGE.md` during rebase**: both files are *generated* by `./scripts/generate-android-screenshots.sh`. Don't try to merge by hand — the deterministic resolution is regenerate.
```bash
git checkout --ours AndroidGarage/android-screenshot-tests/SCREENSHOT_GALLERY.md
./scripts/generate-android-screenshots.sh
git add AndroidGarage/android-screenshot-tests/SCREENSHOT_GALLERY.md
git rebase --continue
```
`git checkout --ours` clears the conflict in the working tree; the regen produces the canonical merged content from current sources. Hit this resolving PR #675 against PR #674 — both PRs had added screenshot tests, and the gallery line ordering conflicted.

**Preview coverage is enforced.** `checkPreviewCoverage` (in `validate.sh`) scans `androidApp/src/main/` for `@Preview`-annotated `*Preview` Composables and fails the build if any aren't imported by a screenshot-test source under `android-screenshot-tests/src/screenshotTest/`. The current report is committed at `AndroidGarage/android-screenshot-tests/PREVIEW_COVERAGE.md`. If a preview shouldn't be screenshot-tested, mark it `private` — the import-based detection naturally excludes it. Adoption motivation: PR #623 → #625 added 4 new `TitleBarCheckInPill*` previews that no test imported, so the framed README screenshot diverged from production for two PRs in a row before the gap was noticed.

**Local screenshot regen produces blank / degraded PNGs on this Mac.** `./scripts/generate-android-screenshots.sh` runs to completion (`Screenshot health check complete`, "21 likely blank" warnings) but the regenerated PNGs are ~15 KB blanks instead of the ~60–100 KB references on `main`. Layoutlib renders correctly when the test job runs in CI but not locally on this machine — root cause not investigated. **Workflow rule**: when a PR's source change should affect screenshots but local regen produces blanks, **discard the PNG diff** (`git checkout -- AndroidGarage/android-screenshot-tests/ AndroidGarage/screenshots/`) and ship the source change without PNG updates. Note in the PR body that reference PNGs are stale and will be regenerated on a working environment in a follow-up. The smoke test on a real device is the verification gate; PNGs are reference artifacts, not load-bearing for correctness. Recurred across multiple sessions (2.16.29, 2.16.30); writing it here so future agents discard immediately instead of debugging.

**Layoutlib gotcha — custom 960-viewport vectors inside LazyColumn item headers.** `painterResource(R.drawable.outline_signal_disconnected_24)` (Material's 960×960 viewport SVG-export) rendered via `Image` + `ColorFilter.tint` inside a LazyColumn item header silently dropped the entire item from the screenshot capture. Production rendered fine; only Layoutlib choked. Symptoms: the section just doesn't appear in the reference PNG, screenshot health-check still passes (file is non-empty), and "fixing" it by bumping `heightDp` does nothing. Workarounds, in preference order: (a) prefer Material `Icons.Outlined.*` / `Icons.Filled.*` (24-viewport vectors) for any drawable that lives inside a screenshot fixture; (b) use Material `Icon` Composable instead of `Image` + tint when you need to colorize. PR #623 hit this with the stale-pill icon; switching to `Icons.Outlined.SignalWifiOff` fixed it.

### Room Database Safety

When modifying Room entities or DAOs: (1) increment the `version` in `@Database` (`AppDatabase.kt`); (2) run `./gradlew :androidApp:assembleDebug` to regenerate the schema; (3) commit the new schema JSON alongside the code change; (4) **declare a `Migration` or `@AutoMigration(from = N, to = N+1)` to preserve data.** Full safeguard list (schema drift check in `validate.sh`, guardrails hook, `RoomSchemaTest` contract tests, exported schema files): see `AndroidGarage/docs/ARCHITECTURE.md` § Database.

**Migration coverage is enforced** (PR #839, 2026-05-23). `RoomSchemaTest.everyVersionPairHasDeclaredMigration` parses `AppDatabase.kt` for declared `AutoMigration(from = X, to = Y)` entries and `DatabaseFactory.android.kt` for `MIGRATION_X_Y` constants, then asserts every consecutive schema-version pair has at least one declared migration. Skipping step (4) above fails this test with a clear message naming the missing pair. The check strips Kotlin comments before parsing so commented-out declarations don't count (that's the exact bug class — a developer commenting out the AutoMigration line "to test something" and not restoring it would otherwise pass). Schemas are also required to form a continuous chain — no gaps. **Companion runtime test:** `DatabaseSanityTest` (instrumented, in `:androidApp/src/androidTest/`) exercises a real Room DB end-to-end, but it uses `inMemoryDatabaseBuilder` and **never runs migration code itself**. The static check is the PR-time gate; an empirical `MigrationTestHelper`-based runtime test would need to live in commonTest or androidInstrumentedTest and was deferred because Room 2.7.2's KMP `MigrationTestHelper` constructor signatures are shadowed by the Android `Instrumentation`-coupled API when compiling for the Android target.

**Step 4 is load-bearing — do not skip.** The codebase uses `fallbackToDestructiveMigration(false)` (in `DatabaseFactory.android.kt`) which **drops every Room-managed table** on any version mismatch with no declared migration — not just the changed one. So a single-column index addition on `appEvent` would also wipe the user's `doorEvent` history. For data-preserving changes (adding columns with defaults, adding/removing indexes, adding tables), Room's auto-migration handles them with no spec class — declare `autoMigrations = [AutoMigration(from = 11, to = 12)]` in the `@Database` annotation, build, and inspect the generated `AppDatabase_AutoMigration_*_Impl.kt` to confirm it preserves your data. For non-trivial changes (column renames, type changes), write an explicit `Migration` class and register it on the database builder. This trap was hit in PR #660 — caught pre-release on review.

**Migration transitions are NOT empirically tested by the existing instrumented suite.** `DatabaseSanityTest` uses `inMemoryDatabaseBuilder`, which always starts at the latest version and so never runs migration code. To test a v(N)→v(N+1) transition with real data, use Room's `MigrationTestHelper` against an exported schema, or smoke-test the upgrade on the internal Play Store track (which is what we do today; the production-promotion gate is the actual test).

### AppComponent / kotlin-inject Safety

`@Singleton` is silent when misused. Two mechanical rules:

1. Every `@Singleton` provider must be reachable via an `abstract val x: T` entry point. Concrete `val x: T @Provides get() = ...` gives kotlin-inject nothing to override — `@Singleton` is ignored.
2. Every `@Provides fun` body takes its deps as parameters. Never call a sibling `provideX()` inside a body — that call is regular Kotlin and bypasses the `_scoped` cache.

When modifying `AppComponent.kt`: run `./gradlew -p AndroidGarage checkSingletonCaching` (in `validate.sh`); inspect `androidApp/build/generated/ksp/debug/.../InjectAppComponent.kt` if it fails. New `@Singleton` state-owning providers need a matching `abstract val` + an `assertSame` identity test in `ComponentGraphTest`.

Full validation procedure, the android/170 postmortem that motivated these rules, and the failure-mode catalog: see `AndroidGarage/docs/DI_SINGLETON_REQUIREMENTS.md` and the kotlin-inject pattern guide at `AndroidGarage/docs/guides/kotlin-inject.md`.

**Two DI components mirror each other by hand — and `validate.sh` only checks one.** A class in a shared KMP module (`viewmodel/`, `usecase/`, `data/`) that is wired for both platforms has a provider in **both** `androidApp/.../di/AppComponent.kt` (Android) **and** `iosFramework/.../NativeComponent.kt` (iOS). When you change such a class's constructor (add/remove a param), you MUST update **both** providers in the same PR. The trap: `./scripts/validate.sh` compiles **Android targets only** — it does not compile the iosFramework iOS targets, so a stale `NativeComponent` provider passes validate.sh *and* the Android required checks clean. Only the separate **iOS CI** (`Build iOS app + framework test`) catches it, and iOS CI is **not** a required status check — so a PR can auto-merge with a broken iOS `main`. After editing any ctor in `viewmodel/`/`usecase/`, grep `NativeComponent.kt` for the matching `provide*` and update it too. Empirical: PR #871 (door-history load-more) updated only `AppComponent` → broke iOS on main (`No value passed for parameter 'fetchOlderDoorEventsUseCase'`) → hotfix #873.

### ViewModel fetch methods: set `Complete` explicitly on Success (ADR-023)

Every ViewModel method driving a `LoadingResult<T>` MUST set `LoadingResult.Complete(result.data)` in the `AppResult.Success` branch. `MutableStateFlow` dedups by equality; when a fetch returns the same value as cached, no observer fires and Loading latches forever (motivating bug: 2.4.4 Home-tab regression, PR #518).

Full rule, code example, and ADR-022 compatibility argument: see `AndroidGarage/docs/DECISIONS.md` ADR-023.

### Adding per-user feature flags

The repo gates UI features per-user via a server-maintained email allowlist edited in the Firebase console. The canonical example is the **Function List** screen (PR #573). The full pattern — file checklist, naming conventions (`featureXAllowedEmails` config key, `XAccess` route, `featureX: Boolean` domain field), `Boolean?` tri-state semantics, what NOT to do — lives in [`docs/FEATURE_FLAGS.md`](docs/FEATURE_FLAGS.md). When adding a new flag, read that doc first; it answers "do I create a new endpoint or extend the existing one?" (per-feature today, bulk deferred to ~feature #3) and "what files do I touch?" (8 on the server, 6 on Android, 1 wire-contract fixture).

### Wire-contract fixtures (`wire-contracts/`)

Shared JSON fixtures pin the over-the-wire shape of HTTP endpoints between the Firebase server and the Android client. Each endpoint gets a directory under `wire-contracts/<endpointName>/` with one `response_<scenario>.json` per documented response. **Both** the server's Mocha tests AND the Android Ktor data-source tests load the same files, so a unilateral rename or shape change fails the test on at least one side. Production decoding stays `ignoreUnknownKeys = true` for forward-compat; tests deserialize the same fixtures in strict mode (`ignoreUnknownKeys = false`) so renamed/missing keys throw `SerializationException` instead of silently coercing to defaults. Setup + the rationale (vs. OpenAPI / protobuf) live in [`wire-contracts/README.md`](wire-contracts/README.md). When adding a new HTTP endpoint, drop a fixture file alongside its tests on day one.

### One ViewModel per screen (ADR-026)

Each `*Content.kt` screen Composable imports at most one ViewModel. New screens get a dedicated VM that aggregates whatever UseCases that screen needs — `FunctionListViewModel` is the canonical example. Sub-component Composables (e.g. `RemoteButtonContent`) import zero VMs and take state via parameters from their parent screen.

Enforced by `./gradlew -p AndroidGarage checkScreenViewModelCardinality`, hooked into `validate.sh`. **`AndroidGarage/screen-viewmodel-exemptions.txt` is empty as of `android/213` (2.15.3)** — every screen complies with the 1:1 rule. The check still fails when an exempt entry has been refactored to comply but stays in the file (so stale entries don't accumulate); keep the file empty unless a real, intentional, time-boxed exemption is added.

The legacy multi-purpose VMs (`AuthViewModel`, `DoorViewModel`, `RemoteButtonViewModel`, `AppSettingsViewModel`, `AppLoggerViewModel`) have all been deleted. The only remaining VMs are screen-scoped: `HomeViewModel`, `DoorHistoryViewModel`, `ProfileViewModel`, `FunctionListViewModel`, `DiagnosticsViewModel`. Each depends only on UseCases (Phase 43) + sanctioned ADR-015 managers (`CheckInStalenessManager`, `LiveClock`) + `DispatcherProvider` + primitives. **Non-screen orchestration code (`AppStartup`, FCM service, etc.) uses UseCases directly — never a ViewModel.** If you find yourself adding a VM dep to non-screen code, that's a signal to extract a UseCase instead.

When adding a new screen: write the ViewModel first (in `usecase/.../<X>ViewModel.kt`), wire it into `AppComponent.kt` as a non-`@Singleton` `@Provides`, then resolve it from the screen via `viewModel { component.<x>ViewModel }`. Full rationale in `AndroidGarage/docs/DECISIONS.md` ADR-026.

### Konsist — redundant architecture enforcement (additive to buildSrc)

[Konsist 0.17.3](https://github.com/LemonAppDev/konsist) is a `testImplementation` dep of `:androidApp` (added 2026-05-22 via PRs #836 + #837). The Konsist tests live under `AndroidGarage/androidApp/src/test/java/com/chriscartland/garage/konsist/`.

**Adoption posture is EXPLICITLY ADDITIVE, not migration.** Every Konsist test mirrors a rule that is ALSO enforced by an existing `buildSrc/` Gradle task — both run on every PR. The duplication is intentional: two enforcement points, one structural (Konsist's typed PSI) and one textual (`buildSrc` grep), catch slightly different failure modes and surface the rule's existence in two CI logs.

Current Konsist tests:
- `ScreenViewModelCardinalityKonsistTest` — mirrors `checkScreenViewModelCardinality` Gradle task (ADR-026 / one VM per `*Content.kt`)
- `ImportBoundaryKonsistTest` — mirrors per-module `checkImportBoundary` tasks (6 KMP modules) + project-wide `org.mockito.*` ban
- `ComposableNullableDefaultKonsistTest` (PR #844, 2026-05-23) — blocks `T? = null` UI-data parameters on the SCREEN-LEVEL Composable in each `*Content.kt` (function name == file basename). Excludes `*ViewModel? = null` (route-entry test injection), `Boolean? = null` (tri-state feature flag), and function-type parameters. Motivation: PR #625 — `HomeContent.deviceCheckIn: DeviceCheckInDisplay? = null` let fixtures silently omit a production-rendered pill.

**`file.name` gotcha (Konsist 0.17.3).** `KoFile.name` returns the simple name **without** the `.kt` extension (`HomeContent`, not `HomeContent.kt`). The intuitive filter `it.name.endsWith("Content.kt")` matches **zero** files and the test passes vacuously. Use `it.path.endsWith("Content.kt")` instead. `ScreenViewModelCardinalityKonsistTest` had been silently vacuously-passing since PR #836 (2026-05-22) for exactly this reason; fixed in PR #844 alongside the new check. Both Konsist tests now use the corrected pattern AND the scope-sanity `require(filesInScope.isNotEmpty())` below.

**Drift policy (load-bearing — these rules are kept in sync by hand):**
1. When a module's `allowedPrefixes` list in `build.gradle.kts` changes, update the matching test method in `ImportBoundaryKonsistTest.kt`.
2. When `ImportBoundaryCheckTask`'s default `forbiddenPrefixes` changes, update `ImportBoundaryKonsistTest.defaultForbidden`.
3. When an exemption is added to `screen-viewmodel-exemptions.txt`, also add a Konsist `withoutNameContaining(...)` filter (or accept the test will go red and refactor instead).

A future "rules in sync" check could automate this, but isn't worth building until both enforcement points diverge in practice.

**Scope sanity pattern:** every per-module Konsist test contains a `require(filesInScope.isNotEmpty()) { "..." }` after the path-substring filter. Without it, a future Konsist scope misconfiguration (e.g., upstream library change to `scopeFromProduction()`) would silently make the test pass vacuously while drifting from the legacy Gradle task's coverage. The require triggers iff the filter returns 0 files; every audited module always has ≥1 production file. Apply this pattern to every new Konsist test that filters by path or package.

**When to add Konsist instead of buildSrc:** for new rules that fit Konsist's vocabulary cleanly (file imports, class names, naming conventions, package boundaries), prefer adding to the existing Konsist test classes rather than writing a new `buildSrc/` task. For rules requiring parsed semantics that PSI exposes (annotation analysis, type-hierarchy walks, cross-file declaration references), Konsist is strictly easier. For rules requiring parsing of GENERATED code (KSP output, R8 mapping files, AGP intermediates), keep using `buildSrc/` — Konsist parses source, not artifacts.

**When NOT to use Konsist:** rules that depend on Gradle's project / dependency graph (e.g., the existing `architecture` task that walks `Project.configurations`) belong in `buildSrc/` — Konsist has no Gradle awareness.

### Releasing Android
Use `./scripts/release-android.sh` — never create or push tags directly (hooks block `git tag`). For the *why* behind the release model (layered rules, conformance audit, deviations), see [`docs/RELEASE_STRATEGY.md`](docs/RELEASE_STRATEGY.md).

**Tag-membership checks:** the hook also blocks `git tag --contains <sha>` (any `git tag` invocation except `git tag -l`). To ask "is commit X in tag Y?", use `git merge-base --is-ancestor <sha> <tag>` instead — same answer, hook-friendly.

```bash
./scripts/release-android.sh              # Interactive (terminal only)
./scripts/release-android.sh --check      # Print state + copy-paste-ready next command
./scripts/release-android.sh --confirm-tag android/N  # Normal release
./scripts/release-android.sh --dry-run    # Preview without releasing
```

**Always start with `--check`.** It prints the exact command to run next, with SHAs and tag numbers already filled in. Copy and paste that command — don't retype from memory. The `--check` output detects normal, rollback, and emergency states and prints the appropriate command for each.

The script computes the next tag as `android/<highest + 1>`. The `--confirm-tag` flag is a safety check — it must match the computed tag, it cannot override it. Deploys to Play Store internal track only — never production.

**Design principle — overrides confirm reality.** Every override flag takes a value (SHA or tag) that must match what's actually in the repo. Wrong value = refused. This makes correct usage easy (read from `--check`) and accidental usage hard (you'd have to type the right value for the wrong situation).

**Validation is required by default.** Run `./scripts/validate.sh` before releasing. If validation hasn't passed on the commit, the release script will block. For emergencies use `--confirm-unvalidated-release <sha>` — the SHA must equal the target commit. Always ask the user before skipping validation.

**Changelog entry is required by default.** `AndroidGarage/CHANGELOG.md` must have a `## X.Y.Z` heading (matching `versionName` in `version.properties`) with a non-empty body before the tag can be pushed. The release script parses `versionName` and looks for the heading; missing or empty bodies block the release. Draft with `/update-android-changelog`. For emergencies use `--confirm-no-changelog <sha>` — the SHA must equal the target commit. Add the entry retroactively after a no-changelog release.

The changelog and the Play Store whatsnew are different files: `CHANGELOG.md` is the permanent internal history (every version, patches included); `distribution/whatsnew/` is rolling and only covers the current minor/major. Missing changelog entries silently went unnoticed through 2.5.0 (added in #548) — the gate closes that gap.

**Stat-cache stale after `validate.sh`** (was recurring — `android/193`, `android/194`, `android/248`, `server/24`). When the release script reported `Working tree has uncommitted changes` but `git status --short` was empty, it was git's stat-cache holding stale `lstat` info from files validate.sh touched (Gradle build outputs, marker file write): the quick dirty check uses timestamps; when they look new but content is identical, it falsely flagged dirty. **Both release scripts now run `git update-index -q --refresh` inside the clean-tree gate (before the `diff-index`/`diff` check), so this false positive should no longer occur** — the refresh re-stats every file and clears mtime-only false positives, and it can NEVER mask a real change (a file whose content differs from HEAD stays flagged). If you somehow still hit it (e.g. running an older script version), the manual fix is the same operation:
```bash
git update-index --refresh > /dev/null 2>&1   # forces git to re-stat everything
./scripts/release-android.sh --confirm-tag android/N    # then retry
```

**Rollback requires two steps** (intentionally hard to do accidentally):
```bash
git checkout android/M                 # 1. move HEAD to the commit you want to re-release
./scripts/release-android.sh --check   # 2. prints the rollback command with --confirm-hash and --confirm-rollback-from
```

**Versioning rule (see [CHANGELOG.md](AndroidGarage/CHANGELOG.md#versioning)):** major = rewrite or core-experience shift; minor = added or removed user-facing feature/capability; patch = fixes, polish, refactors. `CHANGELOG.md` logs every version; `distribution/whatsnew/` gets one line per minor/major (patches roll up).

### Play Store track-state log

`.github/workflows/play-track-snapshot.yml` records the current Play Store release-track state (internal / alpha / beta / production: versionCode → versionName, status, staged-rollout %) onto a single long-lived GitHub issue labelled `play-track-log`. The **latest** snapshot is written to the issue **body** (overwritten each run) and **also** appended as a **comment** (immutable, append-only history). It rolls over to a fresh issue past 1000 comments. Read-only against the store: `edits.insert` → `edits.tracks.list` → `edits.delete` (never commits). Reuses the `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` secret (the same SA that uploads — it has read access).

- **Triggers:** `workflow_dispatch` (run manually right after promoting a track in the Play Console) and `workflow_run` after *Android Release* succeeds (snapshots the new internal upload). Manual dispatch needs repo **Write** access (GitHub-enforced).
- **Why it matters:** release scripts only ever deploy to the **internal** track; promotions to alpha/beta/production are manual Console actions with no other record. This log is that record.
- **versionCode ↔ tag:** each `android/N` tag has `versionCode == N`, so the snapshot maps a code back to its `versionName` via `git show android/N:AndroidGarage/version.properties` (needs `fetch-depth: 0`).
- **Gotcha (baked into the workflow):** read a just-created issue's number straight from `gh issue create`'s output URL — `gh issue list` is eventually consistent and won't surface it yet. Rendering is unit-tested (`.github/scripts/lib/format-snapshot.mjs` + `play-track-snapshot.test.mjs`). Shipped 2026-06-09 in PRs #860, #862 (first-run race fix), #863 (body+history+rollover+tests).

### Determining what's actually deployed / live (verify, don't recall)

**Before telling the user what is live in production, check authoritative sources — NOT memory and NOT a doc's status header.** Both drift fast; deploy/release state is exactly the kind of fact memories and "Status:" lines get wrong. Empirical (2026-06-28 session): an agent twice reported stale state to the user — "you should deploy `server/28`" when it had been **live since 2026-06-10**, and "the resolved-notification is parked" when its flag had been **on since 2026-06-22** — both from trusting a memory description + a stale doc header. The authoritative sources:

- **Server (Cloud Functions) — what `server/N` is live:** `gh run list --workflow=firebase-deploy.yml --limit 8` → the latest `success` run is the deployed tag. Deploys are **cumulative from the tagged commit**, so everything merged up to that tag is live (e.g. `server/30` deploying means `server/28`'s pagination is live too). Each `server/N` tag pushed via `release-firebase.sh` triggers a deploy, so a tag existing ≈ deployed — but confirm with the run list.
- **Android — what version is on which track:** the `play-track-log` issue (label `play-track-log`, currently #861) **body** holds the latest snapshot (internal / alpha / beta / production → versionName). Releases only ever reach the **internal** track; an empty `production` row means there is no public production release (the normal state for this repo).
- **A live config flag's value** (e.g. `resolvedOnCloseEnabled`) is in Firestore `configCurrent/current` — it is **not** knowable from the repo at all. Ask the user or read the config; never infer it from a doc that says "off by default."

When a memory or doc header conflicts with these sources, the sources win — and fix the stale doc/memory in the same pass (that's what PR #947 did for the resolved-notification status).

### Play Store listing assets (icon, feature graphic, screenshots)

Uploading store-listing graphics is a **manual Play Console step** — the release workflow only ships the AAB + `whatsnew/`, never images. The procedure is the **`play-store-assets` skill** (`/play-store-assets`); read it before touching store assets. Shipped 2026-06-11 in PRs #882 (icon + feature graphic), #883 (generators + skill + staging), #884 (screenshots promoted to distribution).

**Two-location model:**
- `AndroidGarage/screenshots/store/` — **generated** staging (all candidates), committed, with an auto-generated `README.md` gallery. Regenerated by the generators; never hand-edited.
- `AndroidGarage/distribution/playstore/` — **curated** set that mirrors what's live in the store (committed). Updated by hand: copy from staging → PR → manual upload. **No generator writes into `distribution/`.**

**Generators:**
- `AndroidGarage/distribution/playstore/generate.sh` — icon-512 + feature graphic → staging (from `src/icon.svg`, a faithful port of `GarageDoorCanvas.kt`); also regenerates the **in-app launcher mipmaps** in `androidApp/.../res/` (app code, shipped in the AAB — distinct from the store-listing icon).
- `scripts/generate-store-screenshots.py` — phone + 7"/10" tablet screenshots → staging. **Wired into `scripts/generate-android-screenshots.sh`** (after the README framing step), so store screenshots regenerate with the normal `update-android-screenshots` flow; copying to `distribution/` is the only manual step. It **composes committed PNGs** (framed phone shots + CI-rendered tablet/3-pane reference renders) — no rendering, so it works despite local Layoutlib blank-render.

**Framing / aspect ratio (settled empirically — Play accepted these 2026-06-11):**
- **Phone**: the framed Pixel shots (`screenshots/framed/`, drawn by `scripts/frame-screenshot.py` — programmatic bezel, no asset) flattened onto **white**, native **~2.12:1**. Play **accepted** the native ratio — do NOT force 9:16 / add padding.
- **Tablet**: `frame_tablet()` wraps each landscape render in a programmatic tablet bezel centered on a **white 16:9 canvas** (7" → 3840×2160, 10" → 4494×2528). The white canvas both polishes the shot and makes it 16:9 in one step. Play states "16:9 / 9:16" and is stricter on tablets, so the native square-ish renders (1.28–1.6:1) DO need this; bare black bars looked bad (flatten transparent → black) — the fix was white background + a real frame, not bars.

### Releasing Firebase Server
Use `./scripts/release-firebase.sh` — same pattern as Android releases (same flags, same `--check` copy-paste workflow, same rollback recipe). Principles + conformance audit: [`docs/RELEASE_STRATEGY.md`](docs/RELEASE_STRATEGY.md).

```bash
./scripts/release-firebase.sh --check              # State + copy-paste-ready next command
./scripts/release-firebase.sh --confirm-tag server/N  # Normal release
./scripts/release-firebase.sh --dry-run            # Preview without releasing
```

**Always start with `--check`.** It prints the exact command to run next (normal, rollback, or emergency), with SHAs and tag numbers already filled in. Copy and paste — don't retype from memory.

The script computes the next tag as `server/<highest + 1>`. Deploys Cloud Functions only.

**Validation is required by default.** Run `./scripts/validate-firebase.sh` before releasing (runs `npm run build` + `npm run tests`, writes marker with commit SHA). For emergencies use `--confirm-unvalidated-release <sha>` — the SHA must equal the target commit. Remote Firebase CI status is also checked but is warn-only (not all commits run full CI).

**Changelog entry is required by default.** `FirebaseServer/CHANGELOG.md` must have a `## server/N` heading with a non-empty body before the tag can be pushed. See the file's header for the supersede rule (bug-chase chains may replace the predecessor's entry instead of appending). Draft with `/update-firebase-changelog`. For emergencies use `--confirm-no-changelog <sha>` — the SHA must equal the target commit. Add the entry retroactively after a no-changelog release.

**Rollback = two steps** (same as Android):
```bash
git checkout server/M
./scripts/release-firebase.sh --check   # prints --confirm-hash + --confirm-rollback-from command
```

**Firebase server operations:** See [`docs/FIREBASE_DEPLOY_SETUP.md`](docs/FIREBASE_DEPLOY_SETUP.md) — long-term maintenance guide. Covers: release process, rollback, monitoring & logs, cost hygiene, Node/runtime deprecation calendar, deployer-SA re-provisioning + rotation, required GitHub secrets, required GCP APIs, and a troubleshooting table. CI deploy was fixed 2026-04-21 after a long period of silent-failure (`firebase deploy` exiting 0 despite `⚠ failed to update function` — the doc describes how to recognize it and what role was missing).

**Database refactor plan:** See [`docs/FIREBASE_DATABASE_REFACTOR.md`](docs/FIREBASE_DATABASE_REFACTOR.md) — phased plan to centralize 18 `new TimeSeriesDatabase(...)` calls into typed per-collection singletons with in-memory fakes. Includes goals, backward-compatibility principles, long-term maintenance rules, and safety guards (contract tests, scope rules). Zero production data impact when followed; rollback via `git revert` at any phase boundary.

**Config authority rule** (active): production server config is the single source of truth for device build timestamps. The four pubsub/HTTP handlers read `body.buildTimestamp` / `body.remoteButtonBuildTimestamp` from config and throw on null (ERROR-level log) — no hardcoded fallbacks. If you see `buildTimestamp missing from config` in Cloud Logs, restore production config via `httpServerConfigUpdate` before considering a code revert. Full rule + operator runbook: [`docs/FIREBASE_CONFIG_AUTHORITY.md`](docs/FIREBASE_CONFIG_AUTHORITY.md). Historical rollout: [`docs/archive/FIREBASE_HARDENING_PLAN.md`](docs/archive/FIREBASE_HARDENING_PLAN.md) (Parts A and B, shipped through `server/17`).

**Handler pattern** (active): every HTTP/pubsub handler in `FirebaseServer/src/functions/` follows a pure `handle<Action>(input)` core + thin wrapper split. New handlers should follow the same pattern from the start. Full pattern + service-bridge convention + preserved-quirk callouts: [`docs/FIREBASE_HANDLER_PATTERN.md`](docs/FIREBASE_HANDLER_PATTERN.md). Historical rollout: [`docs/archive/FIREBASE_HANDLER_TESTING_PLAN.md`](docs/archive/FIREBASE_HANDLER_TESTING_PLAN.md) (H1–H6 across PRs #504–#516, shipped through `server/18`).

**Event-history pagination** (active, `server/28`+; page size raised 50→100 in `server/29`): `eventHistory` returns a windowed default (last 7 days, max 100, newest first) plus cursor pagination via an opaque `nextPageToken`; null token = end-of-history. The window is **universal** — when this deploys, an un-updated app's history view narrows to ~1 week. Wire-compatible (all legacy keys kept; client sends both `pageSize` and legacy `eventHistoryMaxCount`). Token format, the request decision tree, and the Option-B index reuse are in [`docs/EVENT_HISTORY_PAGINATION.md`](docs/EVENT_HISTORY_PAGINATION.md); the Android consumer side is ADR-028. **Deploy gotcha:** the core feature reuses the existing index, but the backward (`newer`) direction needs a new **ASC** `eventsAll` index. That index was **deployed 2026-06-10 and the newer direction is verified working in production** (still client-dormant — the UI only scrolls older). Note for *future* `firestore.indexes.json` changes: the index is **not** deployed by the functions release — run `firebase deploy --only firestore:indexes` separately, and Firestore builds composite indexes asynchronously (queries needing a still-`CREATING` index 500 until `READY`).

**Dormant config readers need encoding audits.** The `remoteButtonBuildTimestamp` config value has been stored **URL-encoded** since April 2021, but its reader was commented out for ~5 years. PR #492 (A1 v1) uncommented it without handling the encoding and would have passed URL-encoded strings to Firestore queries if deployed; caught pre-release and reverted in PR #494. **Before enabling any long-dormant config reader, verify the stored value shape matches what downstream code expects.** `decodeURIComponent()` is idempotent for plain ASCII, so defensive decoding is a safe pattern when shape is ambiguous.

### Secret Management (Android)
The app requires secrets in `local.properties` (decrypted from GPG at build time):
- `SERVER_CONFIG_KEY`, `GOOGLE_WEB_CLIENT_ID` — required for all builds
- `GARAGE_RELEASE_KEYSTORE_PWD`, `GARAGE_RELEASE_KEY_PWD` — release builds only
- Scripts: `release/decrypt-secrets.sh`, `release/clean-secrets.sh`

### PR Workflow
- **After modifying files, ask the user what to do with them.** Never end a session with a dirty tree without surfacing the question. Don't open a PR, commit, or revert autonomously — ask first. The user usually wants fast PR creation, but wants to confirm scope and timing each time files change.
- **Auto-merge — first-PR ask.** On the first PR of a session, ask whether to enable auto-merge. Offer three answers: one-time (this PR only), session default (apply to every later PR you open in this session), or no. Once a session default is given, don't re-ask about auto-merge — but still ask before opening each subsequent PR.
- Always create feature branches — never push to main
- Always `--squash --delete-branch` when merging PRs
- Never use `--admin` to bypass CI — enforce_admins is enabled
- Keep PRs small and focused (one concern per PR)
- Create multiple non-conflicting PRs in parallel — don't wait for CI on each one
- `cd` and `git -C` are blocked by hooks — run all commands from the repository root

**Branch deletion is repo-level, not flag-level.** `gh pr merge --squash --delete-branch` only deletes the head branch when gh performs the merge **synchronously**. When you enable auto-merge with `gh pr merge --auto --squash --delete-branch <N>`, GitHub performs the actual merge later — and `--delete-branch` is **silently ignored** in that path. Result: stale branches accumulate post-merge. The fix is the repo-level `delete_branch_on_merge: true` setting (toggled on 2026-04-25 after a one-time cleanup of ~470 stale branches). With it enabled, every merged PR — sync or auto-merge — has its head branch auto-deleted. To verify or re-enable: `gh api repos/cartland/SmartGarageDoor --jq '.delete_branch_on_merge'` (must return `true`).

**Deleting a remote branch when checked out on main:** the `git-guardrails.sh` hook blocks `git push origin :<branch>` because any push from `main` is rejected — even a delete refspec. Workaround: use the GitHub API directly, which sidesteps the local hook: `gh api repos/cartland/SmartGarageDoor/git/refs/heads/<branch> -X DELETE`. Same effect, doesn't require switching off main first. Useful for cleanup of orphan branches that don't correspond to a merged PR (those are NOT auto-deleted by the repo setting).

### Keeping PRs Mergeable
Branch protection requires PRs to be up-to-date with main before merging. This means PRs merge one at a time and each merge can make others stale.

**After every PR merge:**
```bash
# Check all open PRs for conflicts or behind status
gh pr list --state open --json number,mergeStateStatus --jq '.[]'
# Update each one
gh pr update-branch <number>
```

**Before creating a new PR:**
- Check which files open PRs touch: `gh pr diff <number> --name-only`
- Don't create a PR that modifies the same files as an open PR — it will cause merge conflicts
- If you must touch the same file, wait for the open PR to merge first

**If a PR has conflicts (mergeStateStatus: DIRTY):**
1. Checkout the branch locally
2. Rebase on origin/main: `git rebase origin/main`
3. Resolve conflicts
4. Force push: `git push --force-with-lease`

**Stacked PRs (dependent branches):**
When PRs must merge in order (e.g., PR B depends on PR A's code):

1. **Always use `--base main`** — GitHub CI only runs on PRs targeting protected branches. Using `--base feature-branch` means CI never triggers, and changing the base later does NOT re-trigger workflows.

2. **Document merge order in each PR body** — every PR in a stack must state its position and dependencies so reviewers (and auto-merge) don't merge out of order:
   ```
   ## Merge order
   This is PR 2 of 3 in the auth refactor stack.
   ⚠️ Merge AFTER #326. Do not merge before #326 lands on main.
   Full stack: #326 → #327 → #328
   ```

3. **Enable auto-merge on all PRs** — they serialize naturally. After PR A merges, PR B becomes stale (behind main), GitHub auto-updates it, CI re-runs, then auto-merge fires. Each waits for the previous CI cycle (~5-10 min per PR).

4. **PR diff shows parent commits temporarily** — after the parent squash-merges, GitHub auto-updates the stacked PR to show only its own changes. **GitHub's auto-update is not always reliable**, especially across long stacks (3+ PRs) where each squash-merge produces a different SHA from the original commit. Symptoms: child PRs flip to `mergeStateStatus: DIRTY` / `mergeable: CONFLICTING` after a parent squash-merges, even when the file changes don't actually conflict. The fix is a manual rebase per child PR — git's patch-id matching detects the squashed-version-of-old-commit and skips it via "warnings: skipped previously applied commit":
   ```bash
   # Disable auto-merge on the stuck child PR (mandatory — push is hook-blocked otherwise)
   gh api graphql -f query='mutation($id: ID!) { disablePullRequestAutoMerge(input: {pullRequestId: $id}) { pullRequest { number } } }' \
     -F id=$(gh pr view <N> --json id --jq .id)
   git fetch origin main
   git checkout <child-branch>
   git rebase origin/main          # auto-skips already-applied commits
   git push --force-with-lease origin <child-branch>
   gh pr merge --auto --squash --delete-branch <N>
   ```
   Hit this on every child PR in the spacing rollout (#680→#683 stack, 2026-05-08). Stage 4's branch had 4 pre-rebase commits (Stages 3a/3b/0/4); after the rebase only Stage 4's commit remained on top of main.

5. **Run `spotlessApply` (and any other formatter / linter auto-fix) on BOTH branches before pushing.** When iterating on the child PR — e.g., during a compile-fix cycle — formatting fixes naturally land only on the child's branch. If the child squash-merges first, its diff carries the corrected formatting into main, but the parent PR is left with stale, pre-fix code that fails CI's spotlessCheck. The parent's `mergeStateStatus` flips to `DIRTY` and it's dead-ended. Recovery is to close the parent as obsolete (its content already shipped via the child), but the cleaner pattern is to run `./gradlew :androidApp:spotlessApply` on the parent branch before opening the stack — same for any other check that auto-fixes (`./scripts/firebase-npm.sh run lint --fix` if the stack touches Firebase). PR #588 hit this on 2026-04-28; #589 (the child) merged with the spotless-fixed code, leaving #588 orphaned.

**Watch for PR starvation:** When many PRs queue up, the last one keeps getting pushed back as others merge ahead of it. Each merge makes it stale, requiring another CI run. If a PR has been open for a long time, prioritize merging it before creating new ones.

**Auto-merge rule:** Before modifying a PR in any way (pushing commits, amending, force-pushing), you MUST first check if auto-merge is enabled and disable it:
```bash
# Check if auto-merge is enabled
gh pr view <number> --json autoMergeRequest --jq '.autoMergeRequest'
# Disable auto-merge before modifying — NOTE: the guardrail hook blocks
# `gh pr merge --disable-auto` because any `gh pr merge` invocation must
# include --squash --delete-branch. Use GraphQL directly:
gh api graphql -f query='mutation($id: ID!) { disablePullRequestAutoMerge(input: {pullRequestId: $id}) { pullRequest { number } } }' \
  -F id=$(gh pr view <number> --json id --jq .id)
# After pushing, re-enable
gh pr merge --auto --squash --delete-branch <number>
```
A guardrail hook blocks `git push` when auto-merge is active on the target PR. If the push is blocked, disable auto-merge first (via GraphQL per above), push, then re-enable. Never push to a PR with auto-merge enabled — the merge may execute before the push arrives, silently losing commits.

**Auto-merge already fired — local branch is orphaned.** When the GraphQL `disablePullRequestAutoMerge` mutation returns `"Can't disable auto-merge for this pull request"` while `gh pr view N --json autoMergeRequest` still shows it set, the most likely explanation is the PR has **already merged** and the cached `autoMergeRequest` field is stale. Confirm with `gh pr view <N> --json state,mergedAt,headRefName`. If `state == "MERGED"`, the head branch was deleted on the remote (assuming `delete_branch_on_merge: true`) and your local branch is dead. Pushing it again creates a stale duplicate remote branch with no PR. Recovery: stash → `git checkout main && git pull` → new branch from main → pop stash → open a fresh PR. Delete any stale duplicate remote branch you accidentally created via `gh api repos/cartland/SmartGarageDoor/git/refs/heads/<branch> -X DELETE`. **Lesson:** before adding follow-up commits to a PR you opened earlier in the same session, check `state` — auto-merge may have fired during your validation cycle.

**Branch naming:** The `git-guardrails.sh` hook matches the pattern `\b(main|master)\b` on `git push` command lines to block pushes to the default branch. This also triggers false-positives on branch names containing `main` as a substring (e.g., `refactor-main-kt`). Avoid those substrings in branch names, or rename before pushing.

**Compound-command trap:** the same `\bmain\b` regex is greedy across the entire command line, so chaining `git push -u origin <feature-branch> && gh pr create --base main ...` is blocked even when the push itself is fine — the hook sees `git push` + `\bmain\b` (in `--base main`) together. Workaround: run them as **two separate Bash calls** (push first, then `gh pr create`). Same trap fires for any chained command after `git push` that includes `main` literally (e.g. `git push && open https://github.com/.../tree/main`).

**Compound-command trap variant 2 — current-branch check fires before checkout runs:** `git checkout -b feature/X origin/main && ... && git push -u origin feature/X` is blocked with `BLOCKED: You are on 'main'. Switch to a feature branch before pushing.`, even though the checkout earlier in the chain would put you on `feature/X` before push runs. The hook is a `PreToolUse` that evaluates `git rev-parse --abbrev-ref HEAD` at hook-eval time, before bash runs anything. The earlier `git checkout` in the same compound call doesn't help. Workaround: split into **two Bash calls** — `git checkout -b feature/X origin/main && git add ... && git commit ...` first, then `git push -u origin feature/X` separately.

**Branch name collision with existing refs:** a long-lived `release` branch exists on `origin`. Pushing a new branch with a path like `release/server-14-changelog` fails with `! [remote rejected] ... (directory file conflict)` because git refuses to create a ref nested under an existing branch name. Use `changelog/*`, `docs/*`, `hardening/*`, or similar prefix instead of `release/*` for ordinary work.

**Auto-merge stuck on stale base:** if a PR has auto-merge enabled, all CI checks green, and `mergeStateStatus: CLEAN`, but it still hasn't merged, the likely cause is a stale base commit. GitHub doesn't always auto-update PRs when `main` moves forward. Fix: `gh pr update-branch <number>` — pulls main into the PR branch, CI re-runs, auto-merge then fires. Check for this if a PR sits open much longer than the normal CI cycle (~5–10 minutes).

### Dev Mode
Toggle: `touch .claude/.dev-mode` (enable) / `rm .claude/.dev-mode` (disable).

When active, a Stop hook blocks Claude from ending its turn and directs it to:
1. Check open PRs — merge any that passed CI (`--squash --delete-branch`)
2. Read `docs/TESTING.md` and `docs/MIGRATION.md` for next action items
3. Pick the highest-priority undone item
4. Create PRs on separate branches that don't conflict with each other
5. Don't wait for CI — start the next PR while CI runs on the previous one
6. Run `./scripts/validate.sh` before pushing code changes
7. Keep PRs small and focused (one concern per PR)

**Interaction with backlog hook:** The `check-pr-backlog.sh` hook warns at 5+ open PRs and blocks at 10+. Dev mode yields (stops blocking) when 10+ PRs are open AND none are mergeable — this prevents an infinite loop where dev mode says "keep working" but backlog says "merge first" with nothing to merge.

**Stopping dev mode:** Run `rm .claude/.dev-mode` yourself when ANY of these apply:
1. **User says stop** — "stop", "pause", "that's enough", or confirms via `AskUserQuestion`.
2. **Work is done** — all planned PRs are created/merged and no more meaningful parallel-PR-friendly work remains. Don't keep the loop running just to search for marginal tasks.
3. **Blocked** — all open PRs are waiting on CI with nothing else to do.

Do not just tell the user to run it — the next Stop hook fires before they can act, leaving the loop active. If intent is ambiguous, ask via `AskUserQuestion` first. The user can still `rm` it themselves or press Ctrl+C.

### Claude Hooks (.claude/hooks/)
- **block-admin-bypass.sh** — Denies `--admin` on PR merges
- **git-guardrails.sh** — Blocks push to main, force push, destructive commands; enforces squash merge; warns on stale validation; blocks push to branches with auto-merge enabled
- **check-pr-backlog.sh** — Warns at 5+ open PRs, blocks at 10+
- **dev-mode.sh** — Keeps Claude working when `.claude/.dev-mode` exists
- **warn-shell-loops.sh** — Warns on `for`/`while` loops (prefer separate Bash calls)
- **warn-command-substitution.sh** — Warns on `$(...)` command substitution (harder to review)
- **block-large-image-read.sh** — Blocks Read on images whose long edge exceeds 2000px (many-image conversations reject these with InputValidationError). Only Read; Write/Edit/Bash unaffected so scripts can still generate and commit oversized PNGs (e.g. 1294×2744 framed screenshots). Resize and Read the smaller copy: `sips -Z 2000 path/to/image.png --out /tmp/preview.png`. Added in PR #649.

### Testing Philosophy
- Tests must add value — no coverage for coverage's sake
- Prefer fakes over Mockito mocks (aligns with KMP migration target)
- CI is the deployment gate — if CI passes, the app is safe to ship
- Reference: [battery-butler](https://github.com/cartland/battery-butler) for testing patterns AND build/check infrastructure (it has a richer `buildSrc/` plugin set — `PreviewCoverageCheckTask` was ported from there in #629; `PreviewTimeCheckTask`, more sophisticated `ImportBoundaryCheckTask`, etc. are unported reference patterns)
- **Don't unit-test transient `MutableStateFlow` values when the underlying work doesn't actually suspend.** With non-suspending fakes, the `flag.value = true` → suspend call → `flag.value = false` sequence completes synchronously inside `runCurrent()`, and `MutableStateFlow`'s equality-based conflation means observers may only ever see the final `false`. Two options: (a) make the fake actually suspend at the right boundary (e.g. add `yield()` or use a `CompletableDeferred`), or (b) test only the safety property — the flag returns to false after the action — and rely on the screenshot test for the visual + manual smoke for the UX. Option (b) is the default. Hit this in PR #675 attempting to test `clearInFlight`
- **Device-only behavior needs a pre-release verification design.** Several categories of behavior in this app are only observable on a physical device + a real Play Store install: `WindowInsets.safeDrawing` and gesture-nav coexistence (Compose previews / Layoutlib screenshots render with zero system insets), real FCM delivery, ESP32 sensor + button-token round-trip, real Google Sign-In token semantics, R8/Proguard release builds (debug builds skip them), Doze / JobScheduler timing, and display-cutout interaction. **When a PR touches any of these, the design must include a way to verify it BEFORE shipping** — treat the verification gap as a design defect, not a workflow inconvenience. Practical rules: (1) for `WindowInsets` / gesture nav / cutouts / IME — write a screenshot fixture that injects a fake non-zero inset (via `CompositionLocalProvider` + a Box that simulates the inset); the fixture diff in the PR proves the math. (2) For FCM payloads / topic names — extend `FcmPayloadParserTest` or a `wire-contracts/` JSON fixture; don't rely on the device for parser correctness. (3) For release-only behavior (R8, signing) — opt into `VALIDATE_R8=1 ./scripts/validate.sh`. (4) When *none* of the above is feasible, note the gap once in the PR body ("verification gap — only observable on a real device") and stop there. **Do not add the smoke item to `AndroidGarage/docs/PENDING_SMOKE_TESTS.md` unprompted, and do not list "smoke this on a device" as a follow-up step in release reports or status summaries.** That file is user-maintained; the user runs smoke when they choose to and almost always refuses manual verification before a release (stated 2026-05-29). The 2.16.12 edge-to-edge fix (`android/226`) is the canonical "I only saw it after release" example: Compose previews render with zero insets, so the harsh-cutoff bug above the gesture nav was invisible until it hit a real device. Going forward, screenshot fixtures with simulated insets are warranted for any inset-sensitive change. Self-check before opening a UI PR: *if CI can only verify the code compiles and the screenshot renders with default zero-insets, what does production add that I'm not testing?*
- **CLI testing is the release gate, period.** `validate.sh` + unit tests + instrumented tests (on a connected device or emulator, run via `./scripts/run-instrumented-tests.sh`) + screenshot tests + wire-contract fixtures must be sufficient. The canonical release "done" state ends at *deployed to Play Store internal track* (Android) or *Cloud Functions deploy complete with `✔ Deploy complete!` marker* (Firebase) — there is no "now go smoke X on a device" addendum. If a behavior cannot be verified by CLI testing, that is a verification-gap design defect per the prior bullet — build the fixture inside the PR rather than deferring to manual verification.

## Documentation

Detailed project documentation lives in `AndroidGarage/docs/`:

- [Architecture](AndroidGarage/docs/ARCHITECTURE.md) — System overview, Android layers, data flows, DI, state management
- [Decisions](AndroidGarage/docs/DECISIONS.md) — Architectural decision records (ADRs): server-centric design, tech stack, testing philosophy, migration targets
- [Migration](AndroidGarage/docs/MIGRATION.md) — Phased roadmap toward battery-butler tech stack (kotlin-inject, Ktor, clean architecture, KMP)
- [Testing](AndroidGarage/docs/TESTING.md) — CI stability plan, testing phases, priority order
- [Library Bugs](AndroidGarage/docs/library-bugs/) — Known third-party library bugs with mitigations

Cross-component runbooks in `docs/`:

- [Dependency Upgrades](docs/DEPENDENCY_UPGRADES.md) — Sequencing playbook for multi-PR upgrades: 4-bucket framework (docs / pre-submit / runtime / deploy), per-class risk register, operational gotchas, and the "stay on Node 22" decision with tripwire to revisit. Read this before starting any dependency-upgrade sweep.

## Safety Rules

### FCM Push Notifications
Push notifications are the hardest feature to verify in production. If topic names or payload parsing change, notifications silently stop working with no visible error.
- Never change FCM topic name format or payload key names without explicit user approval
- Verify contract tests pass before modifying: `FcmTopicTest`, `FcmPayloadParsingTest`
- Wire-contract fixtures pin the payload Map<String, String> shape across server + Android (PR #842, 2026-05-23): `wire-contracts/fcmDoorEvent/payload_{closed,open,opening}.json` and `wire-contracts/fcmButtonHealth/payload_{online,offline,online_with_lastpoll}.json`. Server tests (`EventFCMTest.ts`, `ButtonHealthFCMTest.ts`) `deep.equal` against the loaded fixture; Android `FcmPayloadParsingTest.fixturePayload*Parses` loads the same JSON as `Map<String, String>` and asserts the parse. A unilateral rename on either side breaks at least one half.
- FCM-related code: `FCMService`, `DoorFcmRepository`, `FcmPayloadParser`, topic subscription flow
- **Reliability findings** (delivery gaps + fixes) for both push data and the open-door notification: [`docs/NOTIFICATION_RELIABILITY.md`](docs/NOTIFICATION_RELIABILITY.md) — includes the settled **recommended final architecture** (additive, version→topic isolation, warning stays OS-rendered, Phase 2 parked) + a **manual-testing/live-diagnosis runbook** (project `escape-echo`, the door-topic FCM `validateOnly` sends, and the Firestore reads for device check-in status). **Shipped:** at-most-once send (R5, `server/30`); foreground-drop + no-app-channel (R6+M4, Android `2.20.0`/`android/252`, released to internal — the foreground warning renders via `DoorNotificationPresenter.showWarning`, and warning+resolved share one app-owned HIGH "Garage door" channel + `ic_notification_garage` icon + `(tag,id)` slot via manifest `default_notification_channel_id`/`_icon` + an eagerly-created channel); tap-to-open (M5, Android `2.20.1`). **Remaining:** manual-only staleness recovery (R1), runtime topic change (R2).
- **App-built notifications need an explicit `contentIntent` or tapping does nothing.** FCM auto-attaches a launch intent only to **OS-rendered** notification-payload messages; any `NotificationCompat`-built notification (`DoorNotificationPresenter`, `TestNotificationPresenter`) must `setContentIntent(PendingIntent.getActivity(..., MainActivity, FLAG_IMMUTABLE))` itself. Caught on-device in 2.20.1 — the background warning opened the app but the app-built foreground warning / resolved / sandbox didn't.
- **Test-notification sandbox** (diagnostic, shipped 2.18.0 / `android/251`): a flag-gated (`featureFunctionList`), purely-additive Function List feature that prototypes app-built notifications + a dedicated channel + `tag`-based inline replace, isolated from the production door path — a **permanent diagnostic tool; do not roll it back**. Send a test push with `scripts/send-test-notification.sh <testNotification-topic>` (gcloud + curl, `--help`). The "Resolved: door was open X min" feature — **Phase 1 (additive resolved-on-close) is ENABLED + LIVE (since 2026-06-22; confirmed still live 2026-06-28)**: `server/31` (#903) was deployed 2026-06-22, the Android client shipped in `2.20.2` (internal, flag-agnostic), and the maintainer set the live config flag `resolvedOnCloseEnabled` = `true` that day, so the resolved **fires in production**. Revert is instant: flip the flag `false` (no redeploy). (It sat briefly parked from 2026-06-16 until the 06-22 flag flip.) **Phase 1 inline-replaces only a FOREGROUND warning** — since R6+M4 (Android `2.20.0`) the warning and resolved share one "Garage door" channel + icon + `(tag="garage_door", id=7001)` slot, so a warning shown while the app is foregrounded is replaced in place by the resolved. A warning shown while **backgrounded** is OS-rendered with FCM's own tag, so the resolved still **coexists** with it (two cards) — but they now look like one feature (same channel/icon/heads-up). Full replacement of the background warning needs **Phase 2** (app-built warning, moves push-data onto `door_open_v2-` — still deferred). The resolved flag was parked from 2026-06-16 on the principle that the years-good existing warning shouldn't be disturbed for an additive nice-to-have (R6+M4 hardened that warning first); the maintainer enabled it 2026-06-22, so the two-card coexistence with a backgrounded warning is now the live production behavior. Full design, the as-shipped limitations, and Phase 2: [`docs/RESOLVED_NOTIFICATION_PLAN.md`](docs/RESOLVED_NOTIFICATION_PLAN.md). Reliability findings + the miss-vs-duplicate posture: [`docs/NOTIFICATION_RELIABILITY.md`](docs/NOTIFICATION_RELIABILITY.md).

### iOS simulator testing — the remote button is the only physical action; never tap it

The iOS app (`AndroidGarage/iosApp/`) shares the Android business logic via `:iosFramework`, so the **remote garage button is the only path that operates the real door.** It is well-guarded — verified 2026-06-24:
- **Auth-gated before any network call.** `PushRemoteButtonUseCase` returns `ActionError.NotAuthenticated` if `authState != Authenticated` *before* touching the repo/server. While the app is signed out, the button is inert.
- **Two deliberate taps.** The shared `ButtonStateMachine` is `Ready --tap--> Preparing --> AwaitingConfirmation --tap--> SendingToServer`. The first tap only arms; the command sends on the *second* tap. Auto-cancels back to `Ready` after the confirmation timeout.

**Rule for simulator / local testing: never tap the Home "Tap to open / close" button while signed in.** When verifying signed-in flows, stay on other tabs (Profile/History/Diagnostics) and treat the remote button as off-limits — leave exercising it to the user, who can see the physical door. Everything else is safe to drive (door status, history, nav, settings, sign-in itself, screenshots).

**FCM-receive testing is the *opposite direction* and is safe.** Pushing a fake door-state message (`simctl push` with `type/timestampSeconds/...`) flows `FcmPayloadParser → ReceiveFcmDoorEventUseCase → insertDoorEvent → local cache → STATUS label`. It only updates the displayed door *state*; it never sends a command to the ESP32. (Caveat: `simctl` silent `content-available` push is unreliable and may not reach `didReceiveRemoteNotification` at all — so a non-effect is inconclusive, not proof the path is broken. Real delivery is a device/Phase-G check.)

### iOS local verification + the iosFramework-spotless / non-required-iOS-CI traps

The local toolchain works (Xcode 26.5 + xcodegen + simulators), so iOS changes can be verified locally before pushing: `cd`-free `xcodegen generate --spec AndroidGarage/iosApp/project.yml --project AndroidGarage/iosApp` then `xcodebuild -project AndroidGarage/iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO`, then `simctl install`/`launch`/`io screenshot`. A pre-build Run Script rebuilds `shared.framework` via Gradle (slow first time; pass `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES` to skip it when only Swift changed). Reading SKIE's emitted `…/build/skie/.../*.swift` + the framework `Headers/shared.h` gives the exact Swift conformance contract (this is how the bridge `__`-prefixed-async + `SkieSwiftOptionalFlow` shapes were pinned).

Three traps for iOS PRs:
- **`iosFramework` Kotlin IS covered by the Android required check.** `Android Checks / Formatting & Static Analysis` runs `:iosFramework:spotlessApply --check`, so an "iOS-only" PR that edits `iosFramework/src/**/*.kt` can fail a **required** Android gate on formatting. Run `./gradlew -p AndroidGarage :iosFramework:spotlessApply` (or full `validate.sh`) before pushing iOS-Kotlin changes. #915 was blocked on exactly this (a multi-line fun that spotless wanted as a one-liner).
- **iOS CI (`Build iOS app + framework test`) is NOT a required check.** With auto-merge on, a PR merges as soon as the *required* (Android/Firebase/Script/Doc) checks pass — iOS CI often finishes *after* the merge. #912/#913/#915/#936/#937 all merged before iOS CI completed (all came back green because each was verified locally first). Mitigation: verify locally, and watch the iOS CI run (`gh run watch <id>`) to catch a post-merge red and fix-forward. Same root cause as the `NativeComponent` two-DI-component trap above.
- **SKIE does not bridge Kotlin default arguments.** A shared `fun f(x, y, z = default)` consumed from Swift fails the **iOS compile** with *"missing argument for parameter 'z'"* — Swift sees only the full-arity signature. Provide an explicit overload (`fun f(x, y) = f(x, y, default)`) instead of relying on the Kotlin default; the Swift call then resolves to the short overload. Canonical: `CheckInStatusMapper.forCheckIn`'s two-arg overload (#936, ADR-031 P5). This is caught **only** by the CI-exact iOS build (`generate-ios-screenshots.sh` / iOS CI), never `validate.sh` (Android-only, and Kotlin defaults compile fine for Android) — so build iOS locally after adding/changing any shared API consumed from Swift. (Other SKIE bridge shapes: sealed types → `onEnum(of:)` with camelCase cases, `data object` → no-payload case; `StateFlow<Long>.value` → `KotlinLong.int64Value`; keep date-library types off the public surface.)

### iOS snapshot gallery (Prefire + swift-snapshot-testing, regenerate-don't-assert)

iOS has a browsable visual reference of every SwiftUI `#Preview` — the analog of the Android screenshot gallery — covering the door component + **all 5 screens** (Home / History / Profile / Functions / Diagnostics). Full design: [ADR-030](AndroidGarage/docs/DECISIONS.md); how-to: [`AndroidGarage/iosApp/SnapshotTests/README.md`](AndroidGarage/iosApp/SnapshotTests/README.md). Regenerate with `./scripts/generate-ios-screenshots.sh`; browse `AndroidGarage/iosApp/SnapshotTests/SCREENSHOT_GALLERY.md`. Shipped across #920 (infra) → #921/#922/#924 (screens).

- **Posture: regenerate, don't assert** (mirrors the Android flow). **Committed** = the reference PNGs (`__Snapshots__/`) + `SCREENSHOT_GALLERY.md`. **NOT committed** = `PreviewTests.generated.swift` (gitignored; the `prefire` CLI regenerates it from the `#Preview`s, run by the test target's **pre-build Run Script phase**, so Xcode builds the snapshot tests directly — `optional: true` source + declared `outputFiles` make the absent-then-generated file compile). Snapshot tests are **NOT** a gating CI check.
- **When adding/refactoring an iOS view, add a `#Preview`** so it lands in the gallery. Screens take a live `NativeComponent`, so split each into a pure `*ContentView` (plain values + action closures) + fixture `#Preview`s — the `HomeContentView`/`HistoryContentView` pattern.
- **`#Preview` bodies are embedded verbatim** into the generated test (`@testable import iosApp`), so a body may only reference `internal`+ symbols — never a `private` file-scope helper (inline such fixtures). `traits:` overloads are iOS 17+ (deployment target is 16) — use plain `#Preview("Name") { }`.
- **Adding `private @State` to a previewed `*ContentView` can silently break the generated test.** The pure `*ContentView`s are built via Swift's *synthesized memberwise initializer* (the `#Preview`s + the screen shell call `HomeContentView(...)`). Swift lowers that synthesized init's access level to the most restrictive stored property — so adding a `private @State var x` (for local UI state like a presented sheet) makes the whole memberwise init `private`, and the cross-file generated snapshot test then fails to compile with *"initializer is inaccessible due to 'private' protection level."* Fix: write an explicit `internal init` covering only the injected `let`s and leave the `@State` initialized inline (it's excluded from the explicit init). Canonical: `HomeContentView`'s `activeInfoSheet` sheet state (#939, ADR-031 P5 info sheets). Same root family as the `#Preview`-verbatim rule above — both bite at the generated-test compile, which `validate.sh` never sees (iOS-only).
- **Time-dependent previews inject a fixed clock** (`PreviewFixtures.now`) so snapshots don't churn against the real `Date()`. The view takes `now: Date = Date()` (live in prod); previews pass the fixed value (canonical: `HistoryContentView`). Verify determinism by re-recording and confirming a byte-identical PNG.
- Prefire's **build-tool plugin is NOT used** — it requires disabling Xcode's package-plugin sandbox machine-wide. The prebuilt `prefire` CLI generates the file as a normal pre-build step instead. The `Doc front-matter` check excludes the generated iOS gallery + the gitignored `.derivedData-snapshots/`.

**CI-exact iOS build trap.** When verifying an app build locally, `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES` + `-destination 'generic/platform=iOS Simulator'` fails with `cannot find type … in scope` (a `shared` type) — the cached `shared.framework` only holds the arch from the last *specific*-destination build, but `generic` needs both arm64 + x86_64 slices, and the override skips the Gradle rebuild that produces them. **For a CI-exact local build use NO override** (Gradle rebuilds the framework, slower). The override is only safe with a specific booted-sim destination (`-destination "id=<sim>"`, single arch). Hit repeatedly across #921–#924. (Unlike Android Layoutlib, iOS local regen renders for real, so the PNGs are not blank.)

**Flaky parallel-request tests.** Ktor's `MockEngine` invokes its handler **concurrently** for requests issued in parallel (`coroutineScope { async{} ; async{} }`). A test that captures requests into a plain `mutableListOf` races on `add` → intermittent `size` mismatch. Guard the shared capture with a `Mutex` (`withLock`). Fixed `KtorNetworkFeatureAllowlistDataSourceTest` this way in #923 — the flake surfaced as a failing **required** Android Unit Tests check on an unrelated iOS-only PR (#922); rerunning the check is the unblock, fixing the determinism is the cure.

### Snooze (event-coupled, do not "fix" without an ADR)
The server's snooze model is bound to the door event timestamp that was current when the snooze was set (`SnoozeNotifications.ts:100`). Any subsequent door event silently voids it — including the ESP32-poll-triggered `Opening → OpeningTooLong` promotion at 60s (`EventInterpreter.ts:26, 194-197`). Practical consequence: **a stuck-`OPENING` or stuck-`CLOSING` door cannot meaningfully be snoozed** with the current model. Submit during a transition fails with HTTP 404 (`:152-158`); the client maps this specifically to `ActionError.SnoozeEventChanged` → `SnoozeAction.Failed.EventChanged` and surfaces a "Door state changed before snooze could apply" snackbar in `ProfileContent`. Other HTTP error codes still map to the generic `NetworkFailed`. Not a regression — the underlying server design has been this way since 2024-11-18 (`3e73f7384`). Full design + rationale + source pointers + verification table: [`docs/SNOOZE_BEHAVIOR.md`](docs/SNOOZE_BEHAVIOR.md). Don't unilaterally remove the timestamp check; if a redesign is wanted, write an ADR first.

### Code Patterns
- No bare top-level functions — group in a named `object {}` for discoverability (Composables exempt). Enforced by `checkNoBareTopLevelFunctions` (in `validate.sh`). See ADR-009
- No extension functions on generic types (e.g., `FcmPayloadParser.parse(data)` not `Map.asDoorEvent()`)
- No `*Impl` suffix on implementations — use descriptive prefixes (`Network*`, `Cached*`, `Firebase*`, `Default*`). Enforced by `checkNoImplSuffix` (in `validate.sh`). See ADR-008
- Generic naming over platform-specific terms for app-scoped classes: `AppStartup.run()` not `AppStartupActions.onActivityCreated()`. App-scoped code should be platform-agnostic (KMP target). Only call sites in Activities know about Android lifecycles
- **UseCases that need an id token self-wrap auth — never expose `idToken` in `invoke(...)`.** The convention: inject `EnsureFreshIdTokenUseCase` and `AuthRepository`, then read `authRepository.authState.value`, gate on `AuthState.Authenticated` (return a typed `NotAuthenticated` error otherwise), call `ensureFreshIdToken(authState)`, and pass `idToken.asString()` to the repo method. Canonical: `PushRemoteButtonUseCase`, `SnoozeNotificationsUseCase`, `FetchButtonHealthUseCase`. ViewModels then call `useCase()` with no token argument and handle the typed result. The error sealed type must include a `NotAuthenticated` variant so the gate has a typed return path; reusing `Forbidden` is wrong (Forbidden = server denied, NotAuthenticated = local check). When the underlying repo method takes no token (e.g. `fetchSnoozeStatus()`), no wrapping is needed
- **UseCase return types: prefer `AppResult<T, E>` for fetches and mutations** so callers can react to typed failures (`when` is exhaustive on the sealed `E`). Pass-through "fire and forget" UseCases that return `Unit` swallow the error path — fix them at the source. Sealed types like `AuthState` (`Authenticated`/`Unauthenticated`/`Unknown`) are themselves typed results and don't need wrapping in `AppResult`. Avoid lambda-based generic helpers (`AuthenticatedAction<E>`) — kotlin-inject + kotlin-inject DI prefers concrete typed deps
- **No `open class` for testability — use interface + `Default*` impl.** `open class X` exists in this codebase only when tests need to substitute behavior, which is the wrong direction: production code can accidentally extend it too, and the test fake is forced to call a real constructor with real deps. Convert to `interface X` + `class DefaultX : X` so test fakes implement the interface directly with no production constructor args (canonical: `RegisterFcmUseCase` after PR #669, `ReceiveFcmDoorEventUseCase`)
- **Flow vs StateFlow in UseCase return types — match the upstream, never wrap.** Default to `Flow<T>` so lifecycle and coroutine-scope ownership are explicit. Return `StateFlow<T>` only when the UseCase is a direct pass-through of an upstream `StateFlow` (no `.map`, no transformation) — and even then, the cache semantics are the repository's, not the UseCase's. **Never `stateIn(...)` a UseCase Flow inside a ViewModel that itself exposes a `StateFlow` derived from the same upstream** — the wrap-then-rewrap layer hides timing bugs (PR #283 race). Pass the reference through. Canonical pattern in this repo: `ObserveAuthStateUseCase`, `ObserveSnoozeStateUseCase`, `ObserveDoorEventsUseCase.current()`, and `ObserveDoorEventsUseCase.recent()` all return `StateFlow` because the repo's flow IS a StateFlow and the VM exposes it by reference (ADR-022); `ObserveDoorEventsUseCase.position()` and `ObserveFeatureAccessUseCase.functionList()` return `Flow` because they `.map { ... }` (a transformation, not a pass-through). When a UseCase needs to combine multiple flows and return a single observable value, see the next entry on `stateIn(applicationScope, Eagerly)`.
- **VMs that wrap a Singleton-repo StateFlow with a `LoadingResult<T>` mirror MUST seed the initial value from `upstream.value`.** A mirror is needed when the VM layers loading semantics over the underlying data (e.g. setting `Loading(prev)` during a user-initiated refresh, then back to `Complete`). The naive seed `MutableStateFlow(LoadingResult.Loading(null))` causes a one-frame `Loading` render on every fresh `NavBackStackEntry` — door icon flashes UNKNOWN→actual, history list flashes empty→full, etc. Correct seed: `MutableStateFlow(LoadingResult.Complete(observeX().value))`. Requires `observeX()` to expose `StateFlow` (per the ADR-022 pass-through rule above) so `.value` is synchronously readable. Canonical: `DefaultHomeViewModel._currentDoorEvent` (PR #738) and `DefaultDoorHistoryViewModel._recentDoorEvents` (PR #739). The collect lambda in `init {}` still runs to receive future updates.
- **UseCases that combine flows expose `StateFlow` via `stateIn(applicationScope, SharingStarted.Eagerly, initial)`.** A `combine(...)` result is naturally a cold `Flow` — first emission to a fresh subscriber is async and the consumer reads `Loading` for a frame on every fresh `NavBackStackEntry`. To deliver a synchronously-readable cached value, the UseCase wraps the combine with `stateIn` keyed on `applicationScope` (lives for app lifetime) with `Eagerly` (combine starts immediately and never stops). The provider must be `@Singleton` so the eager combine runs exactly once per process — multiple instances would each spin up their own collector with their own initial value, defeating the cache. `ComponentGraphTest` `assertSame` is the enforcement mechanism. Canonical: `ComputeButtonHealthDisplayUseCase` after PR #739. `WhileSubscribed(timeout)` is wrong here — it re-emits the initial value after the timeout window, reintroducing the flicker on the next subscription.
- **Cold-start fetches belong in app-scoped managers, not VM `init {}`.** Per-VM init fetches fire on every fresh `NavBackStackEntry`, causing redundant network round-trips on every tab tap. With FCM covering live updates while the app is open, the only fetches that need to happen are (a) cold-start once per process, (b) user-initiated (pull-to-refresh, alert action). The cold-start path lives in a `@Singleton` manager with idempotent `start()` (mirrors the `FcmRegistrationManager` pattern), invoked from `AppStartup.run()`. Singleton scope guarantees the fetch fires exactly once per process even when `MainActivity.onCreate` re-fires on rotation/Activity-restart. Canonical: `InitialDoorFetchManager` after PR #731 — `DefaultHomeViewModel` and `DefaultDoorHistoryViewModel` both default `fetchOnInit = false`.
- **Route wrappers consume the horizontal `safeDrawing` inset.** `MainActivity.onCreate` calls `enableEdgeToEdge()` (required for Android 15+, target SDK 35), letting content draw under system bars. Material 3 `Scaffold` consumes **vertical** insets via its `topBar`/`bottomBar` slots and exposes the remaining vertical inset as `innerPadding` — but does NOT touch horizontal insets. Without explicit horizontal handling, a side display cutout (camera notch on a phone in landscape) lets content draw under the cutout. The route wrappers (`RouteContent`, `DashboardRouteContent`, `ThreePaneRouteContent`) own horizontal padding per the SPACING_PLAN single-source rule, so they also own horizontal inset consumption: `Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))` on each wrapper's outer Box. Horizontal-only is deliberate — re-consuming vertical would double-pad the system bars. Landed in PR #736 (2.16.4).
- **Edge-to-edge bottom inset propagates to body-bottom content via `LocalContentEdgeInsets`.** `Main.kt`'s Scaffold body wrappers publish what edge insets the body content should observe per layout mode: Compact = `WindowInsets(0)` (bottomBar covers gesture nav, topBar covers status bar); Rail / None = `WindowInsets.safeDrawing.only(Bottom)` (no bottomBar so the gesture nav reaches the body). Two leaf-side APIs read the local: (a) **`safeListContentPadding()` in `Spacing.kt`** — for screen-level `LazyColumn` `contentPadding`. Combines the 8/24dp visual chrome clearance with the published bottom inset. Top is always 8dp (TopAppBar covers the status bar in every mode; never propagated to leaves). Used by Home, History, Settings, Function list. (b) **`Modifier.windowInsetsPadding(LocalContentEdgeInsets.current)`** — for non-LazyColumn body-bottom content (action rows, footers). Adds the propagated bottom inset as visible padding. Used by `DiagnosticsContent`'s action `Column` (Export CSV / Clear all). The contract is: **outer layers publish what insets the body content should observe; leaves read the local via the appropriate API for their shape.** **`asPaddingValues()` on raw `WindowInsets.safeDrawing` is forbidden** in app code (enforced by `checkNoRawSafeDrawingPaddingValues` lint, exempt only for `Spacing.kt`) — it's NOT consumption-aware and would double-pad whatever the body wrapper already padded for. The 2.16.12 → 2.16.13 → 2.16.15 sequence is the cautionary tale: 2.16.12 used raw `asPaddingValues` and double-padded by status-bar height in Compact (regression); 2.16.13 fixed the LazyColumn case via `LocalContentEdgeInsets`; 2.16.15 fixed the parallel Diagnostics action-Column case (action rows weren't migrated when the bridge was added). Verification: `SafeListContentPaddingCanaryPreview` in `HomeDashboardPreviews.kt` is a synthetic side-by-side fixture that visualizes `safeListContentPadding()` directly (red border + green interior at 24dp baseline vs 72dp with `PreviewWithSimulatedInsets(48.dp)` injected). The visible diff between halves in a single PNG is the proof the bridge propagates. Real-screen canaries don't work for this — Layoutlib renders LazyColumns at scroll position 0 so the bottom contentPadding doesn't push anything visible (first attempt at the canary failed for exactly this reason; documented inline). Adding new chrome (snackbar slot, side panel) only requires the body wrapper to publish a different `WindowInsets` value — leaves are unchanged. **Don't revert any leaf to `Spacing.ListContentPadding` in screen-level scrollables** — that's the legacy fixed value that won't track future inset changes; reserve it for scrollables NOT at the body's bottom edge (e.g. `DiagnosticsContent`'s LazyColumn which has an action row below it). Pre-2.15.0's first iteration used per-screen padding; rail + 3-pane work in 2.16.x exposed the gesture-nav coexistence problem; the bridge + lint + canary cleanup landed across `android/226` (2.16.12, original buggy bridge), `android/227` (2.16.13, LocalContentEdgeInsets fix), `android/228` (2.16.14, lint + canary), and `android/229` (2.16.15, Diagnostics action-row fix).
- **Avoid auth-state feedback loops in `combine(authRepository.authState, ...)` watchers.** `AuthRepository.refreshIdToken()` writes a new `Authenticated` instance to `_authState.value` (different `idToken` field, same is-authed boolean). Any code path that (a) is observed by `combine(authState, ...)` AND (b) triggers `EnsureFreshIdTokenUseCase` (directly or via a self-wrapping UseCase) creates a feedback loop: emission → fetch → token refresh → authState change → emission → ... When wiring a watcher, project to `it is AuthState.Authenticated` (a boolean — token refreshes don't change the value) BEFORE combining: `authRepository.authState.map { it is AuthState.Authenticated }.distinctUntilChanged()`. Hit this in PR #672 — `ButtonHealthFcmSubscriptionManager` was looping after 2.12.0's auth-wrap of `FetchButtonHealthUseCase`. Fix shipped in 2.12.1 (#672)
- **Stale-while-revalidate for repo-owned `LoadingResult<T>` state.** When a repository owns a `MutableStateFlow<LoadingResult<T>>` and supports refresh, do NOT overwrite a `Complete<T>` value with `Loading` (during fetch start) or `Error` (on failure). The previous good value is the best information available — keep it visible until a fresh `Complete` lands. Initial fetches with no prior `Complete` data still write `Loading`/`Error` as before. Mapping `Error → Loading` in display logic without a retry mechanism leaves the UI stuck on Checking on transient failures; SWR is the actual fix. Canonical: `NetworkButtonHealthRepository.doFetchButtonHealth` after PR #672 — gates both the fetch-start `Loading` write and the HTTP/connection-error `Error` write on `current !is LoadingResult.Complete`
- **String style: sentence case for headings, no em dashes (`—`) in user-visible strings.** Headings, section labels, info-sheet titles, and other UI text use sentence case (only the first word capitalized) — `"Door status"`, not `"Door Status"`. Em dashes are forbidden in user-visible strings (UI labels, body text, warning chips, info-sheet bodies, transit-warning tags). Replace with periods (most natural — splits two ideas), commas (parenthetical clarification), or colons (definition). Loading-state subtitle placeholders are descriptive (`"Loading…"`), never glyph-only (`"—"`). Em dashes are still fine in code comments, KDoc, and `@Preview` names — those are tooling-internal, not user-visible. The 2.16.9 sweep (`android/223`) cleaned up 8 user-visible strings + 4 test assertions; if you find more during a future change, fix them in the same PR. There's no automated lint for either rule yet (would need to inventory which `String` literals are user-visible vs internal); keep these in mind when reviewing new strings.
- **Spacing tokens.** When adding spacing values, reach for the named token in `androidApp/.../ui/theme/Spacing.kt` (`Spacing`, `CardPadding`, `DividerInset`, `ButtonSpacing`, `ParagraphSpacing`, `ContentWidth`) before a raw `.dp` literal. Names describe role, not value. The macro rule: **the parent owns the gap between its children; the child owns everything inside itself.** A composable should never apply `Modifier.padding(top = ...)` to itself — that claims ownership of the gap above it, which is the parent's job. Use `Arrangement.spacedBy(...)` on the parent instead. Full proposal + token catalog: [`AndroidGarage/docs/SPACING_PLAN.md`](AndroidGarage/docs/SPACING_PLAN.md). Single source of horizontal layout (padding + max-width + centering) lives at `Main.kt`'s `RouteContent { ... }` wrapper for single-pane and `DashboardRouteContent { ... }` for the wide-screen Home dashboard — child screens must NOT re-apply. **Container-owned vertical rhythm** (since 2.16.29): every screen-level `LazyColumn` uses `verticalArrangement = spacedBy(Spacing.BetweenItems = 16.dp)` and `safeListContentPadding().top = 16.dp`. Pre-2.16.29 each `Section`-using page (Home/Settings/History) added an extra 8 dp via `Spacing.SectionHeaderTop` (deleted), creating a visible inconsistency between Section pages (16 dp above first item) and bare-list pages like Diagnostics/Function list (8 dp). Now uniform 16 dp everywhere; same rhythm regardless of whether items are `HomeSection`/`SettingsSection`/`HistoryDaySection` wrappers or bare rows. Audit pattern from 2.16.30: grep `Modifier.padding(top|bottom|vertical = ...)` at the root of any Composable's modifier chain — these usually claim a gap that the parent should own.

- **Derive defaults from named constants instead of hardcoding magic numbers.** Canonical example: `domain/.../NavigationRailLayout.kt` (added 2.16.31). Pre-2.16.31 the Wide-mode NavigationRail's default top padding was the literal `8` in 8 places (`DataStoreAppSettings`, `FakeAppSettingsRepository`, `ProfileViewModel`, 5 preview call sites). The relationship between `8` and `Spacing.ListContentPadding.top` (16 dp) was implicit and fragile — a future bump of the body content padding would silently misalign the rail. Now the default is `BODY_CONTENT_TOP_DP - RAIL_INTRINSIC_PILL_OFFSET_DP` (16 − 8 = 8). The pattern: when a single integer/dp default exists in multiple places AND it has a derivable relationship to another constant (even if the relationship involves an empirical "M3 internals" adjustment), extract both into named `:domain` constants and define the default as their derived expression. Pin the relationship with a unit test (`SpacingTest.navRailBodyContentTopMatchesListContentPaddingTop` asserts `Spacing.ListContentPadding.top.value == NavigationRailLayout.BODY_CONTENT_TOP_DP`). The user can still override the persisted value; only the default returned when no override exists comes from the derivation. Apply this pattern when you encounter another magic-number default that exists in multiple places.
- **Adaptive layout decisions go through `AppLayoutMode`, never raw `LocalAppWindowSizeClass.current` or `LocalConfiguration.current.screenWidthDp`.** `AppLayoutMode` (in `androidApp/.../ui/AppLayoutMode.kt`) is a sealed type — `Compact`, `Wide`, `Expanded` — that owns four properties consumers query: `visibleTabs` (which tabs the nav chrome shows in this mode — drives both bottom nav and rail), `navPlacement` (where the chrome lives: `Bottom` / `Rail` / `None` — Compact = Bottom, Wide = Rail, Expanded = None), `mergedRoutes` (back-stack-entry redirects, e.g. `History → Home` on Wide and Expanded), and the activation thresholds (in `Companion.fromSize(widthSizeClass, widthDp)`). Compact uses `WindowWidthSizeClass.Compact` (<600dp), Wide uses `Medium`+ (≥600dp), Expanded uses a custom raw-dp threshold `EXPANDED_THRESHOLD_DP = 1200` so phones in landscape (~916dp) and foldables open in landscape (~1132dp) stay on Wide; only tablets in landscape (1280dp+) and ChromeOS get 3-pane. Composables call `currentAppLayoutMode()` or take `AppLayoutMode` as a parameter and pattern-match on it. Adding a screen, changing a threshold, or introducing a fourth mode is a single-file edit — the sealed-type `when` exhaustiveness forces every consumer to handle the new case. Enforced by two lints (in `validate.sh`): `checkAppLayoutModeBoundary` forbids `LocalAppWindowSizeClass.current` reads outside `AppLayoutMode.kt`, and `checkNoLocalConfigurationDimensionReads` forbids raw `LocalConfiguration.current.screenWidthDp/screenHeightDp` reads outside `AppWindowSizeClass.kt` (where `LocalAppWindowWidthDp` is computed and re-published). When you need a tighter dp threshold than M3 size classes can express, add a parallel `Local*Dp` from `ProvideAppWindowSizeClass` and read it from `AppLayoutMode.kt` — never sprinkle `LocalConfiguration` reads in screen code (size class can lag the raw configuration during foldable unfold / multi-window resize on some OEM Android variants, which is why the lint exists). The dispatch table is `RouteEntryFor` in `Main.kt` — a `when (canonicalScreen)` arm per `Screen.X` that picks the right wrapper (`RouteContent` vs `DashboardRouteContent`) and body. **Chrome placement is handled at the Scaffold level in `AppNavigation`**, not at the per-route arm: the Scaffold's `bottomBar` slot keys off `navPlacement == Bottom`; Wide wraps `NavDisplay` in a `Row` with `NavigationRailLeft` as a sibling. **Inset rule for the rail:** `NavigationRailLeft` claims the start safe-drawing inset via its `windowInsets = safeDrawing.only(Start)`; the content sibling declares `consumeWindowInsets(safeDrawing.only(Start))` so route wrappers' existing `safeDrawing.only(Horizontal)` reading transparently shrinks to "end side only" — no double padding on devices with a side display cutout. **Rail item placement is user-configurable** (`NavigationRailItemPosition` enum: `CenteredVertically` / `TopAligned`, default Top-aligned since 2.16.26). The toggle + extra top-padding stepper live behind Settings → Developer → Nav rail (consolidated into a single bottom sheet in 2.16.26, defaults derived in 2.16.31 — see `NavigationRailLayout` for the math). When **Top-aligned**, the selected indicator pill's top edge sits at the same y as the body's first content row top (the `STATUS` header on Home, `ACCOUNT` on Settings, etc.) — alignment landmark is the **indicator pill top edge**, not the icon glyph or label text. The default extra padding (8 dp) was empirical through 2.16.10/2.16.11 iteration and codified in 2.16.31 as `NavigationRailLayout.DEFAULT_TOP_PADDING_DP = BODY_CONTENT_TOP_DP - RAIL_INTRINSIC_PILL_OFFSET_DP` (16 − 8 = 8), pinned by a unit test. The 8 dp `RAIL_INTRINSIC_PILL_OFFSET_DP` captures `M3 NavigationRailVerticalPadding` (4 dp) + `NavigationRailItem`'s own pre-pill padding (4 dp), neither of which is exposed by `androidx.compose.material3` — drift risk noted in the constant's KDoc. Earlier iterations tried `header`-slot Spacer and content-side `padding(top = X)` approaches; both were fragile and got reverted. The current pattern is a single `weight(1f)` Spacer **below** the items inside `NavigationRail`'s `ColumnScope` content slot for Top-aligned (`weight(1f)` Spacers above AND below for Centered); the indicator-pill alignment is achieved via the user-controlled `navigationRailTopPaddingDp` setting padding the items downward by the right amount. **Centered mode** (Gmail / YouTube tablet style) remains available as the alternative — useful when alignment-with-content isn't the goal. Pre-2.15.0 history: four raw size-class reads scattered across `Main.kt`; consolidation landed in `android/210` (#704), Expanded mode in `android/214` (3-pane), the 1200dp threshold in `android/221` (#746) after observing landscape-phone 3-pane was too cramped, the rail in `android/224` (#752) after observing the bottom bar wasted vertical real-estate on landscape phones / tablets in portrait, the rail items got centered in `android/225` (#755), the user-configurable position toggle landed in `android/237` (2.16.23), the consolidated bottom sheet + Top-aligned default in `android/240` (2.16.26), and the derived default in `android/243` (2.16.31).
- **Data-only HIGH-priority FCMs wake the app process — every push has a battery cost.** When choosing FCM cadence, default to **transition-only** (server fires only on state changes), not heartbeat. A 1-min heartbeat = ~1440 wake-ups/day per device, comparable to a chat app receiving constant trickle messages. For freshness UX, prefer (a) tighter server-side detection cadence (cheap server-side) + (b) cold-start fetch on app open. The button-health 2026-05-10 review converged on this: server pubsub at 1 min, FCM only on transitions, `lastPollAtSeconds` carried through for the OFFLINE label but the client does NOT use it for a watchdog (because without heartbeats, `lastPollAtSeconds` only updates at FCM transitions — a watchdog comparing it to wall-clock would catch nothing the OFFLINE FCM doesn't already catch). When you find yourself proposing heartbeat FCMs, audit the per-device cost first.
- **Multi-pane: per-pane behavior is the default; cross-pane is opt-in with a clear UX win.** The wide-screen Home dashboard ships independent scroll AND independent pull-to-refresh per pane (matches what each screen does on phone). Pre-2.15.1 briefly wired pull-to-refresh to fire both fetches on either pane via an `onRefreshExtra` parameter; reverted in 2.15.1 as unnecessary surface area without a clear UX win. The shared `NavEntry` already gives both panes shared `ViewModelStore` + shared in-flight state (e.g. `SnoozeAction.Sending` is one VM instance read by both panes), so cross-pane sync happens naturally where it matters. When extending the dashboard with another shared affordance, the question to ask first: does a single user gesture conceptually scope to one pane or both? If one pane, scope it there. The "single screen" framing creates redundant fetches and is harder to revert later.
- **Compose modifier-order trap: `widthIn(max=)` MUST come before `fillMaxSize`.** `fillMaxSize` first sets `minWidth = maxWidth = parent.maxWidth`. The subsequent `widthIn(max = X)` then becomes a no-op because Compose's `SizeModifier` coerces `maxWidth >= minWidth` — it cannot shrink `maxWidth` below the existing `minWidth`. With `widthIn(max=)` first, `maxWidth` is capped to `X`, then `fillMaxSize` sets `[X, X]`. Rendered result: child renders at `X` and the parent Box's `contentAlignment` centers it. Hit this in `RouteContent.kt` — first generation of the tablet screenshot showed full-canvas content (cap was a no-op); reordering fixed it. The bug is invisible on phones (no `widthIn` activates below 640dp) so the only catch is a wide-canvas screenshot fixture. Documented inline in `RouteContent.kt`.
- **Composable params for production-visible UI must be required, not nullable-with-default.** A `null` default lets fixtures (previews, instrumented tests) silently omit a piece of UI that production always renders, and the framed README screenshot then diverges from what users see. Make the type system enforce parity. Canonical example: `HomeContent.deviceCheckIn` flipped from `DeviceCheckInDisplay? = null` to required in #625, which surfaced 7 silent test gaps the same day. Apply the same rule to any new Composable param whose absence would be a UI bug
- **In-flight UI affordance — pick the pattern by surface.** When showing in-flight feedback for a user action that suspends:
  - **Settings rows / icon-led rows**: `CircularProgressIndicator` ringing the leading icon. Box-wrap the icon, overlay a thin (2dp stroke) ring slightly larger than the 24dp icon. Icon stays at full alpha so the row reads "current state, in flux" — not "loading from scratch". Canonical: `SettingsRow.inFlight` opt-in param after PR #674 (snooze row).
  - **Buttons (`Button` / `OutlinedButton`)**: swap the leading icon for a small (20dp) `CircularProgressIndicator`, change the label to a `-ing…` form, and set `enabled = false` so a second tap can't queue another action during the wait. Canonical: Diagnostics "Clear all diagnostics" button after PR #675.
  - **Shared VM contract**: expose `StateFlow<Boolean>` for the action's in-flight state. The action method sets `true` before the suspend call and `false` in a `try/finally` so cancellation/throws never leave the flag stuck. The VM may already have richer state (e.g. `RemoteButtonViewModel.snoozeAction: SnoozeAction.Sending`) — derive the boolean from it; don't add a duplicate field
- **Tap-to-copy clipboard with API-gated Toast.** When a user-tappable surface copies a value to the clipboard, write via `LocalClipboardManager.current.setText(AnnotatedString(value))` and gate any "Copied" Toast to **`Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU`** (Android < 13). Android 13+ shows the OS's own clipboard preview chip after `setText`, so app-side Toast on top of that is duplicate confirmation noise. Canonical: `VersionBottomSheet` rows in `ProfileContent` (PR #718). Don't reach for an in-app Snackbar inside a `ModalBottomSheet` — the SnackbarHost lives in the screen Scaffold above the sheet, not inside it; Toast is the right primitive here even with the gate.
- **`ModalBottomSheet` content must own its scroll.** The Material 3 `ModalBottomSheet` provides the sheet container, drag handle, and dismiss behavior — but no internal scroll. When the sheet's natural height exceeds the available viewport (landscape phones, small windows, large font scale, future content additions), a non-scrolling content root will silently clip overflow at the bottom and rows there become unreachable. Default: apply `Modifier.verticalScroll(rememberScrollState())` to the outer `Column` of every `*SheetContent`. The three production sheets (`AccountBottomSheet`, `SnoozeBottomSheet`, `VersionBottomSheet`) all do this after PR #728. The bug was invisible in portrait on a normal phone with default font size — only fired when the sheet got tall (long sign-in name, expanded snooze options) or the viewport got short (landscape).
- **Visibility transitions for sections — use `AppAnimatedVisibility`** (in `androidApp/.../ui/theme/AppAnimatedVisibility.kt`). Project default for content that fills the parent's width and only changes its height (Settings sections, alert banners, conditionally-shown rows). Bakes in `expandVertically() + fadeIn()` / `shrinkVertically() + fadeOut()`. Compose's stock `AnimatedVisibility(visible = ...)` defaults to 2D `expandIn`/`shrinkOut` — that read as an unintentional horizontal zoom in the Developer section, fixed in 2.13.5. Reach for the upstream `AnimatedVisibility` only when 2D animation is intended (a card growing in place, a popover); document the deliberate choice when you do.
- **Compose preview wrapper — pick by component vs screen** (in `androidApp/.../theme/PreviewSurface.kt`):
  - `PreviewScreenSurface` (`fillMaxSize`) — for screen-level Composables that fill the device viewport in production (e.g. `HomeContent`, `FunctionListContent`, `Settings`). Dark-mode previews paint the dark page across the entire canvas, matching production.
  - `PreviewComponentSurface` (`wrapContentSize`) — for tiny components that don't fill the device (pills, icons, buttons). Theme background paints only behind the component; reference PNGs become 1–6 KB intrinsic-sized captures instead of ~20 KB phone-canvas captures with mostly-empty themed background.
  - **AGP screenshot tests have no "render at intrinsic" mode.** Captured PNG dimensions = canvas dimensions, always. Without `widthDp`/`heightDp` on `@Preview`, the canvas is the default phone viewport (~1080×2400). The Surface modifier inside the wrapper controls how much of that canvas gets themed; it cannot shrink the canvas itself. Split shipped in `android/195` (#645).

## Known Limitations

1. **Root CA Expiration**: Hard-coded certificate expires in 2036
2. **Polling Overhead**: ESP32 polls server; could be optimized with server-side waiting
3. **Button Race Condition**: Device crash after button press but before ack could cause double-press
4. **Reset Recovery**: FreeRTOS implementation needs hard reset testing (Arduino version has physical reset wire)