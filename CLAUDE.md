# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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
# Development cycle
npm install
npm run build          # Cross-platform TypeScript compilation
npm run tests          # Mocha test runner
npm run lint           # TSLint

# Local development
firebase serve --only functions
firebase emulators:start

# Deployment
firebase deploy --only functions
```

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
- **UseCase module** (`usecase/`): Shared business logic in `commonMain` — `RemoteButtonStateMachine`, fetch/push/snooze use cases
- **ViewModels**: `DoorViewModel`, `AuthViewModel`, `RemoteButtonViewModel` — delegate to UseCases
- **Repositories**: `DoorRepository`, `AuthRepository`, `PushRepository` — implement domain interfaces, depend on data interfaces
- **Typed errors**: `AppResult<D, E>` and `NetworkResult<T>` with sealed error types — exhaustive `when`, no `else`. See ADR-010, ADR-011
- **Platform bridges**: `AuthBridge`, `MessagingBridge` decouple Firebase SDK — enables unit testing and future iOS
- **DI**: kotlin-inject (`AppComponent`) — Hilt fully removed. See `docs/DI-MIGRATION.md`
- **Local storage**: Room database with offline-first caching
- **Network**: Ktor HTTP client + kotlinx.serialization, `NetworkResult<T>` at data source boundary

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
Run `./scripts/validate.sh` before pushing. It mirrors CI: spotless (all modules), lint, unit tests (3 variants), domain tests, debug build, screenshot test compilation, and Room schema drift check. Writes a validation marker so the git-guardrails hook can warn on stale pushes.

### Instrumented Tests
Run `./scripts/run-instrumented-tests.sh` when changing Room entities/DAOs, DI wiring (AppComponent), navigation, or Activity lifecycle code. Requires a connected device or emulator. Not part of `validate.sh` (too slow for every run). A git hook warns on push when these files are changed.

### CI Architecture

**Android:**
- **Pre-submit** (`ci.yml` → `ci-checks.yml`): Runs on PRs. Gate job `CI Complete` is the required status check.
- **Post-merge** (`ci-post-merge.yml` → `ci-checks.yml` + instrumented tests): Runs on push to main. Auto-creates GitHub issue on failure (`ci-failure/post-merge`), auto-closes on fix with flakiness detection.

**Firebase** (mirrors Android):
- **Pre-submit** (`firebase-ci.yml` → `firebase-ci-checks.yml`): Runs on PRs. Gate job `Firebase CI Complete` is the required status check.
- **Post-merge** (`firebase-ci-post-merge.yml` → `firebase-ci-checks.yml`): Runs on push to main. Auto-creates GitHub issue on failure (`ci-failure/firebase-post-merge`), auto-closes on fix.
- The `firebase-deploy.yml` `verify-ci` step matches the `Run Unit Tests` check-run via `endswith(...)`, so it works whether the check comes from the inline name or the reusable-workflow-prefixed name.

**Docs-only fast path (Android + Firebase):**
- When every changed file matches `**/*.md`, `docs/**`, `.claude/**`, `LICENSE`, `.gitignore`, or `AndroidGarage/distribution/whatsnew/**`, the `checks` reusable workflow is skipped and the gate jobs post success in ~20–30s.
- Mixed PRs (docs + code) take the full pipeline. Anything under `.github/**` or `scripts/**` is NOT docs — workflow and script edits still trigger full CI.
- Rule: skip CI only when the change cannot affect what CI verifies.

**Screenshot generation**: Use `./scripts/generate-android-screenshots.sh` (never run screenshot Gradle tasks directly — hooks block this).

### Room Database Safety
Room schema changes break at runtime (not compile time). The following safeguards are in place:

1. **Schema drift check** in `validate.sh` — detects if compilation changed the exported schema JSON without it being committed
2. **Guardrails hook** — warns when committing Room entity, DAO, or database files
3. **Schema contract tests** (`RoomSchemaTest`) — verify column structure and enum values match expectations
4. **Schema export** — `androidApp/schemas/` contains versioned JSON files tracked in git

**When modifying Room entities or DAOs:**
1. Make the change
2. Increment `version` in `@Database` annotation (`AppDatabase.kt`)
3. Run `./gradlew :androidApp:assembleDebug` to regenerate schema
4. Commit the new schema JSON file alongside your code change
5. `fallbackToDestructiveMigration` handles upgrades (users lose cached data, which is re-fetched from server)

### AppComponent / kotlin-inject Safety

kotlin-inject's `@Singleton` annotation is silent when misused. The
generated `InjectAppComponent.kt` is the authoritative source for what
the framework actually cached — "tests pass" + "annotation present" is
not enough to prove singletons are singletons. This bit the app hard
during the android/170 snooze regression (see ADR-022 timeline and
`docs/DI_SINGLETON_REQUIREMENTS.md` for the full postmortem).

**Rules for `AppComponent.kt`:**
1. Every `@Singleton` provider must be reachable via an abstract entry
   point (`abstract val x: T`). Concrete `val x: T @Provides get() = ...`
   gives kotlin-inject nothing to override — `@Singleton` is ignored.
2. Every `@Provides fun` body takes its deps as parameters. **Never**
   call a sibling `provideX()` inside a body — that call is regular
   Kotlin and bypasses the `_scoped` cache.
3. The in-file KDoc at the top of `AppComponent.kt` restates both rules.
   Respect it; don't "simplify" back to concrete providers.

**When modifying `AppComponent.kt`:**
1. Make the change.
2. Run `./gradlew -p AndroidGarage checkSingletonCaching` — this regenerates
   `InjectAppComponent.kt` via KSP and fails the build if any `@Singleton`
   provider is not wrapped in `_scoped.get(...)` in the generated code.
   This is the automated version of the manual inspection step and runs
   in `validate.sh`.
3. Read `androidApp/build/generated/ksp/debug/kotlin/com/chriscartland/garage/di/InjectAppComponent.kt`
   if the check failed — the error message lists which providers are missing
   caching. Healthy file is ~300 lines with one
   `override val X: T get() = _scoped.get(...)` per `@Singleton` entry.
4. Run the identity tests: `./gradlew :androidApp:connectedDebugAndroidTest
   -Pandroid.testInstrumentationRunnerArguments.class=com.chriscartland.garage.di.ComponentGraphTest`.
   All `*IsSingleton` tests must pass. This is the runtime counterpart to
   `checkSingletonCaching` (which is a static check on generated code).
5. If you add a new `@Singleton` state-owning provider, add a matching
   `abstract val` entry + an `assertSame` identity test in
   `ComponentGraphTest`.

### Releasing Android
Use `./scripts/release-android.sh` — never create or push tags directly (hooks block `git tag`).

```bash
./scripts/release-android.sh              # Interactive (terminal only)
./scripts/release-android.sh --check      # Print latest + next tag
./scripts/release-android.sh --confirm-tag android/N  # Non-interactive
./scripts/release-android.sh --confirm-tag android/N --confirm-hash android/M  # Rollback to old tag
./scripts/release-android.sh --dry-run    # Preview without releasing
```

The script computes the next tag as `android/<highest + 1>`. The `--confirm-tag` flag is a safety check — it must match the computed tag, it cannot override it. Deploys to Play Store internal track only — never production.

**Validation is required by default.** Run `./scripts/validate.sh` before releasing. If validation hasn't passed on the commit, the release script will block. `--skip-validation` exists for emergencies (e.g., rollbacks to old tags) but should NOT be used routinely. Always ask the user before using `--skip-validation`.

**Versioning rule (see [CHANGELOG.md](AndroidGarage/CHANGELOG.md#versioning)):** major = rewrite or core-experience shift; minor = added or removed user-facing feature/capability; patch = fixes, polish, refactors. `CHANGELOG.md` logs every version; `distribution/whatsnew/` gets one line per minor/major (patches roll up).

### Releasing Firebase Server
Use `./scripts/release-firebase.sh` — same pattern as Android releases.

```bash
./scripts/release-firebase.sh              # Interactive (terminal only)
./scripts/release-firebase.sh --check      # Print latest + next tag
./scripts/release-firebase.sh --confirm-tag server/N  # Non-interactive
./scripts/release-firebase.sh --dry-run    # Preview without releasing
```

The script computes the next tag as `server/<highest + 1>`. Deploys Cloud Functions only.

**Firebase server operations:** See [`docs/FIREBASE_DEPLOY_SETUP.md`](docs/FIREBASE_DEPLOY_SETUP.md) — long-term maintenance guide. Covers: release process, rollback, monitoring & logs, cost hygiene, Node/runtime deprecation calendar, deployer-SA re-provisioning + rotation, required GitHub secrets, required GCP APIs, and a troubleshooting table. CI deploy was fixed 2026-04-21 after a long period of silent-failure (`firebase deploy` exiting 0 despite `⚠ failed to update function` — the doc describes how to recognize it and what role was missing).

**Database refactor plan:** See [`docs/FIREBASE_DATABASE_REFACTOR.md`](docs/FIREBASE_DATABASE_REFACTOR.md) — phased plan to centralize 18 `new TimeSeriesDatabase(...)` calls into typed per-collection singletons with in-memory fakes. Includes goals, backward-compatibility principles, long-term maintenance rules, and safety guards (contract tests, scope rules). Zero production data impact when followed; rollback via `git revert` at any phase boundary.

### Secret Management (Android)
The app requires secrets in `local.properties` (decrypted from GPG at build time):
- `SERVER_CONFIG_KEY`, `GOOGLE_WEB_CLIENT_ID` — required for all builds
- `GARAGE_RELEASE_KEYSTORE_PWD`, `GARAGE_RELEASE_KEY_PWD` — release builds only
- Scripts: `release/decrypt-secrets.sh`, `release/clean-secrets.sh`

### PR Workflow
- Always create feature branches — never push to main
- Always `--squash --delete-branch` when merging PRs
- Never use `--admin` to bypass CI — enforce_admins is enabled
- Keep PRs small and focused (one concern per PR)
- Create multiple non-conflicting PRs in parallel — don't wait for CI on each one
- `cd` and `git -C` are blocked by hooks — run all commands from the repository root

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

**Branch naming:** The `git-guardrails.sh` hook matches the pattern `\b(main|master)\b` on `git push` command lines to block pushes to the default branch. This also triggers false-positives on branch names containing `main` as a substring (e.g., `refactor-main-kt`). Avoid those substrings in branch names, or rename before pushing.

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
- Reference: [battery-butler](https://github.com/cartland/battery-butler) for testing patterns

## Documentation

Detailed project documentation lives in `AndroidGarage/docs/`:

- [Architecture](AndroidGarage/docs/ARCHITECTURE.md) — System overview, Android layers, data flows, DI, state management
- [Decisions](AndroidGarage/docs/DECISIONS.md) — Architectural decision records (ADRs): server-centric design, tech stack, testing philosophy, migration targets
- [Migration](AndroidGarage/docs/MIGRATION.md) — Phased roadmap toward battery-butler tech stack (kotlin-inject, Ktor, clean architecture, KMP)
- [Testing](AndroidGarage/docs/TESTING.md) — CI stability plan, testing phases, priority order
- [Library Bugs](AndroidGarage/docs/library-bugs/) — Known third-party library bugs with mitigations

## Safety Rules

### FCM Push Notifications
Push notifications are the hardest feature to verify in production. If topic names or payload parsing change, notifications silently stop working with no visible error.
- Never change FCM topic name format or payload key names without explicit user approval
- Verify contract tests pass before modifying: `FcmTopicTest`, `FcmPayloadParsingTest`
- FCM-related code: `FCMService`, `DoorFcmRepository`, `FcmPayloadParser`, topic subscription flow

### Code Patterns
- No bare top-level functions — group in a named `object {}` for discoverability (Composables exempt). See ADR-009
- No extension functions on generic types (e.g., `FcmPayloadParser.parse(data)` not `Map.asDoorEvent()`)
- No `*Impl` suffix on implementations — use descriptive prefixes (`Network*`, `Cached*`, `Firebase*`, `Default*`). See ADR-008
- Generic naming over platform-specific terms for app-scoped classes: `AppStartup.run()` not `AppStartupActions.onActivityCreated()`. App-scoped code should be platform-agnostic (KMP target). Only call sites in Activities know about Android lifecycles

## Known Limitations

1. **Root CA Expiration**: Hard-coded certificate expires in 2036
2. **Polling Overhead**: ESP32 polls server; could be optimized with server-side waiting
3. **Button Race Condition**: Device crash after button press but before ack could cause double-press
4. **Reset Recovery**: FreeRTOS implementation needs hard reset testing (Arduino version has physical reset wire)