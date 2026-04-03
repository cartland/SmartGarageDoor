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

### Android MVVM + Repository
- ViewModels: `DoorViewModel`, `AuthViewModel`, `RemoteButtonViewModel`
- Repositories: `DoorRepository`, `AuthRepository`, `PushRepository`
- Local storage: Room database with offline-first caching
- Network: Retrofit + Moshi for API communication

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

## Development Workflow Notes

### Secret Management (Android)
The app requires these secrets in local.properties:
```
SERVER_CONFIG_KEY=YourKey
GOOGLE_WEB_CLIENT_ID=YourClientId
GARAGE_RELEASE_KEYSTORE_PWD=YourKeystorePassword (release builds only)
GARAGE_RELEASE_KEY_PWD=YourKeyPassword (release builds only)
```

Use provided scripts for release builds:
- `release/decrypt-secrets.sh` - Decrypt GPG-encrypted secrets
- `release/clean-secrets.sh` - Remove decrypted secrets after build

### Server Configuration Updates
Update server config via authenticated endpoint:
```bash
curl -H "Content-Type: application/json" \
     -H "X-ServerConfigKey: $SERVER_CONFIG_UPDATE_KEY" \
     https://us-central1-escape-echo.cloudfunctions.net/serverConfigUpdate \
     -d @serverConfig.json
```

### FreeRTOS Task Structure
The firmware runs multiple concurrent tasks:
- `read_sensors`: Monitor door position sensors with debouncing
- `upload_sensors`: Send sensor data to server when values change
- `download_button_commands`: Poll server for button press commands
- `push_button`: Execute button press via relay control
- `log_hello`: Periodic heartbeat logging

## Development Workflow

### Local Validation
Run `./scripts/validate.sh` before pushing. It mirrors CI: spotless, lint, unit tests (3 variants), debug build. Writes a validation marker so the git-guardrails hook can warn on stale pushes.

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

**Watch for PR starvation:** When many PRs queue up, the last one keeps getting pushed back as others merge ahead of it. Each merge makes it stale, requiring another CI run. If a PR has been open for a long time, prioritize merging it before creating new ones.

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

**Known issue:** If the backlog hook fires as a blocking stop hook while all PRs wait on CI, Claude cannot stop on its own. The user must run `rm .claude/.dev-mode` or press Ctrl+C.

### Claude Hooks (.claude/hooks/)
- **block-admin-bypass.sh** — Denies `--admin` on PR merges
- **git-guardrails.sh** — Blocks push to main, force push, destructive commands; enforces squash merge; warns on stale validation
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

## Known Limitations

1. **Root CA Expiration**: Hard-coded certificate expires in 2036
2. **Polling Overhead**: ESP32 polls server; could be optimized with server-side waiting
3. **Button Race Condition**: Device crash after button press but before ack could cause double-press
4. **Reset Recovery**: FreeRTOS implementation needs hard reset testing (Arduino version has physical reset wire)