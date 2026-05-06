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

**Renaming a branch-protection-required gate job (ordering rule):**
- `Android CI Complete` (Android pre-submit) and `Firebase CI Complete` (Firebase pre-submit) are listed in branch protection's required status checks. Their names cannot be changed in a single PR without stalling every subsequent PR.
- Correct order: (1) PR adds a new gate job alongside the old one (both run); (2) wait for the new check-run to appear on a completed PR; (3) `gh api repos/:owner/:repo/branches/main/protection/required_status_checks/contexts --method PUT --input <(echo '["<new name>","<other required>"]')` — swaps the required contexts; (4) follow-up PR deletes the old gate job.
- **NEVER delete the old gate job before removing it from required contexts.** Reverse order leaves branch protection expecting a check-run no workflow produces, which blocks every PR. Recovery is re-adding the old context via the same `gh api` call.
- This was used to rename `CI Complete` → `Android CI Complete` in PRs #474 + #475.

**Screenshot generation**: Use `./scripts/generate-android-screenshots.sh` (never run screenshot Gradle tasks directly — hooks block this).

**Preview coverage is enforced.** `checkPreviewCoverage` (in `validate.sh`) scans `androidApp/src/main/` for `@Preview`-annotated `*Preview` Composables and fails the build if any aren't imported by a screenshot-test source under `android-screenshot-tests/src/screenshotTest/`. The current report is committed at `AndroidGarage/android-screenshot-tests/PREVIEW_COVERAGE.md`. If a preview shouldn't be screenshot-tested, mark it `private` — the import-based detection naturally excludes it. Adoption motivation: PR #623 → #625 added 4 new `TitleBarCheckInPill*` previews that no test imported, so the framed README screenshot diverged from production for two PRs in a row before the gap was noticed.

**Layoutlib gotcha — custom 960-viewport vectors inside LazyColumn item headers.** `painterResource(R.drawable.outline_signal_disconnected_24)` (Material's 960×960 viewport SVG-export) rendered via `Image` + `ColorFilter.tint` inside a LazyColumn item header silently dropped the entire item from the screenshot capture. Production rendered fine; only Layoutlib choked. Symptoms: the section just doesn't appear in the reference PNG, screenshot health-check still passes (file is non-empty), and "fixing" it by bumping `heightDp` does nothing. Workarounds, in preference order: (a) prefer Material `Icons.Outlined.*` / `Icons.Filled.*` (24-viewport vectors) for any drawable that lives inside a screenshot fixture; (b) use Material `Icon` Composable instead of `Image` + tint when you need to colorize. PR #623 hit this with the stale-pill icon; switching to `Icons.Outlined.SignalWifiOff` fixed it.

### Room Database Safety

When modifying Room entities or DAOs: (1) increment the `version` in `@Database` (`AppDatabase.kt`); (2) run `./gradlew :androidApp:assembleDebug` to regenerate the schema; (3) commit the new schema JSON alongside the code change. `fallbackToDestructiveMigration` handles upgrades. Full safeguard list (schema drift check in `validate.sh`, guardrails hook, `RoomSchemaTest` contract tests, exported schema files): see `AndroidGarage/docs/ARCHITECTURE.md` § Database.

### AppComponent / kotlin-inject Safety

`@Singleton` is silent when misused. Two mechanical rules:

1. Every `@Singleton` provider must be reachable via an `abstract val x: T` entry point. Concrete `val x: T @Provides get() = ...` gives kotlin-inject nothing to override — `@Singleton` is ignored.
2. Every `@Provides fun` body takes its deps as parameters. Never call a sibling `provideX()` inside a body — that call is regular Kotlin and bypasses the `_scoped` cache.

When modifying `AppComponent.kt`: run `./gradlew -p AndroidGarage checkSingletonCaching` (in `validate.sh`); inspect `androidApp/build/generated/ksp/debug/.../InjectAppComponent.kt` if it fails. New `@Singleton` state-owning providers need a matching `abstract val` + an `assertSame` identity test in `ComponentGraphTest`.

Full validation procedure, the android/170 postmortem that motivated these rules, and the failure-mode catalog: see `AndroidGarage/docs/DI_SINGLETON_REQUIREMENTS.md` and the kotlin-inject pattern guide at `AndroidGarage/docs/guides/kotlin-inject.md`.

### ViewModel fetch methods: set `Complete` explicitly on Success (ADR-023)

Every ViewModel method driving a `LoadingResult<T>` MUST set `LoadingResult.Complete(result.data)` in the `AppResult.Success` branch. `MutableStateFlow` dedups by equality; when a fetch returns the same value as cached, no observer fires and Loading latches forever (motivating bug: 2.4.4 Home-tab regression, PR #518).

Full rule, code example, and ADR-022 compatibility argument: see `AndroidGarage/docs/DECISIONS.md` ADR-023.

### Adding per-user feature flags

The repo gates UI features per-user via a server-maintained email allowlist edited in the Firebase console. The canonical example is the **Function List** screen (PR #573). The full pattern — file checklist, naming conventions (`featureXAllowedEmails` config key, `XAccess` route, `featureX: Boolean` domain field), `Boolean?` tri-state semantics, what NOT to do — lives in [`docs/FEATURE_FLAGS.md`](docs/FEATURE_FLAGS.md). When adding a new flag, read that doc first; it answers "do I create a new endpoint or extend the existing one?" (per-feature today, bulk deferred to ~feature #3) and "what files do I touch?" (8 on the server, 6 on Android, 1 wire-contract fixture).

### Wire-contract fixtures (`wire-contracts/`)

Shared JSON fixtures pin the over-the-wire shape of HTTP endpoints between the Firebase server and the Android client. Each endpoint gets a directory under `wire-contracts/<endpointName>/` with one `response_<scenario>.json` per documented response. **Both** the server's Mocha tests AND the Android Ktor data-source tests load the same files, so a unilateral rename or shape change fails the test on at least one side. Production decoding stays `ignoreUnknownKeys = true` for forward-compat; tests deserialize the same fixtures in strict mode (`ignoreUnknownKeys = false`) so renamed/missing keys throw `SerializationException` instead of silently coercing to defaults. Setup + the rationale (vs. OpenAPI / protobuf) live in [`wire-contracts/README.md`](wire-contracts/README.md). When adding a new HTTP endpoint, drop a fixture file alongside its tests on day one.

### One ViewModel per screen (ADR-026)

Each `*Content.kt` screen Composable imports at most one ViewModel. New screens get a dedicated VM that aggregates whatever UseCases that screen needs — `FunctionListViewModel` is the canonical example. Sub-component Composables (e.g. `RemoteButtonContent`) import zero VMs and take state via parameters from their parent screen.

Enforced by `./gradlew -p AndroidGarage checkScreenViewModelCardinality`, hooked into `validate.sh`. Legacy multi-VM screens (`HomeContent`, `ProfileContent`, `DoorHistoryContent`) are listed in `AndroidGarage/screen-viewmodel-exemptions.txt`; the list is intended to shrink, not grow. The check also fails when an exempt screen has been refactored to comply but still appears in the exemptions file — so stale entries do not accumulate.

When adding a new screen: write the ViewModel first (in `usecase/.../<X>ViewModel.kt`), wire it into `AppComponent.kt` as a non-`@Singleton` `@Provides`, then resolve it from the screen via `viewModel { component.<x>ViewModel }`. Full rationale in `AndroidGarage/docs/DECISIONS.md` ADR-026.

### Releasing Android
Use `./scripts/release-android.sh` — never create or push tags directly (hooks block `git tag`).

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

**Stat-cache stale after `validate.sh`** (recurring — observed in `android/193`, `android/194`, and `server/24` releases). When the release script reports `Working tree has uncommitted changes` but `git status --short` is empty, it's git's stat-cache holding stale `lstat` info from files validate.sh touched (Gradle build outputs, marker file write). The release script's quick check uses timestamps; when they look new but content is identical, it falsely flags dirty. Fix:
```bash
git update-index --refresh > /dev/null 2>&1   # forces git to re-stat everything
./scripts/release-android.sh --confirm-tag android/N    # then retry
```
Same pattern works for `release-firebase.sh`. The stat-cache refresh is harmless either way; safe to run before every release-script retry. (Both Android and Firebase release scripts share the same `git diff-index --quiet HEAD --` check pattern.)

**Rollback requires two steps** (intentionally hard to do accidentally):
```bash
git checkout android/M                 # 1. move HEAD to the commit you want to re-release
./scripts/release-android.sh --check   # 2. prints the rollback command with --confirm-hash and --confirm-rollback-from
```

**Versioning rule (see [CHANGELOG.md](AndroidGarage/CHANGELOG.md#versioning)):** major = rewrite or core-experience shift; minor = added or removed user-facing feature/capability; patch = fixes, polish, refactors. `CHANGELOG.md` logs every version; `distribution/whatsnew/` gets one line per minor/major (patches roll up).

### Releasing Firebase Server
Use `./scripts/release-firebase.sh` — same pattern as Android releases (same flags, same `--check` copy-paste workflow, same rollback recipe).

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

4. **PR diff shows parent commits temporarily** — after the parent squash-merges, GitHub auto-updates the stacked PR to show only its own changes.

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

### Testing Philosophy
- Tests must add value — no coverage for coverage's sake
- Prefer fakes over Mockito mocks (aligns with KMP migration target)
- CI is the deployment gate — if CI passes, the app is safe to ship
- Reference: [battery-butler](https://github.com/cartland/battery-butler) for testing patterns AND build/check infrastructure (it has a richer `buildSrc/` plugin set — `PreviewCoverageCheckTask` was ported from there in #629; `PreviewTimeCheckTask`, more sophisticated `ImportBoundaryCheckTask`, etc. are unported reference patterns)

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
- FCM-related code: `FCMService`, `DoorFcmRepository`, `FcmPayloadParser`, topic subscription flow

### Code Patterns
- No bare top-level functions — group in a named `object {}` for discoverability (Composables exempt). Enforced by `checkNoBareTopLevelFunctions` (in `validate.sh`). See ADR-009
- No extension functions on generic types (e.g., `FcmPayloadParser.parse(data)` not `Map.asDoorEvent()`)
- No `*Impl` suffix on implementations — use descriptive prefixes (`Network*`, `Cached*`, `Firebase*`, `Default*`). Enforced by `checkNoImplSuffix` (in `validate.sh`). See ADR-008
- Generic naming over platform-specific terms for app-scoped classes: `AppStartup.run()` not `AppStartupActions.onActivityCreated()`. App-scoped code should be platform-agnostic (KMP target). Only call sites in Activities know about Android lifecycles
- **Composable params for production-visible UI must be required, not nullable-with-default.** A `null` default lets fixtures (previews, instrumented tests) silently omit a piece of UI that production always renders, and the framed README screenshot then diverges from what users see. Make the type system enforce parity. Canonical example: `HomeContent.deviceCheckIn` flipped from `DeviceCheckInDisplay? = null` to required in #625, which surfaced 7 silent test gaps the same day. Apply the same rule to any new Composable param whose absence would be a UI bug
- **Compose preview wrapper — pick by component vs screen** (in `androidApp/.../theme/PreviewSurface.kt`):
  - `PreviewScreenSurface` (`fillMaxSize`) — for screen-level Composables that fill the device viewport in production (e.g. `HomeContent`, `FunctionListContent`, `Settings`). Dark-mode previews paint the dark page across the entire canvas, matching production.
  - `PreviewComponentSurface` (`wrapContentSize`) — for tiny components that don't fill the device (pills, icons, buttons). Theme background paints only behind the component; reference PNGs become 1–6 KB intrinsic-sized captures instead of ~20 KB phone-canvas captures with mostly-empty themed background.
  - **AGP screenshot tests have no "render at intrinsic" mode.** Captured PNG dimensions = canvas dimensions, always. Without `widthDp`/`heightDp` on `@Preview`, the canvas is the default phone viewport (~1080×2400). The Surface modifier inside the wrapper controls how much of that canvas gets themed; it cannot shrink the canvas itself. Split shipped in `android/195` (#645).

## Known Limitations

1. **Root CA Expiration**: Hard-coded certificate expires in 2036
2. **Polling Overhead**: ESP32 polls server; could be optimized with server-side waiting
3. **Button Race Condition**: Device crash after button press but before ack could cause double-press
4. **Reset Recovery**: FreeRTOS implementation needs hard reset testing (Arduino version has physical reset wire)