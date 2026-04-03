# Testing Improvements: Lessons from Battery Butler

Analysis of [battery-butler](https://github.com/cartland/battery-butler) — a Kotlin Multiplatform Android project with mature CI enforcement. This document identifies practices worth adopting in SmartGarageDoor, prioritized by impact and effort.

## What Battery Butler Does Well

Battery Butler enforces quality through **layered automated checks** — every PR runs 12+ fast Gradle tasks that catch bugs before code review even starts. The key insight: **custom Gradle plugins** that encode project-specific rules (architecture boundaries, naming, test coverage, theme consistency) are more valuable than generic linting because they catch the bugs unique to your codebase.

---

## High-Impact Practices to Adopt

### 1. Test Coverage Enforcement (Custom Gradle Task)

**What it does:** Scans source modules for classes matching patterns (e.g., `*ViewModel`, `*Repository`) and fails the build if a corresponding `*Test.kt` file is missing.

**Why it matters:** Prevents the most common testing gap — someone adds a new ViewModel and forgets to add tests. Code review catches this sometimes; CI catches it always.

**Battery Butler implementation:** `buildSrc/src/main/kotlin/testcoverage/TestCoverageCheckTask.kt`
- Configurable patterns per module (`*UseCase` → `*UseCaseTest`, `*ViewModel` → `*ViewModelTest`)
- Exemption mechanisms: inline `// @NoTestRequired: reason` or central `test-coverage-exemptions.txt`
- Generates a report showing covered/exempt/missing

**Effort for SmartGarageDoor:** Medium. Write a Gradle task that scans `door/`, `remotebutton/`, `auth/`, `applogger/` for `*ViewModel` and `*Repository` classes, verifies matching test files exist.

**Immediate catches:** `AuthViewModel`, `AppLoggerViewModel`, `AppSettingsViewModel` have no tests. `DoorRepositoryImpl`, `PushRepositoryImpl`, `AuthRepositoryImpl` have no tests.

---

### 2. Architecture Boundary Check (Import Boundary Plugin)

**What it does:** Fails the build if code imports from a forbidden layer. For example: a ViewModel importing directly from a network service, or a Repository importing from a ViewModel.

**Why it matters:** SmartGarageDoor has a clean server-centric architecture, but nothing enforces it. A future change could accidentally couple the door package to the auth package, or have a Composable directly call a repository. These violations are hard to spot in review.

**Battery Butler implementation:** `buildSrc/src/main/kotlin/importboundary/ImportBoundaryCheckTask.kt`
- Declarative rules: `presentation-feature cannot import from .usecase, .data`
- Per-line suppression: `// @ImportBoundaryExempt`

**Effort for SmartGarageDoor:** Medium. Define allowed imports per package:
```
ui/         → can import: door/, remotebutton/, auth/, config/
door/       → can import: internet/, db/, config/, fcm/
remotebutton/ → can import: internet/, config/, auth/
internet/   → cannot import: door/, remotebutton/, auth/, ui/
```

**Immediate catches:** Would enforce that UI composables go through ViewModels, not directly to repositories.

---

### 3. Detekt with Zero Tolerance

**What it does:** Static analysis with `maxIssues: 0` — any new issue fails the build.

**Why it matters:** SmartGarageDoor has no static analysis beyond ktlint formatting. Detekt catches real bugs: string equality with `===`, unchecked casts, swallowed exceptions, unreachable code.

**Battery Butler implementation:** `detekt.yml` with 50+ rules active, custom rules for hardcoded strings in Compose.

**Effort for SmartGarageDoor:** Small-Medium. Add detekt plugin, configure baseline for existing issues, enforce zero new issues on PRs.

**Immediate catches:** The codebase has swallowed exceptions in `DoorRepositoryImpl` and `PushRepositoryImpl` (catch + log + return null). Detekt's `TooGenericExceptionCaught` and `SwallowedException` rules would flag these.

---

### 4. Convention Tests (Reflection-Based)

**What it does:** Runtime tests that use reflection to verify architectural contracts. Example: "every class ending in `UseCase` must have an `operator fun invoke()`."

**Why it matters:** These tests encode team conventions that would otherwise be a comment in a README. They run automatically and catch violations immediately.

**Battery Butler examples:**
- `UseCaseConventionTest`: Every `*UseCase` has `invoke()`
- `ViewModelTestConventionTest`: Every `*ViewModel` has `*ViewModelTest`
- `SealedScreenStateConventionTest`: Every sealed interface has at least one subtype

**Effort for SmartGarageDoor:** Small. Write a single test that scans ViewModel classes via reflection and asserts test files exist. Complements the Gradle task from #1 but catches it at test time rather than build time.

---

### 5. Two-Tier CI (Fast + Slow)

**What it does:** PRs run only fast checks (lint, unit tests, formatting). Push to main runs the full suite (instrumented tests, release builds, screenshot tests).

**Why it matters:** SmartGarageDoor CI takes ~3 minutes for tests. As tests grow, keeping PRs fast matters. Slow checks (emulator tests, release AAB build) can run post-merge as a safety net rather than blocking every PR.

**Battery Butler implementation:** `.github/ci-mode.txt` toggles between `development` (fast) and `release` (full). PRs skip instrumented tests by default but always run them on main.

**Effort for SmartGarageDoor:** Small. Restructure `ci.yml` to have fast-required and slow-optional jobs. Fast jobs block merge; slow jobs run on main push.

---

## Medium-Impact Practices to Consider

### 6. Screenshot Tests for Compose UI

Battery Butler uses Paparazzi/Roborazzi for pixel-perfect UI regression testing. Every `@Preview` has a reference PNG; changes require explicit baseline updates.

**Relevance:** SmartGarageDoor has Compose UI with custom components (AnimatableGarageDoor, DoorStatusCard, SnoozeNotificationCard). Screenshot tests would catch visual regressions without manual QA.

**Effort:** Large. Requires adding Paparazzi dependency, writing test wrappers for previews, generating initial baselines. High ROI for apps with custom UI components.

### 7. Hardcoded String Detection

Battery Butler's custom detekt rules and Gradle task scan for `Text("hardcoded")` in non-preview code, enforcing string resources for localization.

**Relevance:** SmartGarageDoor is English-only, so localization isn't urgent. But the practice of detecting hardcoded strings also catches copy-paste errors and inconsistent wording.

**Effort:** Medium. Either a detekt custom rule or a Gradle task scanning for `Text("` patterns.

### 8. DataStore/Database Singleton Guard

Battery Butler's `DataStoreSingletonCheckTask` fails the build if DataStore or Room provider methods lack `@Singleton` scope. Creating multiple DataStore instances crashes at runtime.

**Relevance:** SmartGarageDoor uses Room with Hilt. If `AppDatabaseModule` ever loses its `@Singleton` annotation, the app would crash. A build-time check prevents this.

**Effort:** Small. A simple Gradle task that greps for `provideAppDatabase` and verifies `@Singleton` is present.

---

## Lower-Priority but Worth Noting

### 9. Theme Layer Enforcement
Prevents raw `Color(0xFF...)` literals outside a central theme file. Not critical for SmartGarageDoor's simpler UI but good practice.

### 10. Preview Time Determinism
Ensures `@Preview` composables don't use `Clock.System.now()` transitively. Prevents flaky screenshot tests. Only relevant if screenshot tests are adopted.

### 11. Naming Convention Checks
Battery Butler blocks `Ui` and `View` in class names (iOS UIKit collision). Not relevant for SmartGarageDoor (Android-only) but shows the pattern of encoding conventions as CI checks.

---

## Recommended Adoption Order

| # | Practice | Effort | Impact | Catches |
|---|----------|--------|--------|---------|
| 1 | Detekt with zero tolerance | Small | High | Swallowed exceptions, unsafe casts, unreachable code |
| 2 | Test coverage enforcement | Medium | High | Missing test files for new ViewModels/Repos |
| 3 | Two-tier CI | Small | Medium | Keeps PRs fast as test suite grows |
| 4 | Architecture boundary check | Medium | Medium | Layer violations, accidental coupling |
| 5 | Convention tests | Small | Medium | Missing ViewModel tests, contract violations |
| 6 | DB singleton guard | Small | Medium | Runtime crash from duplicate DataStore/Room |
| 7 | Screenshot tests | Large | High | Visual regressions without manual QA |
| 8 | Hardcoded string detection | Medium | Low | Non-localizable text, inconsistent copy |

## Key Takeaway

Battery Butler's quality comes not from any single tool but from **encoding project-specific conventions as automated checks**. Generic linters catch generic bugs; custom Gradle tasks catch *your* bugs. The highest-ROI investment for SmartGarageDoor is writing 2-3 small custom Gradle tasks that enforce the rules the team already follows informally.
